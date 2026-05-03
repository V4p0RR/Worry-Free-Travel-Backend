"""
LangGraph agent workflow for worry-free-travel.

Flow matching 要求.md 18-step process:

  START
    ↓
  [1] recognize_intent   (步骤2-7: LLM主驱意图识别+标签提取，关键词校验兜底)
    ↓
  [2] navigate_sources   (步骤8-9: 数据源导航器)
    ↓
  [3] aggregate_data     (步骤10: 数据聚合器)
    ↓
  [4] build_candidates   (步骤11: 候选方案集合+排序)
    ↓
  [5] smart_select       (步骤12-16: 策略规则+LLM综合分析)
    ↓
  [6] explain_result     (步骤17-18: 解释性推荐输出)
    ↓
  END

Design principle: LLM主驱，关键词校验兜底。
"""
import json
import logging
import re
from typing import Literal

from langgraph.graph import StateGraph, END
from langchain_openai import ChatOpenAI

from models.schemas import AgentState
from config import LLM_BASE_URL, LLM_API_KEY, LLM_MODEL
from client.java_client import (
    fetch_intent_cases, fetch_strategy_rules, fetch_tags,
    fetch_shops_by_tags, fetch_blogs_by_tags,
)
from client.mcp_client import (
    MCP_ENABLED, fetch_by_source_ids, parse_mcp_response,
    parse_mcp_response_by_source,
)

logger = logging.getLogger(__name__)
_llm: ChatOpenAI | None = None


def _get_llm(temperature: float = 0.3) -> ChatOpenAI:
    global _llm
    if _llm is None:
        _llm = ChatOpenAI(
            base_url=LLM_BASE_URL, api_key=LLM_API_KEY,
            model=LLM_MODEL, temperature=temperature,
        )
    _llm.temperature = temperature
    return _llm


# ═══════════════════════════════════════════════════════════════════
# Node 1: LLM主驱意图识别+标签提取 (步骤2-7)
# ═══════════════════════════════════════════════════════════════════

async def recognize_intent(state: AgentState) -> dict:
    """
    步骤2-7: 以LLM为主，一次调用完成意图识别+标签提取+表达优化。
    关键词仅做校验和兜底。
    """
    query = state.query

    # 拉数据
    if MCP_ENABLED:
        raw = await fetch_by_source_ids([1])
        intent_cases = parse_mcp_response(raw)
        if not intent_cases:
            intent_cases = await fetch_intent_cases()
    else:
        intent_cases = await fetch_intent_cases()

    all_tags = await fetch_tags() or []

    if not intent_cases:
        return _fallback_response(query, all_tags)

    # ── 主路径：LLM一次性输出意图+标签+优化表达 ──
    tag_desc = "\n".join(
        f"  {t.get('id')}: {t.get('name')}({t.get('category')}) [{t.get('aliases','')}]"
        for t in all_tags
    ) if all_tags else "无"

    intent_desc = "\n".join(
        f"  {c.get('intentionKey','')}: {c.get('intention','')} - {c.get('conditions','')}"
        for c in intent_cases
    )

    llm = _get_llm(0.3)
    try:
        resp = await llm.ainvoke([
            {"role": "system", "content": (
                "你是意图识别与标签提取专家。根据用户输入，完成以下任务：\n\n"
                "### 意图列表\n" + intent_desc + "\n\n"
                "### 可用标签\n" + tag_desc + "\n\n"
                "### 任务\n"
                "1. 判断用户意图（intention_key: food/scene/guide/other）\n"
                "2. 从标签中选出语义匹配的标签id（理解'撸串'→烧烤、'喝酒'→酒吧）\n"
                "3. 优化用户表达使其更清晰完整\n\n"
                "### 回复格式（严格JSON）\n"
                '{"intention_key":"food","intention":"美食推荐",'
                '"tag_ids":[8,4],"optimized_query":"优化后的需求描述"}\n'
                "只回复JSON，不要其他内容。"
            )},
            {"role": "user", "content": query},
        ])
        result = _parse_json_response(resp.content or "")
    except Exception as e:
        logger.warning("LLM intent recognition failed: %s", e)
        result = {}

    intention_key = result.get("intention_key", "other")
    intention = result.get("intention", "其他")
    llm_tag_ids = result.get("tag_ids", [])
    optimized = result.get("optimized_query", query)

    # ── 关键词校验：LLM漏了的标签，如果query中字面包含就补上 ──
    if isinstance(llm_tag_ids, list):
        keyword_supplement = []
        for t in all_tags:
            tid = t.get("id")
            if tid in llm_tag_ids:
                continue
            name = t.get("name", "")
            if name and name in query:
                keyword_supplement.append(tid)
            else:
                aliases_str = t.get("aliases", "[]")
                try: aliases = json.loads(aliases_str) if isinstance(aliases_str, str) else []
                except: aliases = []
                for a in (aliases if isinstance(aliases, list) else []):
                    if a and a in query:
                        keyword_supplement.append(tid)
                        break
        if keyword_supplement:
            llm_tag_ids = list(llm_tag_ids) + keyword_supplement
            logger.info("关键词补充标签: %s", keyword_supplement)

    # ── 兜底：LLM完全失败 → 纯关键词 ──
    if not intention_key or intention_key == "other" or not llm_tag_ids:
        logger.info("LLM输出无效，启用关键词兜底")
        return _fallback_response(query, all_tags, intent_cases)

    # 根据tag_ids取完整tag对象
    matched = [t for t in all_tags if t.get("id") in llm_tag_ids]

    logger.info("步骤7 完成: intention=%s tags=%s optimized=%s",
                intention, [t.get("name") for t in matched], optimized[:40])

    return {
        "intention": intention,
        "intention_key": intention_key,
        "optimized_query": optimized or query,
        "matched_tags": matched,
        "matched_tag_ids": [t.get("id") for t in matched if t.get("id")],
        "keyword_matched": bool(keyword_supplement) if 'keyword_supplement' in dir() else False,
    }


