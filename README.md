# 旅无忧 (Worry-Free Travel)

基于 **Spring Boot + Redis + Kafka + LangGraph + MCP** 的高性能在线旅游点评平台，集成 AI 智能推荐引擎。

---

## 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                       前端 (Web/Mobile)                       │
└──────────┬──────────────────────────────────┬────────────────┘
           │ POST :8090/api/agent/chat        │ :8081 REST API
           ▼                                  ▼
┌──────────────────────┐          ┌──────────────────────────┐
│   Python Agent       │  MCP协议  │  Spring AI MCP Server    │
│   (LangGraph)        │─────────>│  (Port 8020)              │
│   Port 8090          │  工具发现 │  ┌────────────────────┐  │
│                      │  数据聚合 │  │ 数据源导航器        │  │
│  LLM 意图识别        │          │  │ 数据聚合器          │  │
│  标签提取            │          │  │ 5个可配置数据源     │  │
│  推荐总结            │          │  └────────┬───────────┘  │
└──────────────────────┘          └───────────┼──────────────┘
                                              │ HTTP
                                              ▼
┌──────────────────────────────────────────────────────────────┐
│                  Java wft 主项目 (Port 8081)                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ 秒杀引擎 │ │ 缓存层   │ │ 社交模块 │ │ Agent数据API   │  │
│  │ Kafka版  │ │ 穿透/击穿│ │ 关注/Feed│ │ 标签/商家/笔记 │  │
│  │ Redis版  │ │ 互斥锁   │ │ 点赞排行 │ │ 意图/策略/数据 │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────┬───────┘  │
│                                                  │          │
├──────────────────────────────────────────────────┼──────────┤
│               MySQL 8.0  +  Redis 6.0  +  Kafka 3.4          │
└──────────────────────────────────────────────────────────────┘
```

**数据流**: 用户输入 → Python Agent(LangGraph) → MCP Server(工具路由) → Java 数据 API → MySQL/Redis

---

## 快速开始

### 环境要求

| 组件 | 版本要求 |
|---|---|
| JDK | 8 (wft) + 21 (MCP Server) |
| Python | 3.9+ |
| MySQL | 8.0 |
| Redis | 6.0+ |
| Kafka | 3.4 (仅秒杀 Kafka 模式需要) |

### 1. 数据库

```bash
mysql -u root -p worry_free_travel < src/main/resources/db/db.sql
```

### 2. LLM 配置

```bash
# 通义千问（推荐）
$env:LLM_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:LLM_API_KEY="sk-your-key"
$env:LLM_MODEL="qwen-plus"

# 或 DeepSeek
# $env:LLM_BASE_URL="https://api.deepseek.com"
# $env:LLM_MODEL="deepseek-chat"
```

兼容所有 OpenAI 接口格式的 LLM 服务。

### 3. 启动

**顺序启动，缺一不可：**

```bash
# 终端 1：Java wft 主项目（:8081）
mvn spring-boot:run

# 终端 2：MCP Server（:8020，JDK 21）
cd Agent\dataSource-mcp-server
mvn spring-boot:run -pl spring-ai-mcp/data-mcp-service

# 终端 3：Python Agent（:8090）
cd Agent\dataSource-mcp-server\py
venv\Scripts\pip install -r requirements.txt   # 首次
$env:MCP_ENABLED="true"
uvicorn main:app --host 0.0.0.0 --port 8090
```

### 4. 验证

```bash
# 健康检查
curl http://localhost:8090/health          # → {"status":"ok"}
curl http://localhost:8020/api/mcp/datasources  # → 5个数据源列表

# 智能推荐
curl -X POST http://localhost:8090/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"想吃火锅，有什么推荐"}'
```

---

## AI Agent 智能推荐引擎

### LangGraph 工作流（5 节点对应 18 步流程）

```
用户输入: "推荐便宜的火锅店"
        │
        ▼
┌─────────────────────────────────────────────────────┐
│ [1] recognize_intent  (步骤2-7)                      │
│     LLM 一次调用完成: 意图识别 + 标签提取 + 表达优化    │
│     输入: "推荐便宜的火锅店"                            │
│     输出: {intention:"美食推荐", tags:[火锅,性价比高], │
│            optimized:"寻找人均实惠的火锅餐厅"}          │
│     关键词校验: 字面匹配补漏                            │
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────┐
│ [2] navigate_sources  (步骤8-9)                      │
│     数据源导航器: 根据意图+标签决定调用哪些数据源        │
│     → 数据源2(策略规则) + 4(商家) + 5(笔记)            │
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────┐
│ [3] aggregate_data  (步骤10)                         │
│     通过 MCP Server 并发拉取:                         │
│     · 商家列表 (按标签命中数排序)                      │
│     · 笔记列表                                        │
│     · 策略规则                                        │
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────┐
│ [4] build_candidates  (步骤11)                       │
│     评分降序排列 → 空结果智能扩搜索 → 候选集合          │
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────┐
│ [5] smart_select  (步骤12-18)                        │
│     策略规则执行(filter/boost/sort)                    │
│     → LLM 综合分析 → 选出最优 → 截断不相关 → 输出推荐语 │
└──────────────────────┬──────────────────────────────┘
                       ▼
                  前端渲染 (含 /shop/{id} /blog/{id} 跳转链接)
