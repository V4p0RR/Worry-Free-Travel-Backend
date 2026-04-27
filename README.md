# 🚀 旅无忧 (Worry-Free Travel)

**旅无忧** 是一个基于 **Spring Boot + Redis + Kafka** 构建的高性能、高并发在线旅游点评平台。本项目深度集成多种分布式中间件，旨在解决大流量场景下的超卖、击穿、削峰等核心技术难题，是一个生产级的高并发实战项目。

---

## 💡 核心技术亮点

### 1. ⚡ 超硬核：负载感知自适应动态线程池
项目内置了自定义的 `AdaptiveBufferedThreadPoolExecutor`，相比原生 Java 线程池具有以下优势：
- **负载感知 (Load-Aware)**: 实时监控系统 CPU 和线程负载，动态调整扩容策略。
- **本地缓冲队列 (BufferQueue)**: 配合背压机制，在下游处理缓慢时自动降速，防止系统过载。
- **退避空转机制**: 在高负载下自动触发重试与等待逻辑，确保系统丝滑过渡。

### 2. 🔥 高并发秒杀：Kafka + Lua + Redis
系统实现了两套秒杀方案（通过配置自由切换），核心流程如下：
- **原子性保证**: 深度使用 **Redis Lua 脚本** 校验库存与“一人一单”，确保请求在进入业务逻辑前即完成预判。
- **Kafka 异步削峰**: 采用 Kafka 消息队列实现请求异步化，支持 **批量提交 Offset** 和 **慢任务重试**。
- **背压流控 (Backpressure)**: 消费端实时监控线程池负载，自动暂停/恢复消息拉取，彻底解决大流量冲击下的系统崩溃风险。
- **Redisson 分布式锁**: 确保分布式环境下交易的强一致性。

### 3. 🛡️ 缓存深度加固
针对分布式系统最脆弱的缓存环节，提供了全方位的防护：
- **缓存穿透**: 采用缓存空对象方案。
- **缓存击穿**: 实现基于 **互斥锁 (Mutex)** 的重构逻辑，确保热点 Key 失效时只有一个请求击穿到数据库。
- **缓存雪崩**: 随机 TTL 策略防止大面积 Key 同时过期。
- **Cache Aside 模式**: 严格保证数据库与缓存的最终一致性。

### 4. 📊 极致性能统计
- **全局唯一 ID 生成器**: 基于 Redis 的 64 位 ID 生成策略（时间戳 + 序列号），满足每日千万级订单生成需求。
- **BitMap 签到**: 利用 Redis BitMap 实现极小空间开销下的连续签到统计，1 亿用户一个月的签到记录仅需约 400MB。
- **HyperLogLog/GEO**: (扩展支持) 针对 UV 统计及附近商家搜索的极致优化。

---

## 🏗️ 系统架构

### 技术栈

| 类别 | 技术选型 | 版本 | 说明 |
| :--- | :--- | :--- | :--- |
| **核心框架** | Spring Boot | 2.3.12 | 企业级快速开发 |
| **消息中间件** | Apache Kafka | 3.4.0 | 高吞吐异步削峰 |
| **分布式缓存** | Redis | 6.0+ | 数据预减、分布式锁、BitMap |
| **分布式协调** | Redisson | 3.20.0 | 可重入分布式锁、看门狗 |
| **数据库** | MySQL | 5.7+ | 关系型存储 |
| **ORM** | MyBatis-Plus | 3.4.3 | 极速持久层开发 |
| **自研组件** | AdaptivePool | 1.0 | 负载感知型动态线程池 |

---

## 📂 项目结构

```
wft-project/
├── src/main/java/com/wft/
│   ├── config/                    # 配置中心 (Kafka/Redis/Mvc/MyBatis)
│   ├── controller/                # API 接入层 (用户/点评/商家/优惠券)
│   ├── service/                   # 核心业务逻辑
│   │   └── impl/                  # 秒杀逻辑双实现 (Kafka/Redis Stream)
│   ├── mapper/                    # 数据访问层
│   ├── entity/                    # 数据库映射对象
│   ├── dto/                       # 数据传输对象 (Result/UserDTO)
│   ├── utils/                     # 核心工具 (ID生成器/分布式锁/正则)
│   └── WftApplication.java        # 主程序入口
├── src/main/resources/
│   ├── seckill-kafka.lua          # Kafka 版秒杀脚本
│   ├── seckill.lua                # Redis Stream 版脚本
│   └── application.yaml           # 系统核心配置
└── AdaptiveBufferedThreadPoolExecutor/ # 自研动态线程池核心模块
```

---

## ⚙️ 核心配置

### 模式切换
你可以在 `application.yaml` 中一键切换秒杀引擎：
```yaml
wft:
  seckill:
    mode: kafka    # 推荐：支持高吞吐背压模式
    # mode: redis  # 基础：轻量级消息队列模式
```

### 数据库 & Redis
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/wft_db
  redis:
    host: 127.0.0.1
    lettuce:
      pool:
        max-active: 100 # 连接池深度优化
```

---

## 📈 TODO

### 短期规划 (1-3 weeks)
- ⏳ **微服务化改造**: 将秒杀与点评拆分为独立微服务。
- ⏳ **MCP 模块集成**: 引入 Model Context Protocol 支持 AI 辅助分析。