def _fallback_response(query: str, all_tags: list, intent_cases: list = None) -> dict:
    """纯关键词兜底：不依赖LLM，只看字面匹配。"""
    # 意图兜底
    intention_key = "other"
    intention = "其他"
    if intent_cases:
        for c in sorted(intent_cases, key=lambda x: x.get("priority", 0), reverse=True):
            kws = c.get("keywords", "")
            if isinstance(kws, str):
                try: kws = json.loads(kws)
                except: kws = []
            if isinstance(kws, list):
                for kw in kws:
                    if kw and kw in query:
                        intention_key = c.get("intentionKey", "other")
                        intention = c.get("intention", "其他")
                        break
            if intention_key != "other":
                break

    # 标签兜底
    matched = []
    for t in all_tags:
        name = t.get("name", "")
        if name and name in query:
            matched.append(t)
            continue
        aliases_str = t.get("aliases", "[]")
        try: aliases = json.loads(aliases_str) if isinstance(aliases_str, str) else []
        except: aliases = []
        for a in (aliases if isinstance(aliases, list) else []):
            if a and a in query:
                matched.append(t)
                break

    logger.info("兜底结果: intention=%s tags=%s", intention,
                [t.get("name") for t in matched])
    return {
        "intention": intention,
        "intention_key": intention_key,
        "optimized_query": query,
        "matched_tags": matched,
        "matched_tag_ids": [t.get("id") for t in matched if t.get("id")],
        "keyword_matched": True,
    }


# ═══════════════════════════════════════════════════════════════════
# Node 2: 数据源导航器 (步骤8-9)
# ═══════════════════════════════════════════════════════════════════

async def navigate_sources(state: AgentState) -> dict:
    """
    步骤8-9: 数据源导航器
    根据意图+标签决定需要哪些数据源。
    """
    tag_ids = state.matched_tag_ids
    intent_key = state.intention_key
    scene = _intent_to_scene(intent_key)
    tag_id_str = ",".join(str(t) for t in tag_ids) if tag_ids else ""

    if not tag_id_str:
        logger.info("步骤9 导航: 无标签，仅拉策略规则")
        return {"source_ids": [2], "source_params": {2: {"scene": scene}}}

    source_ids = [2, 4, 5]
    source_params = {
        2: {"scene": scene},
        4: {"tagIds": tag_id_str},
        5: {"tagIds": tag_id_str},
    }
    logger.info("步骤9 导航: sources=%s", source_ids)
    return {"source_ids": source_ids, "source_params": source_params}


# ═══════════════════════════════════════════════════════════════════
# Node 3: 数据聚合器 (步骤10)
# ═══════════════════════════════════════════════════════════════════