```

### 设计原则

- **LLM 主驱**: 意图识别和标签提取由 LLM 语义理解主导
- **关键词校验**: 字面匹配仅做补漏，防止 LLM 遗漏明显标签
- **MCP 数据网关**: 所有数据通过 MCP Server 统一获取，5 个数据源可配置扩展
- **智能兜底**: 无结果时不编造，从全量真实数据中 LLM 筛选最优匹配

### MCP 数据源（配置在 `data-sources.json`）

| ID | 数据源 | Java API | 用途 |
|---|---|---|---|
| 1 | 意图案例库 | `/api/agent/intent-cases` | 关键词匹配 + LLM 意图参照 |
| 2 | 策略规则库 | `/api/agent/strategy-rules?scene=` | filter/boost/sort/exclude |
| 3 | 标签词库 | `/api/agent/tags?keyword=&category=` | 菜系/场景/价格/风格标签 |
| 4 | 商家推荐 | `/api/agent/shops/by-tags?tagIds=` | 按标签命中数排序返回 |
| 5 | 笔记推荐 | `/api/agent/blogs/by-tags?tagIds=` | 探店笔记关联推荐 |

### Agent 数据表（6 张）

| 表 | 行数 | 说明 |
|---|---|---|
| `tb_tag` | 21 | 标签词库（scene/food/price/mood/style） |
| `tb_shop_tag` | 130 | 64 商家 × 2~3 标签 |
| `tb_blog_tag` | 34 | 14 笔记 × 2~3 标签 |
| `tb_intent_case` | 4 | 意图案例（美食/景点/攻略/其他） |
| `tb_strategy_rule` | 6 | 策略规则（filter/boost/sort） |
| `tb_data_source` | 5 | MCP 数据源配置 |


### 前端对接

只需调一个接口：

```
POST :8090/api/agent/chat
Body: { "query": "想吃火锅", "userId": 1001, "conversationId": "上次返回的ID" }
```

返回结构自带跳转链接：

```json
{
  "conversationId": "a1b2c3d4",
  "intention": "美食推荐",
  "optimizedQuery": "寻找人均实惠的火锅餐厅",
  "matchedTags": [{"id":8,"name":"火锅","category":"food"}],
  "recommendedShops": [
    {"id":5,"name":"海底捞火锅","link":"/shop/5","avgPrice":104,"score":49}
  ],
  "recommendedBlogs": [
    {"id":23,"title":"人均80吃正宗韩式烤肉","link":"/blog/23","liked":42}
  ],
  "summary": "💡 海底捞建议提前线上取号避免排队"
}
```

完整接口文档见 [API文档.md](API文档.md)。

---

## Java wft 主项目

### 技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | Spring Boot | 2.3.12 |
| ORM | MyBatis-Plus | 3.4.3 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis (Lettuce) | 6.0+ |
| 分布式锁 | Redisson | 3.20.0 |
| 消息队列 | Kafka | 3.4.0 |
| 工具库 | Hutool | 5.7.17 |
| 自研 | AdaptiveBufferedThreadPoolExecutor | 1.0-SNAPSHOT |

### API 模块一览

#### 用户模块 `/user`

| 端点 | 说明 | 亮点 |
|---|---|---|
| `POST /user/code` | 发送短信验证码 | Session 存储 |
| `POST /user/login` | 手机号+验证码/密码登录 | 返回 Token，Redis 存储 |
| `GET /user/me` | 获取当前用户信息 | 拦截器自动注入 |
| `POST /user/sign` | 用户签到 | **Redis BitMap** 极低空间开销 |
| `GET /user/sign/count` | 连续签到天数 | BitMap 位运算统计 |

#### 店铺模块 `/shop`

| 端点 | 说明 | 亮点 |
|---|---|---|
| `GET /shop/{id}` | 查询店铺详情 | **互斥锁防击穿** + 空值防穿透 |
| `POST /shop` | 新增店铺 | — |
| `PUT /shop` | 更新店铺 | 写后删缓存，Cache Aside |
| `GET /shop/of/type` | 按分类分页 | Redis GEO 附近排序 |

#### 笔记模块 `/blog`

| 端点 | 说明 | 亮点 |
|---|---|---|
| `POST /blog` | 发布笔记 | **推模式 Feed 流**，写入粉丝收件箱 |
| `GET /blog/hot` | 热门笔记 | 按点赞数降序 |
| `GET /blog/{id}` | 笔记详情 | 含作者信息 + 当前用户点赞状态 |
| `PUT /blog/like/{id}` | 点赞/取消 | **Redis ZSET** 幂等，天然按时间排序 |
| `GET /blog/likes/{id}` | 点赞排行榜 | ZSET TOP5 按时间升序 |
| `GET /blog/of/follow` | 关注 Feed 流 | **滚动分页**（Scroll Result），推模式 |

#### 秒杀模块 `/voucher-order`

两套实现，`application.yaml` 中一键切换：

| 模式 | 配置值 | 特点 |
|---|---|---|
| **Kafka 版** | `wft.seckill.mode=kafka` | 高吞吐，背压流控，动态线程池 |
| **Redis 版** | `wft.seckill.mode=redis` | 轻量级，Redis Stream 消息队列 |

```
秒杀流程:

  用户请求
    │
    ▼
  Redis Lua 脚本（原子操作）
    ├─ 校验库存（stock > 0）
    ├─ 校验一人一单（userId 去重）
    └─ 扣减库存 + 生成订单ID
    │
    ▼
  ┌─────────────┬─────────────┐
  │ Kafka 模式   │ Redis 模式   │
  │ 发送到 Topic │ 写入 Stream  │
  │ 批量拉取     │ 单线程消费    │
  │ LADBTP 并发 │ pending 兜底 │
  │ 批量提交offset│ XACK 确认    │
  └─────────────┴─────────────┘
    │
    ▼
  Redisson 分布式锁 → DB 扣库存 → 写入订单
