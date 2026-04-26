# 🚀 旅无忧 - Worry-Free Travel

## 📋 项目概述

**旅无忧**是一个高性能、高可用的在线旅游点评平台，采用Spring Boot + Redis + Kafka技术栈构建。系统支持商家管理、用户点评、优惠券秒杀等核心功能，通过分布式架构和缓存策略确保在高并发场景下的稳定性和性能。

---

## 🛠️ 技术架构

### 核心技术栈

| 类别 | 技术选型 | 版本 | 说明 |
|------|----------|------|------|
| **后端框架** | Spring Boot | 2.3.12 | 快速开发框架 |
| **编程语言** | Java | 1.8 | 稳定可靠 |
| **持久层** | MyBatis-Plus | 3.4.3 | ORM框架 |
| **数据库** | MySQL | 5.7+ | 关系型数据库 |
| **缓存** | Redis | 6.0+ | 分布式缓存 |
| **消息队列** | Kafka | 3.4.0 | 异步处理 |
| **分布式锁** | Redisson | 3.20.0 | 分布式协调 |
| **线程池** | AdaptiveBufferedThreadPoolExecutor | 1.0 | 自适应线程池 |
| **工具库** | Hutool | 5.7.17 | 工具集 |
| **构建工具** | Maven | 3.6+ | 项目构建 |



### 项目结构

```
worry-free-travel/
├── src/main/java/com/wft/
│   ├── config/                    # 配置层
│   │   ├── KafkaConfig.java       # Kafka配置
│   │   ├── MvcConfig.java         # MVC配置
│   │   ├── MybatisConfig.java     # MyBatis配置
│   │   ├── RedisConfig.java       # Redisson配置
│   │   └── WebExceptionAdvice.java # 全局异常处理
│   │
│   ├── controller/                # 控制层
│   │   ├── UserController.java    # 用户管理
│   │   ├── BlogController.java    # 点评管理
│   │   ├── ShopController.java    # 商家管理
│   │   ├── VoucherController.java # 优惠券管理
│   │   ├── VoucherOrderController.java # 订单管理
│   │   ├── FollowController.java  # 关注管理
│   │   └── ...                    # 其他控制器
│   │
│   ├── service/                   # 服务层
│   │   ├── impl/                  # 服务实现
│   │   ├── IUserService.java      # 用户服务接口
│   │   ├── IBlogService.java      # 点评服务接口
│   │   └── ...                    # 其他服务接口
│   │
│   ├── mapper/                    # 数据访问层
│   │   ├── UserMapper.java        # 用户Mapper
│   │   ├── BlogMapper.java        # 点评Mapper
│   │   └── ...                    # 其他Mapper
│   │
│   ├── entity/                    # 实体层
│   │   ├── User.java              # 用户实体
│   │   ├── Blog.java              # 点评实体
│   │   ├── Voucher.java           # 优惠券实体
│   │   └── ...                    # 其他实体
│   │
│   ├── dto/                       # 数据传输层
│   │   ├── LoginFormDTO.java      # 登录表单
│   │   ├── Result.java            # 统一返回结果
│   │   └── ...                    # 其他DTO
│   │
│   ├── utils/                     # 工具层
│   │   ├── RedisIdWorker.java     # Redis ID生成器
│   │   ├── UserHolder.java        # 用户持有器
│   │   ├── RedisConstants.java    # Redis常量
│   │   └── ...                    # 其他工具
│   │
│   └── WftApplication.java        # 启动类
│
├── src/main/resources/
│   ├── application.yaml           # 主配置文件
│   ├── mapper/                     # MyBatis映射文件
│   │   └── VoucherMapper.xml
│   ├── db/                        # 数据库脚本
│   │   └── hmdp.sql
│   ├── seckill.lua               # Redis Lua脚本
│   ├── seckill-kafka.lua         # Kafka版Lua脚本
│   └── unlock.lua                # 解锁脚本
│
└── AdaptiveBufferedThreadPoolExecutor/ # 自定义线程池模块
    ├── src/main/java/pool/
    │   ├── AdaptiveBufferedThreadPoolExecutor.java
    │   ├── RejectedExecutionHandler.java
    │   └── ...                    # 线程池相关类
    └── pom.xml





## 📈 TODO

### 短期规划 (1-3weeks)
- ⏳ 微服务化改造
- ⏳ mcp 模块


## 🔧 核心配置说明

### 1. 服务器配置

```yaml
server:
  port: 8081
  tomcat:
    threads:
      max: 500          # 最大工作线程数
      min-spare: 50     # 最小空闲线程数
    accept-count: 500   # 等待队列长度
    max-connections: 10000  # 最大连接数
```

### 2. Redis连接池配置

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 100  # 最大连接数
        max-idle: 50     # 最大空闲连接
        min-idle: 1      # 最小空闲连接
```

### 3. MyBatis-Plus配置

```yaml
mybatis-plus:
  type-aliases-package: com.wft.entity  # 实体别名扫描包
```

### 4. 秒杀模式配置

```yaml
wft:
  seckill:
    mode: kafka    # kafka: 高吞吐模式
    # mode: redis   # redis: 轻量级模式
```