async def aggregate_data(state: AgentState) -> dict:
    """步骤10: 多数据源聚合。"""
    source_ids = state.source_ids or []
    params = state.source_params or {}
    tag_ids = state.matched_tag_ids

    if not source_ids and not tag_ids:
        return {"shops": [], "blogs": [], "rules": []}

    if not source_ids and tag_ids:
        import asyncio
        shops, blogs = await asyncio.gather(
            fetch_shops_by_tags(tag_ids), fetch_blogs_by_tags(tag_ids))
        rules = await fetch_strategy_rules(_intent_to_scene(state.intention_key))
        return {"shops": shops, "blogs": blogs, "rules": rules}

    if MCP_ENABLED:
        try:
            raw = await fetch_by_source_ids(source_ids, params)
            parsed = parse_mcp_response_by_source(raw)
            shops = parsed.get(4, [])
            blogs = parsed.get(5, [])
            rules = parsed.get(2, [])
        except Exception as e:
            logger.warning("MCP aggregate failed: %s, falling back", e)
            shops, blogs, rules = await _aggregate_direct(source_ids, params, tag_ids)
    else:
        shops, blogs, rules = await _aggregate_direct(source_ids, params, tag_ids)

    logger.info("步骤10 聚合: shops=%d blogs=%d rules=%d", len(shops), len(blogs), len(rules))
    return {"shops": shops, "blogs": blogs, "rules": rules}


async def _aggregate_direct(source_ids, params, tag_ids):
    import asyncio
    shops, blogs = [], []
    if tag_ids:
        shops, blogs = await asyncio.gather(
            fetch_shops_by_tags(tag_ids), fetch_blogs_by_tags(tag_ids))
    rules = []
    if 2 in source_ids:
        scene = params.get(2, {}).get("scene", "")
        rules = await fetch_strategy_rules(scene) if scene else []
    return shops, blogs, rules


# ═══════════════════════════════════════════════════════════════════
# Node 4: 生成候选方案集合 (步骤11)
# ═══════════════════════════════════════════════════════════════════

async def build_candidates(state: AgentState) -> dict:
    """步骤11: 候选方案集合，基础排序。"""
    shops = state.shops or []
    blogs = state.blogs or []

    shops = sorted(shops, key=lambda s: s.get("score", 0) or 0, reverse=True)

    if not shops and not blogs:
        logger.info("步骤11: 候选为空，按意图扩搜索")
        shops, blogs = await _expand_search(state.optimized_query, state.intention_key)

    elif len(shops) < 2:
        logger.info("步骤11: 候选不足(%d)，补充", len(shops))
        extra, extra_blogs = await _expand_search(
            state.optimized_query, state.intention_key,
            exclude_ids=[s.get("id") for s in shops])
        shops.extend(extra)
        if extra_blogs:
            blogs = (blogs or []) + extra_blogs

    logger.info("步骤11 候选: shops=%d blogs=%d", len(shops), len(blogs))
    return {"shops": shops, "blogs": blogs}


async def _expand_search(query: str, intent_key: str, exclude_ids: list = None) -> tuple:
    """智能扩搜索。"""
    all_tags = await fetch_tags() or []
    if intent_key == "food":
        priority_cats = ["food", "price", "scene"]
    elif intent_key == "scene":
        priority_cats = ["scene", "style"]
    else:
        priority_cats = []

    sorted_tags = sorted(all_tags,
                         key=lambda t: 0 if t.get("category") in priority_cats else 1)
    top_ids = [t.get("id") for t in sorted_tags[:15] if t.get("id")]
    if not top_ids:
        return [], []

    shops = await fetch_shops_by_tags(top_ids) or []
    blogs = await fetch_blogs_by_tags(top_ids) or []

    if shops:
        shop_desc = "\n".join(
            f"  {s.get('id')}: {s.get('name')} | {s.get('area','')} | "
            f"人均{s.get('avgPrice','?')}元 | 评分{_score_str(s.get('score'))}"
            for s in shops[:30]
        )
        llm = _get_llm(0.3)
        try:
            resp = await llm.ainvoke([
                {"role": "system", "content": (
                    f"选出与用户需求最匹配的5-8家。回复JSON数组[id,...]。\n" + shop_desc
                )},
                {"role": "user", "content": f"需求：{query}\n意图：{intent_key}"},
            ])
            ids = json.loads(resp.content.strip()) if resp.content else []
            if isinstance(ids, list) and ids:
                if exclude_ids:
                    ids = [i for i in ids if i not in exclude_ids]
                picked = [s for s in shops if s.get("id") in ids]
                picked.sort(key=lambda s: ids.index(s.get("id")))
                shops = picked
        except Exception:
            shops = sorted(shops, key=lambda s: s.get("score", 0) or 0, reverse=True)[:5]

    return shops, blogs[:3]


