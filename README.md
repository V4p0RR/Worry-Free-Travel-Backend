# 旅无忧 (Worry-Free Travel)

基于 **Spring Boot + Redis + Kafka + LangGraph** 的高性能在线旅游点评平台，集成 AI 智能推荐引擎。

---

## 系统架构

```
┌──────────────────────────────────────────────────────┐
│                    前端 (Web/Mobile)                  │
└──────────┬────────────────────────────┬──────────────┘
           │                            │
    POST :8090/api/agent/chat    GET :8081/shop/...
           │                            │
           ▼                            ▼
┌──────────────────┐          ┌──────────────────┐
│   Python Agent   │  HTTP    │   Java wft 主项目 │
│   (LangGraph)    │─────────>│   (Spring Boot)  │
│   Port 8090      │  数据API  │   Port 8081      │
│                  │<─────────│                  │
│ LLM 语义理解      │          │ 秒杀/缓存/点评     │
│ 意图识别+标签提取  │          │ 数据读取API       │
│ 推荐总结          │          │                  │
└────────┬─────────┘          └────────┬─────────┘
         │                             │
         │ MCP (可选)                   │
         ▼                             ▼
┌──────────────────┐          ┌──────────────────┐
│ Spring AI MCP    │          │  MySQL + Redis   │
│ Server :8020     │          │  + Kafka         │
│ (工具注册/发现)   │          │                  │
└──────────────────┘          └──────────────────┘
```

### 三组件独立启动

| 组件 | 端口 | 技术栈 | 职责 |
|---|---|---|---|
| Java wft | 8081 | Spring Boot 2.3 / Java 8 | 主业务 + 数据 API |
| Python Agent | 8090 | FastAPI + LangGraph | AI 意图识别 + 推荐 |
| MCP Server | 8020 | Spring AI / Java 21 | LLM 工具发现（可选） |

---

## 快速开始

### 环境要求

- JDK 8（wft 主项目）/ JDK 21（MCP Server 可选）
- Python 3.9+（Agent）
- MySQL 8.0 + Redis 6.0+

### 1. 数据库初始化

```bash
# 导入 Agent 模块的 8 张表（标签/策略/意图/数据源等）
mysql -u root -p worry_free_travel < src/main/resources/db/agent_migration.sql

# 导入种子数据（64 家店铺 + 14 篇笔记 + 关联标签）
mysql -u root -p worry_free_travel < src/main/resources/db/seed_data.sql

# 修复语义标签（酒吧/东南亚菜等补充 + 意图关键词修正）
mysql -u root -p worry_free_travel < src/main/resources/db/fix_tags.sql
mysql -u root -p worry_free_travel < src/main/resources/db/fix_intent.sql
```

### 2. 配置 LLM API Key

```bash
# Windows / PowerShell
$env:LLM_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:LLM_API_KEY="sk-your-key"
$env:LLM_MODEL="qwen-plus"
```

兼容 OpenAI / DeepSeek / 通义千问 / 豆包 等所有 OpenAI 接口格式的 LLM。

### 3. 启动 Java 主项目

```bash
mvn spring-boot:run
# → http://localhost:8081
```

### 4. 启动 Python Agent

```bash
cd Agent\dataSource-mcp-server\py
venv\Scripts\pip install -r requirements.txt       # 首次
uvicorn main:app --host 0.0.0.0 --port 8090
# → http://localhost:8090
```

验证：`curl http://localhost:8090/health` → `{"status":"ok"}`

### 5. 启动 MCP Server（可选）

```bash
cd Agent\dataSource-mcp-server
mvn spring-boot:run -pl spring-ai-mcp/data-mcp-service
# → http://localhost:8020
```

设置 `$env:MCP_ENABLED="true"` 后重启 Agent，数据流转将通过 MCP 网关。

### 6. 测试

```bash
curl -X POST http://localhost:8090/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"想吃火锅，有什么推荐"}'
```

---

## AI Agent 工作流

```
用户: "推荐便宜的火锅店"
        │
        ▼
[recognize_intent]   LLM 一次调用完成:
  ├─ 意图识别: food (美食推荐)
  ├─ 标签提取: [火锅(8), 性价比高(4)]
  └─ 表达优化: "寻找人均实惠的火锅餐厅"
        │
        ▼
[navigate_sources]   数据源导航 → 选择 [策略(2), 商家(4), 笔记(5)]
        │
        ▼
[aggregate_data]     并发拉取: 商家 + 笔记 + 策略规则
        │
        ▼
[build_candidates]   评分排序, 空结果时智能扩搜索
        │
        ▼
[smart_select]       策略规则过滤 → LLM 分析 → 选出最优 → 输出推荐语
        │
        ▼
     前端渲染
```

