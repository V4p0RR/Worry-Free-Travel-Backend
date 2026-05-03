package com.wft.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wft.dto.Result;
import com.wft.entity.*;
import com.wft.mapper.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 数据访问层 —— 供 Python LangGraph 智能体调用
 * 只负责从数据库读取数据并返回 JSON，不包含任何业务逻辑
 */
@RestController
@RequestMapping("/api/agent")
public class AgentDataController {

    @Resource
    private DataSourceMapper dataSourceMapper;
    @Resource
    private TagMapper tagMapper;
    @Resource
    private ShopTagMapper shopTagMapper;
    @Resource
    private BlogTagMapper blogTagMapper;
    @Resource
    private IntentCaseMapper intentCaseMapper;
    @Resource
    private StrategyRuleMapper strategyRuleMapper;
    @Resource
    private ShopMapper shopMapper;
    @Resource
    private BlogMapper blogMapper;

    /**
     * 数据源导航器 —— 列出所有可用数据源
     */
    @GetMapping("/datasources")
    public Result listDataSources() {
        List<DataSourceEntity> sources = dataSourceMapper.selectList(
                new LambdaQueryWrapper<DataSourceEntity>()
                        .eq(DataSourceEntity::getStatus, 1)
                        .orderByAsc(DataSourceEntity::getSortOrder));
        return Result.ok(sources);
    }

    /**
     * 获取所有意图案例（供关键词匹配）
     */
    @GetMapping("/intent-cases")
    public Result listIntentCases() {
        List<IntentCase> cases = intentCaseMapper.selectList(
                new LambdaQueryWrapper<IntentCase>()
                        .eq(IntentCase::getStatus, 1)
                        .orderByDesc(IntentCase::getPriority));
        return Result.ok(cases);
    }

    /**
     * 获取所有策略规则
     */
    @GetMapping("/strategy-rules")
    public Result listStrategyRules(@RequestParam(required = false) String scene) {
        LambdaQueryWrapper<StrategyRule> wrapper = new LambdaQueryWrapper<StrategyRule>()
                .eq(StrategyRule::getStatus, 1)
                .orderByDesc(StrategyRule::getPriority);
        if (StrUtil.isNotBlank(scene)) {
            wrapper.eq(StrategyRule::getScene, scene);
        }
        return Result.ok(strategyRuleMapper.selectList(wrapper));
    }

    /**
     * 标签获取接口 —— 根据意图/关键词获取标签
     */
    @GetMapping("/tags")
    public Result getTags(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getStatus, 1)
                .orderByDesc(Tag::getWeight);

        if (StrUtil.isNotBlank(category)) {
            wrapper.eq(Tag::getCategory, category);
        }
        List<Tag> tags = tagMapper.selectList(wrapper);

        // 如果有关键词，按关键词过滤标签
        if (StrUtil.isNotBlank(keyword)) {
            tags = tags.stream().filter(tag -> {
                // 检查关键词是否包含标签名（如"推荐日料餐厅"包含"日料"）
                if (tag.getName() != null && keyword.contains(tag.getName()))
                    return true;
                // 检查关键词是否包含同义词（如"想吃日式"包含"日式"）
                if (tag.getAliases() != null) {
                    try {
                        JSONArray aliases = JSONUtil.parseArray(tag.getAliases());
                        for (Object alias : aliases) {
                            if (alias != null && keyword.contains(alias.toString()))
                                return true;
                        }
                    } catch (Exception ignored) {
                    }
                }
                return false;
            }).collect(Collectors.toList());
        }

        return Result.ok(tags);
    }

    /**
     * 根据标签获取商家 —— 按标签命中数排序，命中越多排越前
     */
    @GetMapping("/shops/by-tags")
    public Result getShopsByTags(@RequestParam String tagIds) {
        List<Integer> ids = parseTagIds(tagIds);
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<ShopTag> shopTags = shopTagMapper.selectList(
                new LambdaQueryWrapper<ShopTag>().in(ShopTag::getTagId, ids));

        // 按shopId分组，统计每个店铺命中了几个标签
        Map<Long, Long> hitCount = shopTags.stream()
                .collect(Collectors.groupingBy(ShopTag::getShopId, Collectors.counting()));

        // 按命中数降序排列
        List<Long> shopIds = hitCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (shopIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 按排序后的ID列表查询
        List<Shop> shops = shopMapper.selectBatchIds(shopIds);
        // 保持命中数排序
        Map<Long, Shop> shopMap = shops.stream()
                .collect(Collectors.toMap(Shop::getId, s -> s, (a, b) -> a));
        List<Shop> sorted = shopIds.stream()
                .map(shopMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Result.ok(sorted);
    }

    /**
     * 根据标签获取笔记 —— 按标签命中数排序
     */
    @GetMapping("/blogs/by-tags")
    public Result getBlogsByTags(@RequestParam String tagIds) {
        List<Integer> ids = parseTagIds(tagIds);
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<BlogTag> blogTags = blogTagMapper.selectList(
                new LambdaQueryWrapper<BlogTag>().in(BlogTag::getTagId, ids));

        Map<Long, Long> hitCount = blogTags.stream()
                .collect(Collectors.groupingBy(BlogTag::getBlogId, Collectors.counting()));

        List<Long> blogIds = hitCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (blogIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Blog> blogs = blogMapper.selectBatchIds(blogIds);
        Map<Long, Blog> blogMap = blogs.stream()
                .collect(Collectors.toMap(Blog::getId, b -> b, (a, b) -> a));
        List<Blog> sorted = blogIds.stream()
                .map(blogMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Result.ok(sorted);
    }

    private List<Integer> parseTagIds(String tagIds) {
        if (StrUtil.isBlank(tagIds))
            return Collections.emptyList();
        List<Integer> ids = new ArrayList<>();
        for (String s : tagIds.split(",")) {
            try {
                ids.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }
}