# ═══════════════════════════════════════════════════════════════════
# Node 5: LLM智能选择 (步骤12-16)
# ═══════════════════════════════════════════════════════════════════

async def smart_select(state: AgentState) -> dict:
    """
    步骤12-16: 策略规则执行 + LLM综合分析 + 最优方案判定。
    """
    rules = state.rules or []
    shops = state.shops or []
    blogs = state.blogs or []
    tags = state.matched_tags or []

    if not shops and not blogs:
        hint = {"food": "试试输入菜系名（川菜、火锅、日料）",
                "scene": "试试输入地点或类型（KTV、酒吧）",
                "guide": "试试搜索菜系或商圈看探店笔记"}
        return {"summary": f"暂无精确匹配。💡 {hint.get(state.intention_key, hint['food'])}",
                "shops": shops, "blogs": blogs}

    # 应用策略规则（全部规则统一执行）
    shops = _apply_strategy_rules(shops, rules)

    is_guide = state.intention_key == "guide"

    # 构建LLM上下文
    ctx = [
        f"用户需求：{state.optimized_query}",
        f"意图：{state.intention}({state.intention_key})",
    ]
    if tags:
        ctx.append(f"标签：{'、'.join(t.get('name','') for t in tags)}")

    if is_guide and blogs:
        ctx.append(f"相关笔记（应优先推荐）：")
        for i, b in enumerate(blogs[:8]):
            ctx.append(f"  [{i+1}] 《{b.get('title','')}》| 点赞:{b.get('liked',0)}")
    if shops:
        ctx.append(f"候选商家（{len(shops)}家，数据库真实数据）：")
        for i, s in enumerate(shops[:8]):
            ctx.append(
                f"  [{i+1}] {s.get('name')} | {s.get('area','')} | "
                f"人均{s.get('avgPrice','?')}元 | 评分{_score_str(s.get('score'))}"
            )

    sys_prompt = (
        "你是旅行推荐专家。\n【硬约束】只能推荐候选列表中的真实商家，禁止编造。\n"
        "理解用户需求后，选出最匹配的，说明排序理由。\n"
        '回复JSON: {"recommended":[店名,...],"reasoning":"理由","tips":"建议"}'
    ) if not is_guide else (
        "你是旅行攻略推荐专家。优先推荐笔记（博客），其次商家。\n【硬约束】禁止编造。\n"
        '回复JSON: {"recommended_notes":[标题,...],"recommended_shops":[店名,...],'
        '"reasoning":"理由","tips":"建议"}'
    )

    llm = _get_llm(0.5)
    resp = await llm.ainvoke([
        {"role": "system", "content": sys_prompt},
        {"role": "user", "content": "\n".join(ctx)},
    ])
    sel = _parse_json_response(resp.content or "")

    # 按LLM推荐排序
    if is_guide:
        names = sel.get("recommended_notes", [])
        if names:
            nmap = {b.get("title", ""): b for b in blogs}
            reordered = [nmap[n] for n in names if n in nmap]
            blogs = reordered + [b for b in blogs if b.get("title") not in names]
    else:
        names = sel.get("recommended", [])
        if names:
            nmap = {s.get("name", ""): s for s in shops}
            reordered = [nmap[n] for n in names if n in nmap]
            shops = reordered + [s for s in shops if s.get("name") not in names]

    parts = []
    if sel.get("reasoning"): parts.append(sel["reasoning"])
    if sel.get("tips"): parts.append(f"💡 {sel['tips']}")
    if not parts: parts.append(_default_summary(shops, blogs))

    return {"summary": "\n\n".join(parts), "shops": shops, "blogs": blogs}


# ═══════════════════════════════════════════════════════════════════
# Node 6: 解释性输出 (步骤17-18)
# ═══════════════════════════════════════════════════════════════════