**设计原则**: LLM 语义理解为主驱动，关键词字面匹配做校验兜底。

---

## 项目结构

```
worry-free-travel/
├── src/main/java/com/wft/
│   ├── config/              # Kafka/Redis/Mvc/MyBatis 配置
│   ├── controller/          # API 接入层
│   │   └── AgentDataController.java  # Agent 数据读取 API
│   ├── service/impl/        # 秒杀(Kafka/Redis双实现)
│   ├── mapper/              # MyBatis-Plus 数据访问
│   ├── entity/              # 数据库实体（含 Agent 相关）
│   ├── dto/                 # Result/UserDTO
│   └── utils/               # Redis锁/ID生成器/线程池
├── Agent/
│   └── dataSource-mcp-server/
│       ├── py/              # Python LangGraph Agent
│       │   ├── main.py      # FastAPI 入口 (port 8090)
│       │   ├── graph/agent_graph.py  # 5节点工作流
│       │   ├── client/      # Java API / MCP 客户端
│       │   ├── models/      # Pydantic 请求/响应模型
│       │   └── config.py    # 环境变量配置
│       └── spring-ai-mcp/   # MCP Server (port 8020, 可选)
├── AdaptiveBufferedThreadPoolExecutor/  # 自研动态线程池
├── src/main/resources/db/
│   ├── agent_migration.sql  # Agent 建表 + 种子数据
│   ├── seed_data.sql        # 店铺/笔记种子数据
│   ├── fix_tags.sql         # 标签修正
│   └── fix_intent.sql       # 意图关键词修正
├── API文档.md               # 完整接口文档
└── pom.xml                  # Maven (Java 8)
```

---

## AI Agent 数据表

| 表 | 说明 | 行数 |
|---|---|---|
| `tb_tag` | 标签词库（菜系/场景/价格/风格） | 21 |
| `tb_shop_tag` | 商家-标签关联 | 130 |
| `tb_blog_tag` | 笔记-标签关联 | 34 |
| `tb_intent_case` | 意图案例库（关键词+触发条件） | 4 |
| `tb_strategy_rule` | 策略规则（过滤/权重/排序） | 6 |
| `tb_data_source` | 数据源配置（导航器） | 5 |
| `tb_conversation_snapshot` | 对话快照（运行时写入） | 0 |
| `tb_user_cs_profile` | 用户画像（运行时写入） | 0 |

---

## 前端对接

前端只需调一个接口：

```
POST :8090/api/agent/chat
Body: { "query": "想吃火锅", "userId": 1001, "conversationId": "上次返回的ID" }
```

返回结构含 `link` 字段可直接跳转：

```json
{
  "recommendedShops": [{ "id": 5, "name": "海底捞", "link": "/shop/5", ... }],
  "recommendedBlogs": [{ "id": 28, "title": "贰麻酒馆", "link": "/blog/28", ... }],
  "summary": "为您推荐海底捞火锅..."
}
```

完整接口文档见 [API文档.md](API文档.md)。

---

## 核心技术

### 高并发秒杀
- Redis Lua 原子扣减 + 一人一单校验
- Kafka 异步削峰 + 批量提交 + 慢任务重试
- 背压流控：消费端实时监控线程池负载，自动暂停/恢复拉取
- Redisson 分布式锁

### 缓存加固
- 缓存穿透 → 空对象缓存
- 缓存击穿 → 互斥锁重构
- 缓存雪崩 → 随机 TTL
- Cache Aside 一致性模式

### 自研动态线程池
`AdaptiveBufferedThreadPoolExecutor` 具备 CPU 负载感知、本地缓冲队列、退避空转机制，在高负载下自动降速保护系统。

### AI 智能推荐
- LangGraph 5 节点工作流，LLM 主驱语义理解
- 双判断意图识别（LLM 语义 + 关键词校验）
- MCP 数据源导航器 + 聚合器（配置化工具发现）
- 策略规则引擎（filter/boost/sort/exclude）
- 全量商店智能兜底（绝不编造）

---

## 配置

### 秒杀模式切换
```yaml
wft:
  seckill:
    mode: kafka    # 高吞吐背压模式（推荐）
    # mode: redis  # Redis Stream 模式
```

### LLM 配置（环境变量）
| 变量 | 默认值 | 说明 |
|---|---|---|
| `LLM_BASE_URL` | `https://api.openai.com` | API 地址 |
| `LLM_API_KEY` | — | API 密钥（必填） |
| `LLM_MODEL` | `gpt-3.5-turbo` | 模型名 |
| `JAVA_API_BASE` | `http://localhost:8081/api/agent` | Java 数据 API |
| `MCP_ENABLED` | `false` | 启用 MCP 网关 |
| `RATE_LIMIT_MAX` | `20` | 每分钟限流 |

---

## License

MIT
