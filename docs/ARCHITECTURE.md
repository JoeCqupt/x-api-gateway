# X-API-Gateway 使用与架构文档

> **版本**: 1.0-SNAPSHOT  
> **定位**: 生产级、高性能、全异步 Java API 网关  
> **技术栈**: Reactor Netty / Java 1.8+ / Maven 多模块 / 零 Spring 依赖

---

## 目录

1. [项目概述](#1-项目概述)
2. [快速开始](#2-快速开始)
3. [架构设计](#3-架构设计)
4. [模块结构](#4-模块结构)
5. [配置参考](#5-配置参考)
6. [路由与谓词](#6-路由与谓词)
7. [Filter 体系](#7-filter-体系)
8. [负载均衡](#8-负载均衡)
9. [服务发现](#9-服务发现)
10. [限流与熔断](#10-限流与熔断)
11. [可观测性](#11-可观测性)
12. [Admin API](#12-admin-api)
13. [安全体系](#13-安全体系)
14. [插件机制](#14-插件机制)
15. [部署指南](#15-部署指南)
16. [性能基准](#16-性能基准)
17. [编程式 API](#17-编程式-api)

---

## 1. 项目概述

X-API-Gateway 是一个基于 Reactor Netty 的全异步、非阻塞 API 网关，不依赖任何 Spring 框架。它提供：

- **高性能反向代理** — 基于 Netty 事件循环 + 零拷贝 ByteBuf 流式转发
- **灵活路由** — Radix Tree 路由表 + 9 种路由谓词（Path/Method/Header/Host/Query/Cookie/RemoteAddr/Weight/Time）
- **丰富 Filter** — 18+ 内置 Filter（限流、熔断、认证、CORS、请求改写等）
- **多种负载均衡** — 加权轮询、随机、最少连接、一致性哈希、IP 哈希
- **配置热更新** — 文件监听 + Nacos 配置中心推送
- **服务发现** — Nacos 服务发现集成，动态上游实例管理
- **生产级运维** — Admin API、健康检查、优雅关闭、Metrics、分布式追踪、Access Log
- **WebSocket 代理** — 完整 WebSocket 上游代理
- **插件扩展** — Java SPI 插件加载机制

### 设计约束

| 约束 | 说明 |
|------|------|
| Java 版本 | 1.8+（编译目标 1.8） |
| 网络层 | Reactor Netty（Netty 4.1+） |
| 框架依赖 | 零 Spring 依赖 |
| 配置格式 | YAML |
| 构建工具 | Maven |

---

## 2. 快速开始

### 2.1 构建

```bash
# 编译所有模块
mvn clean package -DskipTests

# 产出 fat-jar
ls x-api-gateway-dist/target/x-api-gateway-dist-*.jar
```

### 2.2 运行

```bash
# 使用默认配置启动
java -jar x-api-gateway-dist/target/x-api-gateway-dist-1.0-SNAPSHOT.jar

# 指定配置文件
java -jar x-api-gateway-dist/target/x-api-gateway-dist-1.0-SNAPSHOT.jar -c /path/to/config.yml
```

### 2.3 最小配置示例

```yaml
server:
  port: 8080

routes:
  - id: my-service
    predicates:
      - name: Path
        config:
          patterns:
            - /api/**
    endpoint:
      name: Http
      config:
        urls:
          - http://127.0.0.1:9000
```

### 2.4 验证

```bash
# 通过网关访问后端服务
curl http://localhost:8080/api/hello

# 查看 Admin API 健康状态
curl http://localhost:9090/admin/health
```

---

## 3. 架构设计

### 3.1 整体架构

```
                       ┌─────────────────────────────────────────────────┐
                       │                X-API-Gateway                    │
                       │                                                 │
  Client ─── TLS ───►  │  Netty Server → Handler Chain → Filter Chain   │
                       │       │              │               │          │
                       │       ▼              ▼               ▼          │
                       │  Metrics/Log    Route Table    LoadBalancer ────┼──► Upstream
                       │                                                 │
                       │  ┌──────────────────────────────────────────┐   │
                       │  │         Admin API (独立端口 9090)        │   │
                       │  └──────────────────────────────────────────┘   │
                       └─────────────────────────────────────────────────┘
```

### 3.2 分层架构

```
┌─────────────────────────────────────────────────┐
│  Transport Layer                                 │
│    Netty Server (Boss/Worker EventLoopGroup)     │
│    TLS 终止, HTTP/1.1, HTTP/2, WebSocket        │
├─────────────────────────────────────────────────┤
│  Handler Chain                                   │
│    ReactorHttpHandler → ExceptionHandler         │
│    → MetricsHandler → AccessLogHandler           │
│    → RoutePredicateHandler → FilteringHandler    │
├─────────────────────────────────────────────────┤
│  Routing Layer                                   │
│    Radix Tree 路由表 + 组合谓词匹配              │
├─────────────────────────────────────────────────┤
│  Filter Layer                                    │
│    Pre-Filters → RoutingFilter → Post-Filters    │
├─────────────────────────────────────────────────┤
│  Endpoint Layer                                  │
│    HttpEndpoint / WebSocketEndpoint              │
│    连接池 + 负载均衡 + 健康检查                  │
├─────────────────────────────────────────────────┤
│  Infrastructure Layer                            │
│    Config (File/Nacos) + Metrics + Tracing       │
│    Admin API + Plugin SPI                        │
└─────────────────────────────────────────────────┘
```

### 3.3 请求处理流程

1. 客户端请求到达 Netty Server
2. `ReactorHttpHandler` 创建 `ServerWebExchange`
3. `ExceptionHandlingWebHandler` 包装异常处理
4. `MetricsWebHandler` 采集请求指标
5. `AccessLogWebHandler` 记录访问日志
6. `RoutePredicateWebHandler` 匹配路由（Radix Tree + 组合谓词）
7. `FilteringWebHandler` 合并并排序 globalFilters + defaultFilters + routeFilters
8. Filter Chain Pre 阶段执行（Auth → RateLimit → Transform → ...）
9. `RoutingFilter` 通过 `Endpoint.invoke()` 转发请求到上游
10. Filter Chain Post 阶段执行（响应改写等）
11. 响应写回客户端

---

## 4. 模块结构

```
x-api-gateway/
├── x-api-gateway-server/          # 核心服务器（所有核心功能）
│   ├── bootstrap/                  #   启动引导 (GatewayMain, GatewayServerBootstrap)
│   ├── handler/                    #   Handler Chain 实现
│   ├── route/                      #   路由引擎 (RadixTree, RouteLocator, Builder)
│   ├── filter/                     #   Filter 体系 (18+ 内置 Filter)
│   ├── filter/global/              #   全局 Filter (Routing, AccessLog, Tracing)
│   ├── endpoint/                   #   端点 (Http, WebSocket)
│   ├── predicate/                  #   路由谓词 (9 种)
│   ├── loadbalance/                #   负载均衡策略 (5 种)
│   ├── config/                     #   配置管理 + 热更新
│   ├── admin/                      #   Admin API
│   ├── healthcheck/                #   上游健康检查
│   ├── ratelimit/                  #   限流器 (Local/Redis/SlidingWindow)
│   ├── metrics/                    #   指标采集
│   ├── discovery/                  #   服务发现 SPI
│   ├── plugin/                     #   插件 SPI
│   └── event/                      #   事件总线
│
├── x-api-gateway-config-nacos/    # Nacos 配置源 + 服务发现
├── x-api-gateway-integration-tests/ # 集成测试
├── x-api-gateway-benchmark/       # JMH 性能基准测试
├── x-api-gateway-dist/            # Fat-jar 打包分发
├── deploy/                         # K8s 部署模板
├── docker/                         # Docker 构建文件
├── bin/                            # 启动脚本
└── docs/                           # 文档
```

### 依赖关系

```
x-api-gateway-server (核心)
         ▲
         │
x-api-gateway-config-nacos (可选，Nacos 集成)
         ▲
         │
x-api-gateway-dist (打包，依赖 server + nacos)
```

---

## 5. 配置参考

### 5.1 完整配置

```yaml
# ===== 服务器配置 =====
server:
  port: 8080                    # 监听端口
  shutdownTimeoutMs: 30000      # 优雅关闭超时 (ms)
  http2Enabled: false           # 启用 HTTP/2 (h2c)
  bossThreads: 1                # Netty Boss 线程数
  workerThreads: 0              # Netty Worker 线程数 (0=auto)
  maxContentLength: 10485760    # 最大请求体 (10MB)
  backlog: 1024                 # TCP backlog
  tcpNoDelay: true              # TCP_NODELAY
  soKeepAlive: true             # SO_KEEPALIVE
  tls:                          # TLS 配置
    enabled: false
    certPath: /etc/gateway/tls/cert.pem
    keyPath: /etc/gateway/tls/key.pem
    protocols:                  # TLS 协议版本
      - TLSv1.2
      - TLSv1.3

# ===== Admin API =====
admin:
  enabled: true
  port: 9090
  accessToken: ""               # Bearer Token 认证 (生产环境必须设置)

# ===== 健康检查 =====
healthCheck:
  enabled: true
  intervalMs: 10000             # 检查间隔 (ms)
  path: /health                 # 探测路径
  unhealthyThreshold: 3         # 连续失败 N 次标记不健康
  healthyThreshold: 2           # 连续成功 N 次恢复
  timeoutMs: 3000               # 探测超时 (ms)

# ===== 上游连接池 =====
upstream:
  connectTimeout: 3000          # 连接超时 (ms)
  readTimeout: 10000            # 读超时 (ms)
  poolMaxConnections: 256       # 最大连接数
  poolMaxIdleTime: 60000        # 空闲连接超时 (ms)
  poolMaxLifeTime: 300000       # 连接最大生命周期 (ms)
  poolEvictionInterval: 30000   # 后台清理间隔 (ms)

# ===== Nacos 配置中心 (可选) =====
nacos:
  enabled: false
  serverAddr: 127.0.0.1:8848
  dataId: x-api-gateway
  group: DEFAULT_GROUP
  discoveryEnabled: false       # 启用 Nacos 服务发现

# ===== 插件 =====
plugins:
  enabled: false
  pluginsDir: plugins           # 外部插件 JAR 目录

# ===== 分布式追踪 (可选) =====
tracing:
  enabled: false
  serviceName: x-api-gateway

# ===== 默认 Filter (应用于所有路由) =====
defaultFilters:
  - name: RequestId
  - name: SecureHeaders

# ===== 路由配置 =====
routes:
  - id: example-service
    order: 1
    predicates:
      - name: Path
        config:
          patterns:
            - /api/example/**
      - name: Method
        config:
          methods: [GET, POST]
    filters:
      - name: StripPrefix
        config:
          parts: 1
      - name: AddHeader
        config:
          headers:
            X-Gateway: x-api-gateway
    endpoint:
      name: Http
      config:
        urls:
          - http://10.0.1.1:8080
          - http://10.0.1.2:8080
        weights:
          - 80
          - 20
        connectTimeout: 3000
        readTimeout: 10000
        poolMaxConnections: 256
```

---

## 6. 路由与谓词

### 6.1 路由匹配流程

```
请求到达 → Radix Tree 快速路径匹配 O(k) → 组合谓词精确匹配 → Order 排序取优先级最高
```

### 6.2 内置谓词

| 谓词 | 说明 | 配置示例 |
|------|------|----------|
| **Path** | 路径匹配 (Ant 风格 + 正则) | `patterns: [/api/v1/**]` |
| **Method** | HTTP 方法 | `methods: [GET, POST]` |
| **Header** | 请求头匹配 (支持正则) | `name: X-Token, regex: .+` |
| **Host** | 域名匹配 (支持通配符) | `patterns: [**.example.com]` |
| **Query** | 查询参数匹配 | `param: version, regex: v[0-9]+` |
| **Cookie** | Cookie 匹配 | `name: session, regex: .+` |
| **RemoteAddr** | 客户端 IP 匹配 (CIDR) | `sources: [192.168.0.0/16]` |
| **Weight** | 权重路由 (灰度发布) | `group: svc-a, weight: 80` |
| **Time** | 时间窗口 | `after: 2024-01-01T00:00:00` |

### 6.3 路由配置示例

```yaml
routes:
  # 基于路径和方法匹配
  - id: user-service
    predicates:
      - name: Path
        config:
          patterns: ["/api/users/**"]
      - name: Method
        config:
          methods: [GET, POST, PUT, DELETE]
    endpoint:
      name: Http
      config:
        urls: [http://user-svc:8080]

  # 基于 Header 版本路由
  - id: order-v2
    order: 1
    predicates:
      - name: Path
        config:
          patterns: ["/api/orders/**"]
      - name: Header
        config:
          name: X-Api-Version
          regex: "v[2-9]"
    endpoint:
      name: Http
      config:
        urls: [http://order-svc-v2:8080]

  # 灰度发布 (80% 流量到 v1, 20% 到 v2)
  - id: canary-v1
    predicates:
      - name: Path
        config:
          patterns: ["/api/product/**"]
      - name: Weight
        config:
          group: product
          weight: 80
    endpoint:
      name: Http
      config:
        urls: [http://product-v1:8080]

  - id: canary-v2
    predicates:
      - name: Path
        config:
          patterns: ["/api/product/**"]
      - name: Weight
        config:
          group: product
          weight: 20
    endpoint:
      name: Http
      config:
        urls: [http://product-v2:8080]

  # WebSocket 代理
  - id: ws-service
    predicates:
      - name: Path
        config:
          patterns: ["/ws/**"]
    endpoint:
      name: WebSocket
      config:
        url: ws://ws-backend:8080

  # 基于 Nacos 服务发现
  - id: dynamic-service
    predicates:
      - name: Path
        config:
          patterns: ["/api/dynamic/**"]
    endpoint:
      name: Http
      config:
        serviceId: my-service-name
```

---

## 7. Filter 体系

### 7.1 Filter 执行模型

```
Pre 阶段 (请求方向 →)
──────────────────────────────────────────►
  Filter-1 → Filter-2 → ... → RoutingFilter → Upstream
◄──────────────────────────────────────────
Post 阶段 (响应方向 ←)
```

### 7.2 内置 Filter 清单

#### Pre-Filters

| Filter | 功能 | 配置参数 |
|--------|------|----------|
| **RequestId** | 生成 X-Request-Id | 无 |
| **SecureHeaders** | 安全响应头 + 清理内部头 | 无 |
| **Cors** | CORS 跨域处理 | `allowedOrigins, allowedMethods, allowedHeaders, maxAge` |
| **Auth** | JWT / API-Key / HMAC 认证 | `type, secretKey, headerName` |
| **IpFilter** | IP 黑白名单 | `whitelist, blacklist` (CIDR 格式) |
| **RateLimit** | 限流 | `replenishRate, burstCapacity, keyResolver, type` |
| **Bulkhead** | 并发控制 | `maxConcurrent, maxWait` |
| **CircuitBreaker** | 熔断器 | `failureRateThreshold, waitDurationInOpenState, slidingWindowSize` |
| **StripPrefix** | 路径前缀裁剪 | `parts` |
| **RewritePath** | 路径重写 | `regex, replacement` |
| **AddHeader** | 添加请求头 | `headers: {key: value}` |
| **RemoveHeader** | 移除请求头 | `headers: [name1, name2]` |
| **RequestSize** | 请求体大小限制 | `maxSize` (bytes) |
| **Retry** | 重试 | `retries, statuses, methods, backoff` |

#### Post-Filters

| Filter | 功能 | 配置参数 |
|--------|------|----------|
| **AddResponseHeader** | 添加响应头 | `headers: {key: value}` |
| **RemoveResponseHeader** | 移除响应头 | `headers: [name1, name2]` |
| **RequestBodyRewrite** | 请求体改写 | `contentType, template` |
| **ResponseBodyRewrite** | 响应体改写 | `contentType, template` |

#### Global Filters (自动应用)

| Filter | 功能 |
|--------|------|
| **AccessLogGatewayFilter** | JSON 格式访问日志 |
| **RoutingFilter** | 核心路由转发 |
| **TracingFilter** | OpenTelemetry 分布式追踪 (可选) |

### 7.3 Filter 配置示例

```yaml
routes:
  - id: protected-service
    predicates:
      - name: Path
        config:
          patterns: ["/api/protected/**"]
    filters:
      # 路径裁剪: /api/protected/xxx → /xxx
      - name: StripPrefix
        config:
          parts: 2

      # JWT 认证
      - name: Auth
        config:
          type: jwt
          secretKey: ${JWT_SECRET}

      # 限流: 每秒 100 请求, 突发 200
      - name: RateLimit
        config:
          replenishRate: 100
          burstCapacity: 200
          keyResolver: remoteAddr

      # 熔断: 50% 失败率触发
      - name: CircuitBreaker
        config:
          failureRateThreshold: 50
          waitDurationInOpenState: 30000
          slidingWindowSize: 100

      # 重试: 最多 3 次, 仅 GET, 仅 502/503
      - name: Retry
        config:
          retries: 3
          statuses: [502, 503]
          methods: [GET]
          backoff:
            firstBackoff: 50
            maxBackoff: 500
            factor: 2

      # 添加请求头
      - name: AddHeader
        config:
          headers:
            X-Gateway: x-api-gateway
            X-Env: production
    endpoint:
      name: Http
      config:
        urls: [http://backend:8080]
```

---

## 8. 负载均衡

### 8.1 负载均衡策略

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| **WeightedRoundRobin** | 平滑加权轮询 (默认) | 通用 |
| **Random** | 加权随机 | 无状态服务 |
| **LeastConnections** | 最少活跃连接 | 长连接 / 处理时间差异大 |
| **ConsistentHash** | Ketama 一致性哈希 | 需要会话亲和 |
| **IPHash** | 客户端 IP 哈希 | 简单会话亲和 |

### 8.2 配置

```yaml
endpoint:
  name: Http
  config:
    urls:
      - http://10.0.1.1:8080
      - http://10.0.1.2:8080
      - http://10.0.1.3:8080
    weights:
      - 50
      - 30
      - 20
    loadBalancer: WeightedRoundRobin  # 可选值见上表
```

### 8.3 健康检查

上游健康检查通过 `HealthChecker` 实现：

- **主动检查**: 定时发送 HTTP GET 到上游 `/health` 端点
- **被动检查**: 根据转发结果自动调整上游状态
- 连续 N 次失败标记为不健康，连续 M 次成功恢复

```yaml
healthCheck:
  enabled: true
  intervalMs: 10000
  path: /health
  unhealthyThreshold: 3
  healthyThreshold: 2
  timeoutMs: 3000
```

---

## 9. 服务发现

### 9.1 Nacos 服务发现

当 `nacos.discoveryEnabled=true` 时，路由可以通过 `serviceId` 动态获取上游实例列表。

```yaml
nacos:
  enabled: true
  serverAddr: 127.0.0.1:8848
  discoveryEnabled: true

routes:
  - id: dynamic-route
    predicates:
      - name: Path
        config:
          patterns: ["/api/svc/**"]
    endpoint:
      name: Http
      config:
        serviceId: my-service    # Nacos 上注册的服务名
```

### 9.2 工作原理

1. 网关启动时通过反射加载 `x-api-gateway-config-nacos` 模块中的 `NacosServiceDiscoveryFactory`
2. `NacosServiceDiscovery` 实现 `ServiceDiscovery` SPI 接口
3. `HttpEndpoint` 检测到 `serviceId` 配置时，调用 `ServiceDiscovery.getInstances()` 获取实例列表
4. 通过 `subscribe()` 监听实例变更，动态更新 `UpstreamTarget` 列表

### 9.3 服务发现 SPI

```java
public interface ServiceDiscovery {
    List<ServiceInstance> getInstances(String serviceId);
    void subscribe(String serviceId, Consumer<List<ServiceInstance>> listener);
    void start();
    void stop();
}
```

可通过实现此接口扩展其他注册中心（Consul、etcd、ZooKeeper 等）。

---

## 10. 限流与熔断

### 10.1 限流器

| 类型 | 实现类 | 说明 |
|------|--------|------|
| 本地令牌桶 | `LocalRateLimiter` | 单机限流，无锁 CAS 实现 |
| 滑动窗口 | `SlidingWindowRateLimiter` | 固定窗口计数 |
| Redis 分布式 | `RedisRateLimiter` | 基于 Lua 脚本的分布式令牌桶 |

```yaml
filters:
  - name: RateLimit
    config:
      replenishRate: 100        # 令牌填充速率 (个/秒)
      burstCapacity: 200        # 桶容量
      keyResolver: remoteAddr   # 限流 Key: remoteAddr / header / routeId
      type: local               # local / redis
      redisUri: redis://localhost:6379  # type=redis 时必填
```

### 10.2 熔断器

基于滑动窗口的熔断器，支持 CLOSED → OPEN → HALF_OPEN 状态机。

```yaml
filters:
  - name: CircuitBreaker
    config:
      failureRateThreshold: 50    # 失败率阈值 (%)
      waitDurationInOpenState: 30000  # OPEN 等待时间 (ms)
      slidingWindowSize: 100      # 滑动窗口大小
      halfOpenPermits: 5          # HALF_OPEN 探测请求数
```

### 10.3 并发控制 (Bulkhead)

```yaml
filters:
  - name: Bulkhead
    config:
      maxConcurrent: 100          # 最大并发请求数
      maxWait: 500                # 等待获取许可超时 (ms)
```

---

## 11. 可观测性

### 11.1 Access Log

所有请求自动记录 JSON 格式的访问日志：

```json
{
  "requestId": "a1b2c3d4-e5f6-7890",
  "timestamp": "2024-01-15T10:30:45.123Z",
  "clientIp": "192.168.1.100",
  "method": "GET",
  "path": "/api/users/123",
  "statusCode": 200,
  "durationMs": 15,
  "routeId": "user-service",
  "upstreamHost": "10.0.1.1:8080"
}
```

### 11.2 Metrics

`MetricsCollector` 采集以下指标：

- 请求总数（按 route、status_code 分组）
- 请求延迟分布
- 活跃请求数
- 上游连接池状态
- 上游健康状态

通过 Admin API `/admin/metrics` 暴露。

### 11.3 分布式追踪

集成 OpenTelemetry，需要 `opentelemetry-api` 在 classpath：

```yaml
tracing:
  enabled: true
  serviceName: x-api-gateway
```

`TracingFilter` 自动从请求头提取 W3C TraceContext，创建 gateway span，并注入到上游请求。

---

## 12. Admin API

Admin API 运行在独立端口（默认 9090），提供运维管理能力。

### 12.1 端点列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/health` | 健康检查 |
| GET | `/admin/ready` | 就绪检查 |
| GET | `/admin/info` | 网关信息（版本、启动时间等） |
| GET | `/admin/routes` | 查看所有路由 |
| GET | `/admin/routes/{id}` | 查看单个路由 |
| POST | `/admin/routes` | 动态添加路由 |
| PUT | `/admin/routes/{id}` | 更新路由 |
| DELETE | `/admin/routes/{id}` | 删除路由 |
| POST | `/admin/routes/refresh` | 强制刷新路由表 |
| GET | `/admin/filters` | 查看可用 Filter |
| GET | `/admin/predicates` | 查看可用 Predicate |
| GET | `/admin/endpoints` | 查看可用 Endpoint 类型 |
| GET | `/admin/metrics` | 指标数据 |
| GET | `/admin/plugins` | 查看已加载插件 |
| POST | `/admin/config/reload` | 重新加载配置 |
| GET | `/admin/services/{serviceId}` | 查看服务发现实例 |

### 12.2 认证

通过 Bearer Token 认证，配置 `admin.accessToken`：

```bash
curl -H "Authorization: Bearer your-token" http://localhost:9090/admin/routes
```

**安全提醒**: 生产环境必须设置 `accessToken`，否则 Admin API 完全开放。

---

## 13. 安全体系

### 13.1 多层安全

| 层次 | 功能 | Filter |
|------|------|--------|
| Transport | TLS 终止 | 服务器配置 |
| Network | IP 黑白名单 | `IpFilter` |
| Application | JWT/API-Key/HMAC 认证 | `Auth` |
| Application | CORS 跨域控制 | `Cors` |
| Application | 安全响应头 | `SecureHeaders` |
| Rate Limiting | 限流 + 熔断 + 并发控制 | `RateLimit` / `CircuitBreaker` / `Bulkhead` |
| Request | 请求体大小限制 | `RequestSize` |

### 13.2 TLS 配置

```yaml
server:
  tls:
    enabled: true
    certPath: /etc/gateway/tls/cert.pem
    keyPath: /etc/gateway/tls/key.pem
    protocols: [TLSv1.2, TLSv1.3]
```

### 13.3 CORS 配置

```yaml
defaultFilters:
  - name: Cors
    config:
      allowedOrigins: ["https://example.com"]
      allowedMethods: [GET, POST, PUT, DELETE]
      allowedHeaders: ["*"]
      maxAge: 3600
```

---

## 14. 插件机制

### 14.1 Plugin SPI

```java
public interface GatewayPlugin {
    String name();
    void onInstall(PluginContext context);
    void onStart(PluginContext context);
    void onStop(PluginContext context);
}
```

### 14.2 使用方式

1. 实现 `GatewayPlugin` 接口
2. 在 `META-INF/services/io.github.xinfra.lab.gateway.plugin.GatewayPlugin` 中注册
3. 打包为 JAR 放入 `plugins/` 目录
4. 配置启用：

```yaml
plugins:
  enabled: true
  pluginsDir: plugins
```

### 14.3 PluginContext

插件可通过 `PluginContext` 注册自定义 Filter 工厂、Predicate 工厂、Endpoint 工厂。

---

## 15. 部署指南

### 15.1 Docker 部署

```bash
# 构建镜像
mvn clean package -DskipTests
docker build -f docker/Dockerfile -t x-api-gateway:latest .

# 运行
docker run -d -p 8080:8080 -p 9090:9090 \
  -v /path/to/config.yml:/app/config.yml \
  -e GATEWAY_ADMIN_TOKEN=your-secret-token \
  x-api-gateway:latest
```

### 15.2 Kubernetes 部署

项目提供了完整的 K8s 部署模板：

- `deploy/k8s/deployment.yaml` — Deployment + Service
- `deploy/k8s/configmap.yaml` — ConfigMap（网关配置）

```bash
# 应用配置
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/deployment.yaml
```

K8s 部署特性：
- 3 副本滚动更新
- Liveness/Readiness 探针（Admin API）
- 优雅关闭（`terminationGracePeriodSeconds: 60`）
- 资源限制（CPU/内存）

### 15.3 启动脚本

```bash
# Linux/Mac
bin/start.sh

# 或直接
java -jar x-api-gateway-dist/target/x-api-gateway-dist-1.0-SNAPSHOT.jar \
  -c /path/to/config.yml
```

### 15.4 JVM 推荐配置

```bash
java \
  -XX:+UseG1GC \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+AlwaysPreTouch \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Dio.netty.leakDetection.level=disabled \
  -jar gateway.jar -c config.yml
```

---

## 16. 性能基准

### 16.1 JMH 基准测试

项目包含三类 JMH 基准测试：

| 基准 | 类 | 说明 |
|------|-----|------|
| Filter Chain | `FilterChainBenchmark` | Filter 链执行性能 |
| Route Match | `RouteMatchBenchmark` | Radix Tree 路由匹配性能 |
| Proxy | `ProxyBenchmark` | 端到端 HTTP 代理吞吐量 |

```bash
# 运行基准测试
mvn -pl x-api-gateway-benchmark exec:java
```

### 16.2 性能目标

| 场景 | QPS 目标 | P99 延迟 |
|------|---------|---------|
| Proxy (1KB body) | ≥ 50,000 | < 3ms |
| Proxy + 5 Filters | ≥ 40,000 | < 5ms |
| Route Matching (100 routes) | ≥ 50,000 | < 0.5ms |

---

## 17. 编程式 API

除 YAML 配置外，X-API-Gateway 支持完全编程式启动：

### 17.1 RouteLocatorBuilder

```java
RouteLocator routeLocator = RouteLocatorBuilder.builder()
    .route("user-service", spec -> spec
        .path("/api/users/**")
        .and().method("GET", "POST")
        .filters()
            .stripPrefix(1)
            .addHeader("X-Gateway", "x-api-gateway")
        .endpoint()
            .http(
                Arrays.asList("http://10.0.1.1:8080", "http://10.0.1.2:8080"),
                Arrays.asList(80, 20)
            ))
    .route("ws-service", spec -> spec
        .path("/ws/**")
        .filters()
        .endpoint()
            .webSocket("ws://ws-backend:8080"))
    .build();
```

### 17.2 GatewayServerBootstrap

```java
GatewayServerBootstrap gateway = new GatewayServerBootstrap()
    .port(8080)
    .routeLocator(routeLocator)
    .globalFilters(Arrays.asList(
        new AccessLogGatewayFilter(),
        new RoutingFilter()
    ))
    .shutdownTimeout(Duration.ofSeconds(30))
    .start();

// 优雅关闭
gateway.stop();
```

### 17.3 嵌入式使用

网关可以嵌入到现有 Java 应用中，作为一个库使用。只需依赖 `x-api-gateway-server` 模块，通过 `GatewayServerBootstrap` 编程式启动。

---

## 附录

### A. 异常处理

| 异常类型 | HTTP 状态码 | 说明 |
|----------|-----------|------|
| RouteNotFoundException | 404 | 无匹配路由 |
| UnauthorizedException | 401 | 认证失败 |
| ForbiddenException | 403 | 鉴权失败 |
| RateLimitExceededException | 429 | 限流 |
| BadRequestException | 400 | 请求错误 |
| ConnectTimeoutException | 504 | 上游连接超时 |
| ReadTimeoutException | 504 | 上游读超时 |
| CircuitBreakerOpenException | 503 | 熔断器打开 |
| InternalException | 500 | 网关内部错误 |

错误响应格式：

```json
{
  "code": 40401,
  "message": "No route found for path: /api/unknown",
  "timestamp": 1705312245000
}
```

### B. 配置热更新

支持两种方式：

1. **文件监听**: 修改配置文件后自动重新加载路由表
2. **Nacos 推送**: Nacos 配置变更通过 Listener 实时推送

热更新流程：配置变更 → 解析验证 → 编译新路由 → 原子替换路由表（volatile reference）

### C. 测试

```bash
# 运行所有测试
mvn test

# 仅运行服务器模块测试
mvn test -pl x-api-gateway-server

# 运行集成测试
mvn test -pl x-api-gateway-integration-tests
```

当前测试覆盖：401 个测试用例，0 失败。
