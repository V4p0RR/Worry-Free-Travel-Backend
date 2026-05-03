"""
Worry-Free-Travel AI Agent Service
基于 LangGraph 的智能推荐与意图理解引擎

启动方式: uvicorn main:app --host 0.0.0.0 --port 8090
"""
import logging
import uuid
from contextlib import asynccontextmanager
from collections import defaultdict
import time

from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse

from models.schemas import ChatRequest, ChatResponse, AgentState
from graph.agent_graph import get_agent_graph
from config import SERVER_HOST, SERVER_PORT, RATE_LIMIT_MAX, RATE_LIMIT_WINDOW

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# In-memory rate limiter
_rate_limit_buckets: dict[str, list] = defaultdict(list)


def _check_rate_limit(key: str) -> bool:
    """Simple sliding window rate limiter."""
    now = time.time()
    bucket = _rate_limit_buckets[key]
    bucket[:] = [t for t in bucket if now - t < RATE_LIMIT_WINDOW]
    if len(bucket) >= RATE_LIMIT_MAX:
        return False
    bucket.append(now)
    return True


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"Agent service starting on {SERVER_HOST}:{SERVER_PORT}")
    get_agent_graph()  # pre-warm
    yield
    logger.info("Agent service shutting down")


app = FastAPI(
    title="Worry-Free-Travel AI Agent",
    description="基于 LangGraph 的智能推荐与意图理解引擎",
    version="1.0.0",
    lifespan=lifespan,
)


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/api/agent/chat", response_model=ChatResponse)
async def chat(request: ChatRequest, req: Request):
    """
    智能客服聊天接口 —— 用户以自然语言输入需求，系统识别意图并返回推荐结果。

    前端对接说明：
    - 首次请求不传 conversationId，系统自动生成并返回
    - 后续请求传入 conversationId 维持会话上下文
    - 被限流时返回 429 状态码，前端应延迟重试
    """
    query = request.query.strip() if request.query else ""
    if not query:
        raise HTTPException(status_code=400, detail="查询内容不能为空")

    # Rate limiting
    limit_key = f"user:{request.user_id}" if request.user_id else f"ip:{req.client.host}"
    if not _check_rate_limit(limit_key):
        raise HTTPException(status_code=429, detail="请求过于频繁，请稍后再试")

    conversation_id = request.conversation_id or uuid.uuid4().hex[:12]

    init_state = AgentState(
        user_id=request.user_id,
        query=query,
        conversation_id=conversation_id,
    )

    try:
        graph = get_agent_graph()
        result = await graph.ainvoke(init_state)
    except Exception as e:
        logger.error("Agent execution failed: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail="处理请求失败，请稍后重试")

    # 组装响应，给每个商家/笔记注入跳转链接
    shops = []
    for s in result.get("shops", []) or []:
        shops.append({**s, "link": f"/shop/{s.get('id', '')}"} if isinstance(s, dict) else s)
    blogs = []
    for b in result.get("blogs", []) or []:
        blogs.append({**b, "link": f"/blog/{b.get('id', '')}"} if isinstance(b, dict) else b)
    tags = []
    for t in result.get("matched_tags", []) or []:
        tags.append({**t} if isinstance(t, dict) else t)

    return ChatResponse(
        conversation_id=conversation_id,
        intention=result.get("intention", "其他"),
        optimized_query=result.get("optimized_query", query),
        matched_tags=tags,
        recommended_shops=shops,
        recommended_blogs=blogs,
        summary=result.get("summary", ""),
    )


@app.get("/api/agent/rate-limit/{key}")
async def get_rate_limit(key: str):
    """查询限流状态."""
    now = time.time()
    bucket = _rate_limit_buckets[key]
    bucket[:] = [t for t in bucket if now - t < RATE_LIMIT_WINDOW]
    remaining = max(0, RATE_LIMIT_MAX - len(bucket))
    return {"key": key, "remaining_tokens": remaining, "max": RATE_LIMIT_MAX}


# ── CLI entry ─────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=SERVER_HOST, port=SERVER_PORT, reload=True)