async def explain_result(state: AgentState) -> dict:
    logger.info("步骤17-18: 最终结果生成完成")
    return state.model_dump()


# ═══════════════════════════════════════════════════════════════════
# Routing
# ═══════════════════════════════════════════════════════════════════

def route_after_intent(state: AgentState) -> Literal["navigate_sources", "__end__"]:
    if state.intention_key == "other" and not state.matched_tag_ids:
        return "__end__"
    return "navigate_sources"


# ═══════════════════════════════════════════════════════════════════
# Helpers
# ═══════════════════════════════════════════════════════════════════

def _parse_json_response(text: str) -> dict:
    if not text: return {}
    try: return json.loads(text.strip())
    except: pass
    m = re.search(r'```(?:json)?\s*([\s\S]*?)```', text)
    if m:
        try: return json.loads(m.group(1).strip())
        except: pass
    m = re.search(r'\{[\s\S]*\}', text)
    if m:
        try: return json.loads(m.group(0))
        except: pass
    return {}


def _intent_to_scene(key: str) -> str:
    return {"food": "food_recommend", "scene": "scene_query", "guide": "guide"}.get(key, "other")


def _score_str(score) -> str:
    if score is None: return "?"
    try:
        s = int(score)
        return f"{s/10:.1f}" if s > 10 else str(s)
    except: return str(score)


def _apply_strategy_rules(shops: list, rules: list) -> list:
    for rule in rules:
        try:
            action = rule.get("action", "")
            conditions = rule.get("conditions", "{}")
            if isinstance(conditions, str): conditions = json.loads(conditions)
            if action == "filter":
                area = conditions.get("area")
                min_r = conditions.get("rating_gte")
                shops = [s for s in shops
                         if (area is None or s.get("area") == area)
                         and (min_r is None or _shop_rating(s) >= min_r)]
            elif action == "priority_boost":
                area = conditions.get("area")
                boosted = [s for s in shops if area and s.get("area") == area]
                normal = [s for s in shops if not area or s.get("area") != area]
                shops = boosted + normal
            elif action == "sort_by":
                params = rule.get("actionParams", "{}")
                if isinstance(params, str): params = json.loads(params)
                field = params.get("field", "score")
                reverse = params.get("order") == "desc"
                shops = sorted(shops, key=lambda s: s.get(field, 0) or 0, reverse=reverse)
        except Exception as e:
            logger.warning("Rule %s failed: %s", rule.get("name"), e)
    shops = sorted(shops, key=lambda s: s.get("score", 0) or 0, reverse=True)
    return shops


def _shop_rating(shop: dict) -> float:
    score = shop.get("score")
    if score is None: return 0
    try:
        s = int(score)
        return s / 10.0 if s > 10 else float(s)
    except: return 0


def _default_summary(shops: list, blogs: list) -> str:
    parts = []
    if shops: parts.append(f"{len(shops)}家相关商家")
    if blogs: parts.append(f"{len(blogs)}篇相关笔记")
    return f"为您找到{'和'.join(parts)}，请查看下方推荐结果。" if parts else "暂无相关推荐。"


# ═══════════════════════════════════════════════════════════════════
# Build graph (6 nodes)
# ═══════════════════════════════════════════════════════════════════

def build_agent_graph() -> StateGraph:
    w = StateGraph(AgentState)

    w.add_node("recognize_intent", recognize_intent)   # 步骤2-7 (LLM主驱)
    w.add_node("navigate_sources", navigate_sources)   # 步骤8-9
    w.add_node("aggregate_data", aggregate_data)       # 步骤10
    w.add_node("build_candidates", build_candidates)   # 步骤11
    w.add_node("smart_select", smart_select)           # 步骤12-16
    w.add_node("explain_result", explain_result)       # 步骤17-18

    w.set_entry_point("recognize_intent")

    w.add_conditional_edges(
        "recognize_intent", route_after_intent,
        {"navigate_sources": "navigate_sources", "__end__": END},
    )
    w.add_edge("navigate_sources", "aggregate_data")
    w.add_edge("aggregate_data", "build_candidates")
    w.add_edge("build_candidates", "smart_select")
    w.add_edge("smart_select", "explain_result")
    w.add_edge("explain_result", END)

    return w


_agent_graph = None


def get_agent_graph():
    global _agent_graph
    if _agent_graph is None:
        _agent_graph = build_agent_graph().compile()
    return _agent_graph