```

#### 关注模块 `/follow`

| 端点 | 说明 | 亮点 |
|---|---|---|
| `PUT /follow/{id}/{isFollow}` | 关注/取关 | DB + Redis Set 双写 |
| `GET /follow/or/not/{id}` | 是否已关注 | — |
| `GET /follow/common/{id}` | 共同关注 | **Redis Set 交集** O(N) 查询 |

---

## 核心技术亮点

### 1. 负载感知自适应动态线程池（LADBTP）

`AdaptiveBufferedThreadPoolExecutor` 是自研线程池，相比 `ThreadPoolExecutor`：

- **CPU 负载感知**: 每 5 秒采样系统 CPU，结合线程池负载动态调整策略
- **本地缓冲队列**: 当线程池队列使用率超过阈值（70%），自动暂停上游消息拉取（背压）
- **退避空转机制**: 高负载下指数退避重试入队，避免盲目创建线程
- **强制入队策略**: 配合 `isPreventRejection` 开关，高负载下 CPU 空转 + 阻塞等待双策略

Kafka 秒杀模式中配合 LADBTP 实现了完整的背压流控链路：
```
Kafka Consumer → BufferQueue(70%暂停拉取) → LADBTP(负载感知扩容) → 业务处理
```

### 2. Redis Lua 原子秒杀

```lua
-- seckill-kafka.lua 核心逻辑
local voucherId = KEYS[1]
local userId = ARGV[1]
-- 1. 校验库存
if (tonumber(redis.call('get', stockKey)) <= 0) then return 1 end
-- 2. 校验一人一单
if (redis.call('sismember', orderKey, userId) == 1) then return 2 end
-- 3. 扣库存 + 记录用户
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0  -- 抢券成功，后续 Kafka/Stream 异步写库
```

### 3. Kafka 异步削峰

- **批量拉取**: `poll(Duration.ofMillis(100))` 每次最多 2000 条
- **批量处理**: 一次 `drainTo(batch, 100)`，`CompletableFuture` 并发执行
- **批量提交**: 记录已完成 offset，定时批量 `commitAsync`
- **慢任务重试**: 每条消息最多重试 3 次，彻底失败可入死信队列
- **背压流控**: `shouldPausePulling()` 检测 `bufferQueue` 和线程池队列使用率

### 4. Redis Stream 轻量秒杀

- `XREADGROUP` 阻塞读，单线程消费
- `pending-list` 异常兜底：处理失败自动重试 pending 消息
- `XACK` 确认消费完成

### 5. 缓存深度加固

| 场景 | 策略 | 实现 |
|---|---|---|
| 缓存穿透 | 空对象缓存 | 查不到的 Key 写入空值，短 TTL |
| 缓存击穿 | 互斥锁重构 | `setIfAbsent` 获取重建锁，只有一个请求穿透 DB |
| 缓存雪崩 | 随机 TTL | 过期时间加随机偏移 |
| 一致性 | Cache Aside | 写 DB → 删缓存；读缓存 → 未命中 → 查 DB → 写缓存 |

### 6. 社交 Feed 流（推模式）

发布笔记时写出所有粉丝的 Redis ZSET 收件箱，滚动分页读取：
```
ZREVRANGEBYSCORE feed:user:{id} max min LIMIT offset count
```

### 7. 全局唯一 ID 生成器

64 位 ID = 32 位时间戳（相对 2025-01-01）+ 32 位 Redis 自增序列号，每日千万级无压力。

### 8. BitMap 签到

用户签到用 Redis BitMap 存储，每月 1 亿用户仅需 ~400MB。通过 `BITFIELD` 统计连续签到天数。

---

## 项目结构

```
worry-free-travel/
│
├── src/main/java/com/wft/
│   ├── WftApplication.java         # Spring Boot 入口
│   ├── config/                     # Kafka/Redis/Mvc/MyBatis 配置
│   ├── controller/                 # 11 个 REST Controller
│   │   ├── UserController.java     #   用户登录/签到
│   │   ├── ShopController.java     #   店铺 CRUD + 缓存
│   │   ├── BlogController.java     #   笔记发布/点赞/Feed
│   │   ├── FollowController.java   #   关注/取关/共同关注
│   │   ├── VoucherController.java  #   优惠券管理
│   │   ├── VoucherOrderController  #   秒杀抢券
│   │   └── AgentDataController.java#   Agent 数据读取 (5个GET)
│   ├── service/impl/
│   │   ├── VoucherOrderServiceKafkaImpl  # 秒杀 Kafka 版
│   │   ├── VoucherOrderServiceRedisImpl  # 秒杀 Redis Stream 版
│   │   ├── BlogServiceImpl         #   笔记 + Feed 流
│   │   ├── ShopServiceImpl         #   店铺 + 缓存加固
│   │   └── UserServiceImpl         #   登录 + 签到
│   ├── entity/                     # 17 个数据库实体
│   ├── mapper/                     # MyBatis-Plus Mapper
│   ├── dto/                        # Result/UserDTO/ScrollResult
│   └── utils/                      # 工具类
│       ├── RedisIdWorker.java      #   全局唯一 ID 生成器
│       ├── SimpleRedisLock.java    #   Redis 分布式锁
│       ├── ThreadPoolConstants.java#   线程池参数配置
│       └── UserHolder.java         #   ThreadLocal 用户上下文
│
├── Agent/dataSource-mcp-server/
│   ├── py/                         # Python LangGraph Agent
│   │   ├── main.py                 #   FastAPI 入口 (:8090)
│   │   ├── graph/agent_graph.py    #   5 节点 LangGraph 工作流
│   │   ├── client/
│   │   │   ├── java_client.py      #   Java API 直连（兜底）
│   │   │   └── mcp_client.py       #   MCP Server 客户端（主路径）
│   │   ├── models/schemas.py       #   Pydantic 模型
│   │   └── config.py               #   环境变量配置
│   └── spring-ai-mcp/data-mcp-service/
│       ├── DataMcpServiceApplication  # MCP Server (:8020)
│       ├── service/DataService.java   # @Tool: 导航器+聚合器
│       ├── controller/McpRestController  # REST 包装
│       └── resources/config/data-sources.json  # 5个数据源
│
├── AdaptiveBufferedThreadPoolExecutor/  # 自研动态线程池
│
├── src/main/resources/
│   ├── db/                        # 数据库脚本
│   │   ├── 1.sql                  #   完整 dump
│   │   ├── agent_migration.sql    #   Agent 建表 + 种子
│   │   ├── seed_data.sql          #   64店 14笔记种子
│   │   ├── fix_tags.sql           #   标签修正
│   │   └── fix_intent.sql         #   意图关键词修正
│   ├── seckill-kafka.lua          # Kafka 版秒杀 Lua
│   ├── seckill.lua                # Redis 版秒杀 Lua
│   └── application.yaml           # 系统配置
│
├── API文档.md                     # 完整接口文档 (31+5 端点)
├── pom.xml                        # Maven (Java 8)
└── README.md
```

---

## 配置参考

### application.yaml

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/worry_free_travel
    username: root
    password: 123456
  redis:
    host: 127.0.0.1
    port: 6379
    password: "123456"
    lettuce:
      pool:
        max-active: 100
        max-idle: 50

# 秒杀引擎切换
wft:
  seckill:
    mode: kafka    # 推荐，高吞吐背压
    # mode: redis  # 轻量级
```

### Agent 环境变量

| 变量 | 默认值 | 必填 |
|---|---|---|
| `LLM_BASE_URL` | `https://api.openai.com` | — |
| `LLM_API_KEY` | — | ✅ |
| `LLM_MODEL` | `gpt-3.5-turbo` | — |
| `JAVA_API_BASE` | `http://localhost:8081/api/agent` | — |
| `MCP_BASE_URL` | `http://localhost:8020` | — |
| `MCP_ENABLED` | `true` | — |
| `RATE_LIMIT_MAX` | `20` | — |
| `SERVER_PORT` | `8090` | — |

---

## License

MIT
