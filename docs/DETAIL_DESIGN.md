# X-API-Gateway 详细设计文档

> **版本**: v2.0  
> **定位**: 生产级、高性能、高可用的 Java API 网关  
> **参考项目**: Spring Cloud Gateway / BFE (百度) / Nginx / Kong / Envoy / APISIX

---

## 目录

1. [设计目标与约束](#1-设计目标与约束)
2. [整体架构](#2-整体架构)
3. [模块划分](#3-模块划分)
4. [核心数据模型](#4-核心数据模型)
5. [请求处理管线 (Request Pipeline)](#5-请求处理管线)
6. [路由引擎 (Routing Engine)](#6-路由引擎)
7. [Filter 体系](#7-filter-体系)
8. [负载均衡与服务发现](#8-负载均衡与服务发现)
9. [高性能设计](#9-高性能设计)
10. [高可用设计](#10-高可用设计)
11. [配置管理 (静态 + 动态)](#11-配置管理)
12. [安全体系](#12-安全体系)
13. [可观测性 (Observability)](#13-可观测性)
14. [限流与熔断](#14-限流与熔断)
15. [Admin API](#15-admin-api)
16. [插件扩展机制](#16-插件扩展机制)
17. [部署架构](#17-部署架构)
18. [Benchmark 目标与测试方案](#18-benchmark-目标与测试方案)
19. [与现有代码的映射 & 演进路线](#19-与现有代码的映射--演进路线)

---

## 1. 设计目标与约束

### 1.1 核心目标

| 维度 | 目标 | 量化指标 |
|------|------|----------|
| **高性能** | 单实例吞吐量对标 Nginx proxy_pass 的 70%+ | ≥ 50,000 QPS (8核16G, 1KB body) |
| **低延迟** | 网关自身增加的延迟 < 1ms (P99 < 3ms) | 端到端 P99 < 10ms (上游延迟 < 5ms 场景) |
| **高可用** | 无单点, 支持优雅升级, 热加载配置 | 99.99% SLA |
| **可扩展** | 插件化架构, 用户不修改网关源码即可扩展 | SPI + 注解驱动 |
| **生产级** | 完善的可观测性、安全、运维能力 | Metrics / Tracing / AccessLog / Admin API |
| **开源级** | 清晰的模块边界、完备的文档和测试 | > 80% 行覆盖率 |

### 1.2 设计约束

- **语言**: Java 17+ (使用 Virtual Threads / Record / Sealed Class 等现代特性)
- **网络层**: Reactor Netty (Netty 4.1+), 全异步非阻塞
- **零 Spring 依赖**: 保持轻量, 不依赖 Spring Framework / Spring Boot
- **配置格式**: YAML 为主, 支持 JSON
- **构建工具**: Maven 多模块

### 1.3 非目标 (明确排除)

- 不做 Service Mesh 的 Sidecar 模式
- 不做 GraphQL / gRPC 协议网关 (初版), 预留扩展点
- 不内置分布式事务

---

## 2. 整体架构

### 2.1 架构总览

```
                           ┌─────────────────────────────────────────────────────────┐
                           │                    X-API-Gateway                        │
                           │                                                         │
  Client ──── TLS ────►    │  ┌──────────┐   ┌──────────┐   ┌──────────────────┐    │
                           │  │  Netty   │──►│ Handler  │──►│  Filter Chain    │    │
                           │  │  Server  │   │  Chain   │   │  (Pre + Route +  │    │    ┌──────────┐
                           │  │ (Boss +  │   │          │   │   Post filters)  │────┼───►│ Upstream │
                           │  │  Worker) │   │          │   └──────────────────┘    │    │ Service  │
                           │  └──────────┘   └──────────┘            │              │    └──────────┘
                           │       │                                 │              │
                           │       │              ┌──────────────────┘              │
                           │       ▼              ▼                                 │
                           │  ┌──────────┐   ┌──────────┐   ┌──────────────────┐   │
                           │  │ Metrics  │   │  Route   │   │   Config Center  │   │
                           │  │ Tracing  │   │  Table   │   │  (File/Nacos/    │   │
                           │  │ AccLog   │   │          │   │   etcd/ZK)       │   │
                           │  └──────────┘   └──────────┘   └──────────────────┘   │
                           │                                                         │
                           │  ┌──────────────────────────────────────────────────┐   │
                           │  │              Admin API (HTTP 端口)               │   │
                           │  └──────────────────────────────────────────────────┘   │
                           └─────────────────────────────────────────────────────────┘
```

### 2.2 分层架构

```
┌──────────────────────────────────────────────────────────────────────┐
│                       Transport Layer                                │
│   Netty Server (Boss/Worker EventLoopGroup, Epoll/KQueue)           │
│   TLS Termination, HTTP/1.1, HTTP/2, WebSocket Upgrade              │
├──────────────────────────────────────────────────────────────────────┤
│                       Protocol Layer                                 │
│   HTTP Codec, Request/Response Abstraction (ServerWebExchange)      │
│   Connection Pool Management, Keep-Alive                            │
├──────────────────────────────────────────────────────────────────────┤
│                       Pipeline Layer                                 │
│   Handler Chain:                                                     │
│     ReactorHttpHandler → ExceptionHandler → RoutePredicateHandler   │
│     → FilteringHandler                                               │
├──────────────────────────────────────────────────────────────────────┤
│                       Routing Layer                                   │
│   Route Table, Predicate Engine (Trie + Regex + Composite)          │
│   Route Definition → Route Compilation → Route Matching             │
├──────────────────────────────────────────────────────────────────────┤
│                       Filter Layer                                   │
│   Pre-Filters: Auth, RateLimit, RequestTransform, Logging           │
│   Routing-Filter: LoadBalance, Retry, CircuitBreaker, Proxy         │
│   Post-Filters: ResponseTransform, Metrics, AccessLog               │
├──────────────────────────────────────────────────────────────────────┤
│                       Endpoint Layer                                 │
│   HTTP Reverse Proxy, WebSocket Proxy                               │
│   Connection Pool, Load Balancer, Health Check                      │
├──────────────────────────────────────────────────────────────────────┤
│                       Infrastructure Layer                           │
│   Config (File/Nacos/etcd), Metrics (Micrometer), Tracing (OTel)   │
│   Admin API, Plugin SPI                                              │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. 模块划分

```
x-api-gateway/
├── x-api-gateway-kernel/              # 核心内核 (无第三方依赖, 纯接口+模型)
│   ├── route/                         # Route, RoutePredicate, RouteLocator
│   ├── filter/                        # GatewayFilter, GatewayFilterChain
│   ├── server/                        # ServerWebExchange, HttpRequest, HttpResponse
│   ├── endpoint/                      # Endpoint 接口
│   ├── config/                        # 配置模型 (RouteDefinition, etc.)
│   └── commons/                       # Ordered, Assert, 工具类
│
├── x-api-gateway-server/             # 服务器实现 (当前已有)
│   ├── bootstrap/                     # GatewayServerBootstrap
│   ├── handler/                       # Handler Chain 实现
│   ├── transport/                     # Netty Server 配置、连接管理
│   ├── route/                         # 路由引擎实现 (Trie, Radix Tree)
│   ├── filter/builtin/               # 内置 Filter 实现
│   ├── endpoint/                      # HttpEndpoint, WebSocketEndpoint
│   ├── loadbalance/                   # 负载均衡器
│   ├── healthcheck/                   # 上游健康检查
│   ├── config/                        # 配置加载与热更新
│   └── admin/                         # Admin API
│
├── x-api-gateway-plugin-api/         # 插件 SPI 接口
│   ├── Plugin.java
│   ├── PluginContext.java
│   └── PluginLifecycle.java
│
├── x-api-gateway-plugin-ratelimit/   # 限流插件
├── x-api-gateway-plugin-auth/        # 认证插件 (JWT/OAuth2/API-Key)
├── x-api-gateway-plugin-circuitbreaker/ # 熔断插件
├── x-api-gateway-plugin-observability/  # 可观测性插件 (Metrics/Tracing)
│
├── x-api-gateway-config-nacos/       # Nacos 配置源
├── x-api-gateway-config-etcd/        # etcd 配置源
│
├── x-api-gateway-integration-tests/  # 集成测试
├── x-api-gateway-benchmark/          # 性能基准测试 (JMH)
├── x-api-gateway-dist/               # 打包分发 (fat-jar, docker)
└── docs/                             # 文档
```

### 3.1 模块依赖关系

```
                    ┌───────────────────┐
                    │  x-gateway-kernel │  (纯接口, 零依赖)
                    └────────┬──────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │  server    │  │ plugin-api │  │ config-*   │
     └──────┬─────┘  └──────┬─────┘  └──────┬─────┘
            │               │               │
            ▼               ▼               │
     ┌──────────────────────────┐           │
     │       plugin-*           │◄──────────┘
     │  (ratelimit/auth/cb/obs) │
     └──────────────────────────┘
```

---

## 4. 核心数据模型

### 4.1 ServerWebExchange (请求上下文)

```
┌─────────────────────────────────────────────────────────────────┐
│                      ServerWebExchange                          │
│                                                                 │
│  ┌───────────────────┐    ┌───────────────────┐                │
│  │  GatewayRequest   │    │  GatewayResponse  │                │
│  │  ┌─────────────┐  │    │  ┌─────────────┐  │                │
│  │  │ method      │  │    │  │ statusCode  │  │                │
│  │  │ uri         │  │    │  │ headers     │  │                │
│  │  │ path        │  │    │  │ body (Flux) │  │                │
│  │  │ queryParams │  │    │  └─────────────┘  │                │
│  │  │ headers     │  │    └───────────────────┘                │
│  │  │ cookies     │  │                                         │
│  │  │ body (Flux) │  │    ┌───────────────────┐                │
│  │  │ remoteAddr  │  │    │   Attributes      │                │
│  │  └─────────────┘  │    │   (Map<String,     │                │
│  └───────────────────┘    │    Object>)        │                │
│                           │   - matchedRoute   │                │
│  ┌───────────────────┐    │   - routeStartTime │                │
│  │  MutableRequest   │    │   - alreadyRouted  │                │
│  │  (装饰器, 可修改   │    │   - cachedBody     │                │
│  │   uri/headers/    │    │   - ...            │                │
│  │   body)           │    └───────────────────┘                │
│  └───────────────────┘                                         │
└─────────────────────────────────────────────────────────────────┘
```

```java
public interface ServerWebExchange {

    GatewayRequest getRequest();
    GatewayResponse getResponse();

    Map<String, Object> getAttributes();

    <T> T getAttribute(String name);
    <T> T getRequiredAttribute(String name);

    // 返回一个可修改请求的 Exchange (Decorator)
    ServerWebExchange mutate(Consumer<MutableRequest> mutator);
}

public interface GatewayRequest {
    HttpMethod method();
    URI uri();
    String path();
    HttpHeaders headers();
    Map<String, List<String>> queryParams();
    Map<String, String> cookies();
    InetSocketAddress remoteAddress();
    Flux<ByteBuf> body();
}

public interface GatewayResponse {
    HttpResponseStatus status();
    void setStatus(HttpResponseStatus status);
    HttpHeaders headers();
    Mono<Void> writeWith(Publisher<ByteBuf> body);
    Mono<Void> setComplete();
    boolean isCommitted();
}
```

### 4.2 Route 模型

```java
public interface Route extends Ordered {
    String getId();
    RoutePredicate getPredicate();
    List<GatewayFilter> getFilters();
    Endpoint getEndpoint();
    Map<String, Object> getMetadata();  // 元数据 (超时配置、重试策略等)
}
```

**路由定义 (YAML):**

```yaml
routes:
  - id: user-service
    order: 1
    predicates:
      - name: Path
        config:
          patterns:
            - /api/users/**
      - name: Method
        config:
          methods: [GET, POST]
      - name: Header
        config:
          name: X-Version
          regex: v[0-9]+
    filters:
      - name: StripPrefix
        config:
          parts: 1
      - name: AddHeader
        config:
          headers:
            X-Gateway: x-api-gateway
      - name: RateLimit
        config:
          replenishRate: 100
          burstCapacity: 200
          keyResolver: remoteAddr
      - name: Retry
        config:
          retries: 3
          statuses: [502, 503]
          backoff:
            firstBackoff: 50ms
            maxBackoff: 500ms
            factor: 2
    endpoint:
      name: Http
      config:
        targets:
          - url: http://10.0.1.1:8080
            weight: 80
          - url: http://10.0.1.2:8080
            weight: 20
        connectTimeout: 3000
        readTimeout: 10000
        poolMaxConnections: 256
        poolMaxIdleTime: 60000
    metadata:
      version: v2
      env: production
```

### 4.3 配置层次结构

```
┌─────────────────────────────────────────┐
│         GatewayConfig (顶层)            │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ server:                         │    │
│  │   port: 8080                    │    │
│  │   bossThreads: 1               │    │
│  │   workerThreads: 0 (auto)      │    │
│  │   maxContentLength: 10MB       │    │
│  │   idleTimeout: 60s             │    │
│  │   tls:                         │    │
│  │     enabled: false             │    │
│  │     certPath: ...              │    │
│  │     keyPath: ...               │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ defaultFilters:                 │    │
│  │   - name: AccessLog             │    │
│  │   - name: Metrics               │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ upstream:                       │    │
│  │   connectTimeout: 5000          │    │
│  │   readTimeout: 30000            │    │
│  │   poolMaxConnections: 512       │    │
│  │   poolMaxIdleTime: 120000       │    │
│  │   healthCheck:                  │    │
│  │     enabled: true               │    │
│  │     interval: 10s               │    │
│  │     path: /health               │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ routes: [...]                   │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ admin:                          │    │
│  │   enabled: true                 │    │
│  │   port: 9090                    │    │
│  │   accessToken: ...             │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

---

## 5. 请求处理管线

### 5.1 完整请求生命周期

```
Client Request
    │
    ▼
┌────────────────────────────────────────────────────────────────────┐
│ 1. Netty Channel Pipeline                                          │
│    ├─ SslHandler (TLS 终止)                                       │
│    ├─ HttpServerCodec (HTTP 编解码)                                │
│    ├─ HttpObjectAggregator (请求聚合, 可选)                        │
│    ├─ IdleStateHandler (空闲检测)                                  │
│    └─ ReactorNettyHttpHandler (桥接到 Gateway 管线)                │
│                                                                    │
│ 2. ReactorHttpHandler                                              │
│    └─ 创建 ServerWebExchange, 记录 startTime                      │
│                                                                    │
│ 3. ExceptionHandlingWebHandler                                     │
│    └─ onErrorResume → WebExceptionHandler 链                       │
│                                                                    │
│ 4. MetricsWebHandler (新增)                                        │
│    └─ 记录请求指标 (QPS, 延迟, 状态码分布)                         │
│                                                                    │
│ 5. AccessLogWebHandler (新增)                                      │
│    └─ 异步写 Access Log (不阻塞请求)                               │
│                                                                    │
│ 6. RoutePredicateWebHandler                                        │
│    ├─ 遍历 RouteTable, 执行 Predicate 匹配                        │
│    ├─ 匹配成功: 将 Route 存入 Exchange Attributes                  │
│    └─ 匹配失败: 抛出 ErrorResponseException(404)                   │
│                                                                    │
│ 7. FilteringWebHandler                                             │
│    ├─ 合并 defaultFilters + routeFilters + globalFilters           │
│    ├─ 按 Order 排序                                                │
│    └─ 构建 DefaultGatewayFilterChain 并执行                        │
│                                                                    │
│ 8. Filter Chain 执行 (Pre 阶段)                                   │
│    ├─ AuthFilter          (认证鉴权)                               │
│    ├─ RateLimitFilter     (限流)                                   │
│    ├─ RequestTransformFilter  (请求改写)                           │
│    ├─ StripPrefixFilter   (路径裁剪)                               │
│    ├─ AddHeaderFilter     (添加请求头)                              │
│    └─ ... 用户自定义 pre-filters ...                              │
│                                                                    │
│ 9. RoutingFilter (GlobalFilter, 核心)                              │
│    ├─ 从 Exchange 获取匹配的 Route                                 │
│    ├─ 获取 Endpoint                                                │
│    ├─ LoadBalancer 选择目标实例                                     │
│    ├─ CircuitBreaker 检查                                          │
│    ├─ Endpoint.invoke() → HTTP Client 转发请求                     │
│    ├─ 失败时 Retry 重试                                            │
│    └─ 将上游响应写入 GatewayResponse                               │
│                                                                    │
│ 10. Filter Chain 执行 (Post 阶段, chain.filter() 之后)            │
│    ├─ ResponseTransformFilter  (响应改写)                          │
│    ├─ CorsFilter          (跨域处理)                               │
│    └─ ... 用户自定义 post-filters ...                             │
│                                                                    │
│ 11. Response 写回 Client                                           │
└────────────────────────────────────────────────────────────────────┘
```

### 5.2 Handler Chain 设计 (已有 + 增强)

```java
// Handler Chain 组装顺序 (由 GatewayServerBootstrap 构建)
WebHandler chain = new FilteringWebHandler(globalFilters);          // 最内层
chain = new RoutePredicateWebHandler(chain, routeLocator);         // 路由匹配
chain = new AccessLogWebHandler(chain, accessLogWriter);           // 访问日志
chain = new MetricsWebHandler(chain, meterRegistry);               // 指标采集
chain = new ExceptionHandlingWebHandler(chain, exceptionHandlers); // 异常兜底

ReactorHttpHandler adapter = new ReactorHttpHandler(chain);        // Netty 适配

HttpServer.create()
    .port(port)
    .handle(adapter)
    .bind()
    .block();
```

### 5.3 异常处理策略

```
异常分类:
├── ClientException (4xx)
│   ├── RouteNotFoundException        → 404
│   ├── UnauthorizedException         → 401
│   ├── ForbiddenException            → 403
│   ├── RateLimitExceededException    → 429
│   └── BadRequestException           → 400
│
├── UpstreamException (5xx, 来自上游)
│   ├── ConnectTimeoutException       → 504
│   ├── ReadTimeoutException          → 504
│   └── UpstreamErrorException        → 502
│
└── SystemException (5xx, 网关自身)
    ├── CircuitBreakerOpenException    → 503
    └── InternalException             → 500
```

```java
public interface WebExceptionHandler extends Ordered {
    Mono<Void> handle(ServerWebExchange exchange, Throwable ex);
}

// 默认实现: 按异常类型映射状态码, 写 JSON 错误体
public class DefaultWebExceptionHandler implements WebExceptionHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex); // 响应已提交, 无法处理
        }

        ErrorResponse error = mapToError(ex);

        exchange.getResponse().setStatus(error.status());
        exchange.getResponse().headers().set("Content-Type", "application/json");
        exchange.getResponse().headers().set(GatewayHeaders.ERROR_CODE,
            String.valueOf(error.code()));

        byte[] body = serializer.serialize(error);
        return exchange.getResponse().writeWith(
            Mono.just(Unpooled.wrappedBuffer(body))
        );
    }
}
```

---

## 6. 路由引擎

### 6.1 路由匹配流程

```
请求到达
    │
    ▼
┌──────────────────────┐
│ 1. 快速路径匹配       │    基于 Radix Tree 的精确/前缀匹配
│    (Radix Tree)       │    时间复杂度 O(k), k = path 长度
└──────────┬───────────┘
           │ 命中候选路由列表
           ▼
┌──────────────────────┐
│ 2. Predicate 精确匹配 │    Header / Method / Query / Host 等组合条件
│    (组合谓词)         │    短路求值, AND 优先
└──────────┬───────────┘
           │ 匹配成功的路由 (可能多个)
           ▼
┌──────────────────────┐
│ 3. 优先级排序         │    按 Route.order 排序, 取第一个
│    (Order)            │
└──────────┬───────────┘
           │
           ▼
      匹配的 Route
```

### 6.2 Radix Tree 路由表 (性能关键)

相比当前的线性遍历 + 正则匹配, Radix Tree 提供 **O(k)** 的路由查找性能:

```
                       (root)
                      /      \
                   /api       /static
                  /    \          \
              /users  /orders     /*
              /   \       \
           /:id   /list   /:id
```

```java
public class RadixTreeRouteTable {
    private final RadixTreeNode root = new RadixTreeNode();

    // 插入路由
    public void addRoute(Route route, String pathPattern) {
        // 将 /api/users/:id 拆分为 segments
        // 支持: 精确匹配 (/api), 参数匹配 (/:id), 通配符 (/**)
        String[] segments = splitPath(pathPattern);
        root.insert(segments, route);
    }

    // 查找匹配路由
    public List<Route> match(String requestPath) {
        String[] segments = splitPath(requestPath);
        List<Route> candidates = new ArrayList<>();
        root.search(segments, 0, candidates);
        return candidates;
    }
}

class RadixTreeNode {
    String segment;                          // 路径段
    Map<String, RadixTreeNode> children;     // 精确匹配子节点
    RadixTreeNode paramChild;                // :param 参数节点
    RadixTreeNode wildcardChild;             // ** 通配符节点
    List<Route> routes;                      // 此节点上挂载的路由
}
```

### 6.3 内置 Predicate 工厂

| Predicate | 描述 | 配置示例 |
|-----------|------|----------|
| **Path** | 路径匹配 (Ant-style + 正则) | `patterns: [/api/v1/**]` |
| **Method** | HTTP 方法匹配 | `methods: [GET, POST]` |
| **Header** | 请求头匹配 (支持正则) | `name: X-Token, regex: .+` |
| **Query** | 查询参数匹配 | `param: version, regex: v[0-9]+` |
| **Host** | 域名匹配 (支持通配符) | `patterns: [**.example.com]` |
| **RemoteAddr** | 客户端 IP 匹配 (CIDR) | `sources: [192.168.0.0/16]` |
| **Cookie** | Cookie 匹配 | `name: session, regex: .+` |
| **Weight** | 权重路由 (灰度发布) | `group: service-a, weight: 80` |
| **Time** | 时间窗口 (定时上线) | `after: 2024-01-01T00:00+08:00` |

```java
public interface RoutePredicateFactory<C> extends Configurable<C> {
    String getName();
    RoutePredicate<ServerWebExchange> apply(C config);
}

// 谓词组合
public interface RoutePredicate<T> {
    boolean test(T t);                    // 改为同步, 路由匹配不应有异步操作

    default RoutePredicate<T> and(RoutePredicate<T> other) {
        return t -> this.test(t) && other.test(t);   // 短路求值
    }

    default RoutePredicate<T> or(RoutePredicate<T> other) {
        return t -> this.test(t) || other.test(t);
    }

    default RoutePredicate<T> negate() {
        return t -> !this.test(t);
    }
}
```

> **设计决策**: 将 `RoutePredicate.test()` 从 `Mono<Boolean>` 改为 `boolean`。  
> **原因**: 路由匹配是纯 CPU 操作 (字符串比较/正则匹配), 引入 Mono 增加了不必要的开销和复杂度。  
> **参考**: Spring Cloud Gateway 的 `Predicate<ServerWebExchange>` 也是同步接口; Nginx 的路由匹配也是同步操作。

### 6.4 路由热更新

```
                 Config Source (File / Nacos / etcd)
                          │
                          │ Watch / Poll
                          ▼
                 ┌─────────────────┐
                 │ RouteDefinition │
                 │   Locator       │
                 └────────┬────────┘
                          │ Flux<RouteDefinition>
                          ▼
                 ┌─────────────────┐
                 │ RouteDefinition │    编译: Definition → Route
                 │  RouteLocator   │    验证: 工厂存在性, 配置合法性
                 └────────┬────────┘
                          │ Flux<Route>
                          ▼
                 ┌─────────────────┐
                 │ CacheRouteLocator│   双缓冲: 新旧路由表原子切换
                 │ (Atomic Swap)   │   无锁读: volatile reference
                 └────────┬────────┘
                          │
                          ▼
                   RoutePredicateWebHandler 使用新路由表
```

```java
public class CacheRouteLocator implements RouteLocator {
    private final RouteLocator delegate;
    private volatile List<Route> cachedRoutes;       // 无锁读
    private final RadixTreeRouteTable routeTable;    // 路由索引

    public void refresh() {
        // 全量重建: 编译新路由 → 构建新 RadixTree → 原子替换
        List<Route> newRoutes = delegate.getRoutes().collectList().block();
        RadixTreeRouteTable newTable = buildRouteTable(newRoutes);

        // 原子替换引用, 旧表由 GC 回收, 读端无锁无阻塞
        this.routeTable = newTable;
        this.cachedRoutes = newRoutes;
    }
}
```

---

## 7. Filter 体系

### 7.1 Filter 接口设计

```java
public interface GatewayFilter extends Ordered {
    Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain);
}

// 全局 Filter (应用于所有路由)
public interface GlobalGatewayFilter extends GatewayFilter {
}

// Filter 工厂 (从配置创建 Filter 实例)
public interface GatewayFilterFactory<C> extends Configurable<C> {
    String getName();
    GatewayFilter apply(C config);
}
```

### 7.2 Filter Chain 执行模型

```
Pre 阶段 (请求方向 →)
─────────────────────────────────────────────────►

  Filter-1      Filter-2      Filter-3      RoutingFilter
  ┌──────┐      ┌──────┐      ┌──────┐      ┌──────────┐
  │ pre  │─────►│ pre  │─────►│ pre  │─────►│ endpoint │
  │ logic│      │ logic│      │ logic│      │ .invoke()│
  │      │◄─────│      │◄─────│      │◄─────│          │
  │ post │      │ post │      │ post │      │          │
  │ logic│      │ logic│      │ logic│      │          │
  └──────┘      └──────┘      └──────┘      └──────────┘

◄─────────────────────────────────────────────────
Post 阶段 (响应方向 ←)
```

```java
// Filter 编写模板
public class ExampleGatewayFilter implements GatewayFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // === Pre 阶段: chain.filter() 之前 ===
        exchange.getRequest().headers().set("X-Request-Id", generateId());

        return chain.filter(exchange)
            .then(Mono.fromRunnable(() -> {
                // === Post 阶段: chain.filter() 之后 ===
                exchange.getResponse().headers().set("X-Response-Time",
                    calculateDuration(exchange));
            }));
    }
}
```

### 7.3 Filter 排序与执行顺序

```
Order 值:   -1000    -500     -100      0       100      500     1000
             │        │        │        │        │        │        │
             ▼        ▼        ▼        ▼        ▼        ▼        ▼
Pre:     Metrics → AccessLog → Auth → RateLimit → Transform → AddHeader → Routing
Post:    Routing → AddHeader → Transform → RateLimit → Auth → AccessLog → Metrics
```

| Order 范围 | 阶段 | 典型 Filter |
|-----------|------|-------------|
| -1000 ~ -500 | 最早 Pre / 最晚 Post | MetricsFilter, AccessLogFilter |
| -500 ~ -100 | 安全 | AuthFilter, CorsFilter, IPWhitelistFilter |
| -100 ~ 0 | 限流/熔断 | RateLimitFilter, CircuitBreakerFilter |
| 0 ~ 100 | 请求改写 | StripPrefixFilter, RewritePathFilter, AddHeaderFilter |
| 100 ~ 500 | 路由转发 | RoutingFilter (默认 order = 200) |
| 500 ~ 1000 | 响应改写 | ResponseTransformFilter |

### 7.4 内置 Filter 清单

#### Pre-Filters

| Filter | 功能 | Order |
|--------|------|-------|
| **RequestId** | 生成/传递 X-Request-Id | -900 |
| **AccessLog** | 记录访问日志 | -800 |
| **Cors** | CORS 跨域处理 | -600 |
| **Auth** (plugin) | JWT / OAuth2 / API-Key 认证 | -500 |
| **IPWhitelist** | IP 白名单/黑名单 | -400 |
| **RateLimit** (plugin) | 限流 (令牌桶/滑动窗口) | -200 |
| **StripPrefix** | 路径前缀裁剪 | -50 |
| **RewritePath** | 路径重写 (正则替换) | -40 |
| **AddHeader** | 添加/覆盖请求头 | -30 |
| **RemoveHeader** | 移除请求头 | -20 |
| **RequestSize** | 请求体大小限制 | -10 |

#### Routing Filter (Global)

| Filter | 功能 | Order |
|--------|------|-------|
| **RoutingFilter** | 调用 Endpoint 转发请求, 含 LB + Retry | 200 |

#### Post-Filters

| Filter | 功能 | Order |
|--------|------|-------|
| **AddResponseHeader** | 添加响应头 | 500 |
| **RemoveResponseHeader** | 移除响应头 (如内部头) | 510 |
| **ResponseDecorator** | 响应体改写 | 520 |

### 7.5 DefaultFilters 合并策略

```java
// FilteringWebHandler 中的 Filter 合并逻辑
public class FilteringWebHandler implements WebHandler {
    private final List<GlobalGatewayFilter> globalFilters;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange) {
        Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);

        List<GatewayFilter> combined = new ArrayList<>();
        combined.addAll(this.defaultFilters);       // 1. 全局默认 Filter
        combined.addAll(route.getFilters());         // 2. 路由级别 Filter
        combined.addAll(this.globalFilters);          // 3. GlobalFilter

        OrderedAwareComparator.sort(combined);

        return new DefaultGatewayFilterChain(combined).filter(exchange);
    }
}
```

---

## 8. 负载均衡与服务发现

### 8.1 负载均衡架构

```
┌─────────────────────────────────────────────────┐
│                 LoadBalancer                      │
│                                                  │
│  ┌──────────────────┐    ┌───────────────────┐  │
│  │  Server List     │    │  LB Strategy      │  │
│  │  ┌────────────┐  │    │  ┌─────────────┐  │  │
│  │  │ 10.0.1.1   │  │    │  │ Round Robin │  │  │
│  │  │ weight: 80 │  │    │  │ Weighted RR │  │  │
│  │  │ healthy: ✓ │  │    │  │ Random      │  │  │
│  │  ├────────────┤  │    │  │ LeastConn   │  │  │
│  │  │ 10.0.1.2   │  │    │  │ ConsistHash │  │  │
│  │  │ weight: 20 │  │    │  └─────────────┘  │  │
│  │  │ healthy: ✓ │  │    └───────────────────┘  │
│  │  ├────────────┤  │                            │
│  │  │ 10.0.1.3   │  │    ┌───────────────────┐  │
│  │  │ weight: 0  │  │    │  Health Checker   │  │
│  │  │ healthy: ✗ │  │    │  (Active/Passive) │  │
│  │  └────────────┘  │    └───────────────────┘  │
│  └──────────────────┘                            │
└─────────────────────────────────────────────────┘
```

### 8.2 负载均衡策略

```java
public interface LoadBalancerStrategy {
    UpstreamTarget choose(List<UpstreamTarget> targets, ServerWebExchange exchange);
}

public record UpstreamTarget(
    String url,
    int weight,
    volatile boolean healthy,
    AtomicInteger activeConnections,     // for LeastConn
    AtomicLong totalRequests             // for stats
) {}
```

**策略实现:**

| 策略 | 适用场景 | 算法 |
|------|---------|------|
| **WeightedRoundRobin** | 通用 (默认) | 平滑加权轮询 (Nginx 算法) |
| **Random** | 无状态服务 | 加权随机 |
| **LeastConnections** | 长连接/处理时间差异大 | 最小活跃连接 |
| **ConsistentHash** | 需要会话亲和 | Ketama 一致性哈希 |
| **IPHash** | 简单会话亲和 | 客户端 IP 哈希 |

**平滑加权轮询 (Smooth Weighted Round-Robin):**

```java
public class WeightedRoundRobinStrategy implements LoadBalancerStrategy {

    @Override
    public UpstreamTarget choose(List<UpstreamTarget> targets,
                                  ServerWebExchange exchange) {
        // Nginx 平滑加权轮询算法
        // 每个节点维护 currentWeight, 每次选择:
        //   1. currentWeight += effectiveWeight
        //   2. 选 currentWeight 最大的节点
        //   3. 被选节点 currentWeight -= totalWeight
        // 保证权重分配均匀且平滑
    }
}
```

### 8.3 健康检查

```java
public class HealthChecker {

    // 主动健康检查: 定时探测上游
    // - HTTP GET /health, 期望 2xx
    // - 连续 N 次失败 → 标记 unhealthy
    // - 连续 M 次成功 → 恢复 healthy
    private final ScheduledExecutorService scheduler;

    // 被动健康检查: 根据转发结果判断
    // - 连续 N 次 5xx 或 connect timeout → 标记 unhealthy
    // - 进入半开状态后, 放行探测流量
    public void recordResult(UpstreamTarget target, boolean success) { ... }
}
```

**健康检查配置:**

```yaml
upstream:
  healthCheck:
    enabled: true
    type: active           # active / passive / both
    interval: 10s          # 主动检查间隔
    timeout: 3s            # 探测超时
    path: /health          # 探测路径
    unhealthyThreshold: 3  # 连续失败 N 次标记不健康
    healthyThreshold: 2    # 连续成功 N 次恢复
```

### 8.4 服务发现 (可选扩展)

```java
// SPI 接口, 支持多种注册中心
public interface ServiceDiscovery {
    List<ServiceInstance> getInstances(String serviceId);
    void subscribe(String serviceId, Consumer<List<ServiceInstance>> listener);
}

// 内置实现:
// - StaticServiceDiscovery    (配置文件, 默认)
// - NacosServiceDiscovery     (x-api-gateway-config-nacos 模块)
// - EtcdServiceDiscovery      (x-api-gateway-config-etcd 模块)
// - ConsulServiceDiscovery    (扩展)
```

---

## 9. 高性能设计

### 9.1 Netty 线程模型

```
┌──────────────────────────────────────────────────────────────────┐
│                     Netty Thread Model                           │
│                                                                  │
│  BossGroup (1 thread)                                           │
│  └─ Accept 新连接, 分配给 WorkerGroup                           │
│                                                                  │
│  WorkerGroup (CPU cores × 2 threads)                            │
│  ├─ Thread-1: Channel[1,5,9,...]    ← I/O 读写 + 编解码       │
│  ├─ Thread-2: Channel[2,6,10,...]   ← 同一连接始终在同一线程   │
│  ├─ Thread-3: Channel[3,7,11,...]   ← 避免线程切换开销         │
│  └─ Thread-4: Channel[4,8,12,...]                              │
│                                                                  │
│  注: 使用 Epoll (Linux) / KQueue (macOS) 替代 NIO              │
│      减少 JNI 调用开销, 支持 Edge-Triggered 模式               │
└──────────────────────────────────────────────────────────────────┘
```

```java
// Netty Server 配置
public class TransportConfig {
    private int bossThreads = 1;
    private int workerThreads = 0;   // 0 = Runtime.availableProcessors() * 2
    private int maxContentLength = 10 * 1024 * 1024;  // 10MB
    private int backlog = 1024;
    private boolean tcpNoDelay = true;
    private boolean soKeepAlive = true;
    private int soRcvBuf = 64 * 1024;
    private int soSndBuf = 64 * 1024;

    // 使用原生传输
    private boolean preferNative = true;  // Epoll (Linux) / KQueue (macOS)
}
```

### 9.2 连接池管理 (上游)

```
┌────────────────────────────────────────────────────────────────────┐
│                   Connection Pool (per upstream host)              │
│                                                                    │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐                  │
│  │ Connection │  │ Connection │  │ Connection │  ...             │
│  │  idle ✓    │  │  active    │  │  idle ✓    │                  │
│  └────────────┘  └────────────┘  └────────────┘                  │
│                                                                    │
│  参数:                                                             │
│  ├─ maxConnections: 256          (每上游最大连接数)               │
│  ├─ maxIdleTime: 60s             (空闲连接超时回收)               │
│  ├─ maxLifeTime: 300s            (连接最大生命周期)               │
│  ├─ pendingAcquireTimeout: 5s    (等待获取连接超时)               │
│  ├─ pendingAcquireMaxCount: 1000 (等待队列最大长度)               │
│  └─ evictionInterval: 30s        (后台清理间隔)                   │
│                                                                    │
│  策略:                                                             │
│  ├─ FIFO (默认): 均匀使用连接, 适合连接数多的场景                │
│  └─ LIFO: 复用热连接, 适合连接数少的场景                         │
└────────────────────────────────────────────────────────────────────┘
```

```java
// 使用 Reactor Netty 内置连接池
HttpClient httpClient = HttpClient.create(
    ConnectionProvider.builder("upstream")
        .maxConnections(256)
        .maxIdleTime(Duration.ofSeconds(60))
        .maxLifeTime(Duration.ofSeconds(300))
        .pendingAcquireTimeout(Duration.ofSeconds(5))
        .pendingAcquireMaxCount(1000)
        .evictInBackground(Duration.ofSeconds(30))
        .fifo()    // FIFO 策略
        .metrics(true)  // 连接池指标
        .build()
);
```

### 9.3 零拷贝与内存优化

```
传统方式:
  Netty ByteBuf → byte[] → 新 ByteBuf → 写出
  (两次内存拷贝)

零拷贝方式 (X-API-Gateway):
  Netty ByteBuf → 直接转发 ByteBuf → 写出
  (零拷贝, ByteBuf 引用传递)
```

**关键优化点:**

| 优化项 | 说明 | 预期收益 |
|--------|------|----------|
| **ByteBuf 直通** | 请求/响应 body 不做 byte[] 拷贝, 直接传递 ByteBuf 引用 | 减少 50% 内存分配 |
| **PooledAllocator** | 使用 Netty PooledByteBufAllocator | 减少 GC 压力 |
| **CompositeByteBuf** | 多段 header 拼接使用 Composite, 避免合并拷贝 | 减少拷贝 |
| **Streaming Proxy** | body 以 Flux<ByteBuf> 流式转发, 不缓存全量 | 内存恒定, 支持大文件 |
| **Header 复用** | 固定 header (Server, Via) 预分配为 AsciiString 常量 | 减少对象创建 |

```java
// 流式代理 — body 不落地, 直接 pipe
public class HttpEndpoint implements Endpoint {
    @Override
    public Mono<Void> invoke(ServerWebExchange exchange) {
        GatewayRequest request = exchange.getRequest();

        return httpClient
            .headers(h -> copyHeaders(request.headers(), h))
            .request(request.method())
            .uri(targetUri)
            .send(request.body())             // Flux<ByteBuf> 零拷贝流式发送
            .response((resp, body) -> {
                exchange.getResponse().setStatus(resp.status());
                copyHeaders(resp.responseHeaders(), exchange.getResponse().headers());
                return exchange.getResponse().writeWith(body.retain());  // 零拷贝
            });
    }
}
```

### 9.4 性能关键路径优化

```java
// 1. 路由匹配: Radix Tree O(k) 替代线性扫描 O(n)
// 2. Filter 排序: 启动时预排序, 运行时不再排序
// 3. Predicate: 同步执行, 不包装为 Mono
// 4. 对象池: ServerWebExchange 可池化复用 (高级优化)
// 5. 字符串: 路径比较使用 AsciiString, 避免 UTF-8 编解码

// 路由表预编译: 启动/更新时一次性完成
public class CompiledRoute {
    final String id;
    final RoutePredicate predicate;     // 已编译的组合谓词
    final GatewayFilter[] filters;      // 已排序的 filter 数组 (避免 List 迭代器)
    final Endpoint endpoint;
    final int order;
}
```

### 9.5 性能对比参考

```
测试环境: 8核16G, 1KB 响应体, 100 并发连接, wrk 压测

             QPS        P50       P99      内存
Nginx        80,000     0.5ms     2ms      50MB
Kong         35,000     1.2ms     5ms      200MB
SCG          25,000     1.5ms     8ms      300MB
X-Gateway    55,000+    0.8ms     3ms      150MB   (目标)
(目标)
```

---

## 10. 高可用设计

### 10.1 无状态部署

```
                    ┌─────────────┐
                    │   L4/L7 LB  │   (Nginx / LVS / 云 SLB)
                    │   VIP       │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Gateway  │ │ Gateway  │ │ Gateway  │
        │ Node-1   │ │ Node-2   │ │ Node-3   │
        └──────┬───┘ └──────┬───┘ └──────┬───┘
               │            │            │
               ▼            ▼            ▼
        ┌──────────────────────────────────┐
        │      Upstream Services           │
        └──────────────────────────────────┘

说明:
- 每个 Gateway 节点完全无状态, 可水平扩展
- 限流状态通过 Redis 共享 (分布式限流场景)
- 配置通过配置中心统一下发
- 健康检查由 L4 LB 或 K8s 探针完成
```

### 10.2 优雅关闭 (Graceful Shutdown)

```
收到 SIGTERM
    │
    ▼
┌──────────────────────┐
│ 1. 停止接受新连接     │   从 LB 摘除 (健康检查返回 503)
│    关闭 Server Socket │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 2. 等待在途请求完成   │   超时时间: 30s (可配置)
│    Drain existing     │   到期后强制关闭剩余连接
│    connections        │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 3. 关闭上游连接池     │   等待所有 pending 请求完成
│    Close upstream     │
│    connections        │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 4. 刷新 Metrics/Logs │   确保最后的数据不丢失
│    Flush buffers      │
└──────────┬───────────┘
           │
           ▼
       进程退出
```

```java
public class GracefulShutdown {
    private final Duration timeout;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private volatile boolean shuttingDown = false;

    public void shutdown() {
        shuttingDown = true;

        // 1. 标记为下线 (健康检查返回 503)
        healthEndpoint.markUnhealthy();

        // 2. 停止接受新连接
        serverChannel.close();

        // 3. 等待在途请求完成
        if (!awaitQuiescence(timeout)) {
            log.warn("Force shutdown with {} active requests", activeRequests.get());
        }

        // 4. 关闭连接池
        connectionProvider.dispose();

        // 5. 刷新缓冲区
        metricsReporter.flush();
        accessLogWriter.flush();
    }
}
```

### 10.3 配置热加载

```
┌────────────────────────────────────────────────────────────────┐
│                  配置热加载流程                                 │
│                                                                │
│  Config Source ──► Watcher ──► Validator ──► Atomic Swap       │
│                                                                │
│  1. 文件变更 (WatchService / inotify)                         │
│     或 配置中心推送 (Nacos/etcd listener)                     │
│                                                                │
│  2. 解析新配置 → RouteDefinition 列表                         │
│                                                                │
│  3. 验证:                                                     │
│     - 语法正确性 (YAML 解析)                                  │
│     - 语义正确性 (工厂存在性, 参数合法性)                     │
│     - 差异计算 (新增/删除/修改了哪些路由)                     │
│                                                                │
│  4. 编译新路由表                                               │
│                                                                │
│  5. 原子替换 (volatile / AtomicReference)                     │
│     - 旧路由表继续服务在途请求                                │
│     - 新请求使用新路由表                                       │
│                                                                │
│  6. 发布事件: RouteRefreshEvent (供 Metrics 等监听)           │
│                                                                │
│  回滚: 验证失败时不替换, 打印错误日志, 保持旧配置运行       │
└────────────────────────────────────────────────────────────────┘
```

### 10.4 故障隔离

```java
// 上游故障不影响其他路由
// 每个 Route/Upstream 独立的:
// - 连接池 (避免慢上游耗尽全局连接)
// - 超时配置
// - 熔断器状态
// - 重试策略

public class RouteIsolation {
    // 连接池隔离: 按上游 host:port 分组
    Map<String, ConnectionProvider> poolPerUpstream;

    // 熔断器隔离: 按 route id 分组
    Map<String, CircuitBreaker> breakerPerRoute;

    // 限流器隔离: 按 route id 或 key 分组
    Map<String, RateLimiter> limiterPerRoute;
}
```

---

## 11. 配置管理

### 11.1 配置源架构

```java
public interface ConfigSource {
    // 获取当前配置
    GatewayConfig load();

    // 监听配置变更
    void watch(Consumer<GatewayConfig> listener);

    // 配置源优先级
    int order();
}

// 多配置源合并: 低 order 优先级高
// 合并策略: routes 取并集, server 配置取最高优先级
```

```
┌──────────────────────────────────────────────┐
│              Config Sources                   │
│                                              │
│  Priority 1: CLI args / Env vars             │
│  Priority 2: 配置中心 (Nacos/etcd)           │
│  Priority 3: 外部配置文件 (-c config.yml)    │
│  Priority 4: 内置默认配置                     │
│                                              │
│  合并策略:                                   │
│  - server 配置: 高优先级覆盖低优先级         │
│  - routes: 按 route id 合并, 同 id 高优先级  │
│  - defaultFilters: 取并集                    │
└──────────────────────────────────────────────┘
```

### 11.2 完整配置示例

```yaml
# gateway-config.yml

server:
  port: 8080
  bossThreads: 1
  workerThreads: 0                    # 0 = auto (CPU × 2)
  maxContentLength: 10485760          # 10MB
  idleTimeout: 60s
  shutdownTimeout: 30s
  tls:
    enabled: false
    certPath: /etc/gateway/cert.pem
    keyPath: /etc/gateway/key.pem
    protocols: [TLSv1.2, TLSv1.3]

upstream:
  connectTimeout: 3000
  readTimeout: 10000
  writeTimeout: 10000
  pool:
    maxConnections: 512
    maxIdleTime: 60s
    maxLifeTime: 300s
  healthCheck:
    enabled: true
    interval: 10s
    path: /health
    unhealthyThreshold: 3
    healthyThreshold: 2

admin:
  enabled: true
  port: 9090
  accessToken: ${GATEWAY_ADMIN_TOKEN}

observability:
  metrics:
    enabled: true
    exporterType: prometheus          # prometheus / otlp
    prefix: x_gateway
  tracing:
    enabled: false
    exporterType: otlp
    endpoint: http://localhost:4317
    sampleRate: 0.1
  accessLog:
    enabled: true
    format: json                      # json / combined
    output: stdout                    # stdout / file
    filePath: /var/log/gateway/access.log

defaultFilters:
  - name: RequestId
  - name: Cors
    config:
      allowedOrigins: ["*"]
      allowedMethods: [GET, POST, PUT, DELETE]
      allowedHeaders: ["*"]
      maxAge: 3600

routes:
  - id: user-service
    order: 1
    predicates:
      - name: Path
        config:
          patterns: ["/api/users/**"]
      - name: Method
        config:
          methods: [GET, POST, PUT, DELETE]
    filters:
      - name: StripPrefix
        config:
          parts: 1
      - name: RateLimit
        config:
          replenishRate: 100
          burstCapacity: 200
          keyResolver: remoteAddr
      - name: Retry
        config:
          retries: 3
          statuses: [502, 503]
          methods: [GET]
          backoff:
            firstBackoff: 50ms
            maxBackoff: 500ms
            factor: 2
    endpoint:
      name: Http
      config:
        targets:
          - url: http://10.0.1.1:8080
            weight: 80
          - url: http://10.0.1.2:8080
            weight: 20
        loadBalancer: WeightedRoundRobin

  - id: order-service
    order: 2
    predicates:
      - name: Path
        config:
          patterns: ["/api/orders/**"]
      - name: Header
        config:
          name: X-Api-Version
          regex: "v[2-9]"
    filters:
      - name: StripPrefix
        config:
          parts: 1
      - name: Auth
        config:
          type: jwt
          secretKey: ${JWT_SECRET}
      - name: CircuitBreaker
        config:
          failureRateThreshold: 50
          waitDurationInOpenState: 30s
          slidingWindowSize: 100
    endpoint:
      name: Http
      config:
        targets:
          - url: http://order-service:8080
        connectTimeout: 5000
        readTimeout: 15000
```

---

## 12. 安全体系

### 12.1 安全架构

```
┌────────────────────────────────────────────────────────────────────┐
│                       安全层次                                     │
│                                                                    │
│  Layer 1: Transport Security                                      │
│  ├─ TLS 1.2/1.3 终止                                             │
│  ├─ 证书管理 (支持热更新)                                         │
│  └─ HTTP/2 (ALPN 协商)                                           │
│                                                                    │
│  Layer 2: Network Security                                        │
│  ├─ IP 黑白名单 (CIDR 匹配)                                      │
│  ├─ 连接数限制 (per IP)                                           │
│  └─ 请求体大小限制                                                │
│                                                                    │
│  Layer 3: Application Security                                    │
│  ├─ 认证: JWT / OAuth2 / API-Key / Basic Auth / HMAC             │
│  ├─ 鉴权: RBAC / ABAC (可选, 通过插件)                          │
│  ├─ CORS 跨域控制                                                 │
│  ├─ CSRF 防护 (可选)                                              │
│  └─ 请求头安全 (移除内部头, 防止 header 注入)                    │
│                                                                    │
│  Layer 4: Rate Limiting & Abuse Prevention                        │
│  ├─ 限流 (令牌桶 / 滑动窗口)                                     │
│  ├─ 熔断 (Circuit Breaker)                                       │
│  └─ 并发控制 (Bulkhead)                                          │
│                                                                    │
│  Layer 5: Audit & Observability                                   │
│  ├─ 访问日志 (完整请求/响应元数据)                                │
│  ├─ 安全事件告警                                                  │
│  └─ 审计追踪                                                      │
└────────────────────────────────────────────────────────────────────┘
```

### 12.2 认证插件

```java
public interface AuthProvider {
    // 验证请求, 返回认证主体 (用户信息)
    Mono<AuthPrincipal> authenticate(ServerWebExchange exchange);
}

// JWT 认证
public class JwtAuthProvider implements AuthProvider {
    @Override
    public Mono<AuthPrincipal> authenticate(ServerWebExchange exchange) {
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return Mono.error(new UnauthorizedException("Missing token"));
        }

        try {
            Claims claims = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token)
                .getBody();

            return Mono.just(new AuthPrincipal(
                claims.getSubject(),
                claims.get("roles", List.class)
            ));
        } catch (JwtException e) {
            return Mono.error(new UnauthorizedException("Invalid token"));
        }
    }
}

// API-Key 认证
public class ApiKeyAuthProvider implements AuthProvider { ... }

// OAuth2 认证 (Introspection / JWKS)
public class OAuth2AuthProvider implements AuthProvider { ... }
```

### 12.3 Header 安全

```java
public class SecureHeadersFilter implements GatewayFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Pre: 清理可能被伪造的内部 header
        exchange.getRequest().headers().remove("X-Internal-Token");
        exchange.getRequest().headers().remove("X-Real-IP"); // 由网关重新设置

        // 设置真实客户端 IP
        exchange.getRequest().headers().set("X-Real-IP",
            resolveClientIP(exchange));
        exchange.getRequest().headers().set("X-Forwarded-For",
            buildXForwardedFor(exchange));

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // Post: 添加安全响应头
            HttpHeaders respHeaders = exchange.getResponse().headers();
            respHeaders.set("X-Content-Type-Options", "nosniff");
            respHeaders.set("X-Frame-Options", "DENY");
            respHeaders.set("X-XSS-Protection", "1; mode=block");
            // 移除可能暴露内部信息的 header
            respHeaders.remove("Server");
            respHeaders.remove("X-Powered-By");
        }));
    }
}
```

---

## 13. 可观测性

### 13.1 三大支柱

```
┌────────────────────────────────────────────────────────────────────┐
│                     Observability                                  │
│                                                                    │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐  │
│  │   Metrics    │   │   Tracing    │   │    Logging           │  │
│  │              │   │              │   │                      │  │
│  │ • QPS        │   │ • Request    │   │ • Access Log         │  │
│  │ • Latency    │   │   span      │   │   (每请求一行)       │  │
│  │   (P50/99)   │   │ • Route     │   │ • Error Log          │  │
│  │ • Status     │   │   match     │   │   (异常详情)         │  │
│  │   codes      │   │ • Filter    │   │ • Gateway Log        │  │
│  │ • Connection │   │   execution │   │   (运行状态)         │  │
│  │   pool       │   │ • Upstream  │   │                      │  │
│  │ • Upstream   │   │   call      │   │ 格式: JSON           │  │
│  │   health     │   │ • Error     │   │ 输出: stdout / file  │  │
│  │              │   │   details   │   │ 采样: 可配置         │  │
│  │ Exporter:    │   │             │   │                      │  │
│  │ Prometheus   │   │ Exporter:   │   │                      │  │
│  │ / OTLP       │   │ OTLP/Jaeger│   │                      │  │
│  └──────────────┘   └──────────────┘   └──────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
```

### 13.2 核心 Metrics

```java
public class GatewayMetrics {
    // ---- 请求维度 ----

    // 请求总数 (按 route, status_code, method 分组)
    Counter requestsTotal;
    // 标签: route_id, status_code, method, outcome (SUCCESS/CLIENT_ERROR/SERVER_ERROR)

    // 请求延迟分布 (按 route 分组)
    DistributionSummary requestDuration;
    // 标签: route_id, status_code

    // 活跃请求数
    Gauge activeRequests;
    // 标签: route_id

    // ---- 上游维度 ----

    // 上游请求延迟
    DistributionSummary upstreamDuration;
    // 标签: upstream_host, status_code

    // 上游连接池使用情况
    Gauge connectionPoolActive;
    Gauge connectionPoolIdle;
    Gauge connectionPoolPending;
    // 标签: upstream_host

    // 上游健康状态
    Gauge upstreamHealthy;
    // 标签: upstream_host, route_id

    // ---- 限流/熔断维度 ----

    Counter rateLimitedRequests;
    // 标签: route_id, key

    Gauge circuitBreakerState;
    // 标签: route_id, state (CLOSED/OPEN/HALF_OPEN)

    // ---- 系统维度 ----

    Gauge jvmMemoryUsed;
    Gauge jvmGcCount;
    Gauge eventLoopPending;
}
```

**Prometheus 输出示例:**

```prometheus
# HELP x_gateway_requests_total Total number of requests
# TYPE x_gateway_requests_total counter
x_gateway_requests_total{route_id="user-service",status_code="200",method="GET"} 125430
x_gateway_requests_total{route_id="user-service",status_code="429",method="GET"} 230
x_gateway_requests_total{route_id="order-service",status_code="200",method="POST"} 53201

# HELP x_gateway_request_duration_seconds Request duration in seconds
# TYPE x_gateway_request_duration_seconds histogram
x_gateway_request_duration_seconds_bucket{route_id="user-service",le="0.001"} 95000
x_gateway_request_duration_seconds_bucket{route_id="user-service",le="0.005"} 120000
x_gateway_request_duration_seconds_bucket{route_id="user-service",le="0.01"} 124500
x_gateway_request_duration_seconds_bucket{route_id="user-service",le="+Inf"} 125430

# HELP x_gateway_upstream_connections_active Active upstream connections
# TYPE x_gateway_upstream_connections_active gauge
x_gateway_upstream_connections_active{upstream_host="10.0.1.1:8080"} 45
x_gateway_upstream_connections_idle{upstream_host="10.0.1.1:8080"} 211
```

### 13.3 Access Log

```java
public class AccessLogEntry {
    String requestId;
    Instant timestamp;
    String clientIp;
    String method;
    String path;
    String queryString;
    String protocol;
    int statusCode;
    long requestSize;
    long responseSize;
    long durationMs;
    String routeId;
    String upstreamHost;
    long upstreamDurationMs;
    String userAgent;
    String referer;
    Map<String, String> customFields;
}
```

**JSON 格式输出:**

```json
{
  "requestId": "a1b2c3d4-e5f6-7890",
  "timestamp": "2024-01-15T10:30:45.123Z",
  "clientIp": "192.168.1.100",
  "method": "GET",
  "path": "/api/users/123",
  "protocol": "HTTP/1.1",
  "statusCode": 200,
  "requestSize": 0,
  "responseSize": 1234,
  "durationMs": 15,
  "routeId": "user-service",
  "upstreamHost": "10.0.1.1:8080",
  "upstreamDurationMs": 12,
  "userAgent": "Mozilla/5.0 ..."
}
```

### 13.4 分布式追踪

```
Client ─── Gateway ─── Upstream Service ─── Database
  │           │              │                  │
  ├─ Span A ──┤              │                  │
  │           ├── Span B ────┤                  │
  │           │   (gateway)  ├── Span C ────────┤
  │           │              │   (service)      │
  │           │              │                  │
  
  Span B 包含:
  - route_id
  - upstream_host
  - http.method, http.url
  - http.status_code
  - gateway.filter_chain_duration
  - error (if any)
```

```java
// 追踪集成: 兼容 OpenTelemetry
public class TracingFilter implements GlobalGatewayFilter {
    private final Tracer tracer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 从请求头提取 trace context (W3C TraceContext / B3)
        Context extractedContext = propagator.extract(
            Context.current(), exchange.getRequest().headers(), headerGetter
        );

        Span span = tracer.spanBuilder("gateway.request")
            .setParent(extractedContext)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("http.method", exchange.getRequest().method().name())
            .setAttribute("http.url", exchange.getRequest().uri().toString())
            .startSpan();

        // 注入 trace context 到上游请求
        return chain.filter(exchange)
            .doOnSuccess(v -> {
                span.setAttribute("http.status_code",
                    exchange.getResponse().status().code());
                span.setStatus(StatusCode.OK);
                span.end();
            })
            .doOnError(e -> {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.end();
            });
    }
}
```

---

## 14. 限流与熔断

### 14.1 限流 (Rate Limiting)

```
┌────────────────────────────────────────────────────────────────────┐
│                    Rate Limiting Architecture                      │
│                                                                    │
│  ┌──────────────┐                                                 │
│  │ KeyResolver  │  提取限流 Key                                   │
│  │ ├ remoteAddr │  (客户端 IP / API Key / User ID / Route ID)    │
│  │ ├ header(key)│                                                 │
│  │ ├ routeId    │                                                 │
│  │ └ custom     │                                                 │
│  └──────┬───────┘                                                 │
│         │                                                         │
│         ▼                                                         │
│  ┌──────────────────┐    ┌────────────────────────────────────┐   │
│  │ RateLimiter      │    │ 算法选择                           │   │
│  │ ├ Local (单机)   │    │                                    │   │
│  │ │  └ TokenBucket │    │ TokenBucket:                       │   │
│  │ │  └ SlidingWin  │    │   replenishRate = 100 req/s       │   │
│  │ ├ Distributed    │    │   burstCapacity = 200             │   │
│  │ │  └ Redis       │    │   requestedTokens = 1             │   │
│  │ │  └ Sentinel    │    │                                    │   │
│  │ └ Cluster        │    │ SlidingWindow:                     │   │
│  └──────────────────┘    │   windowSize = 1s                 │   │
│                          │   maxRequests = 100               │   │
│                          └────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────┘
```

```java
// 令牌桶算法 (本地, 高性能)
public class TokenBucketRateLimiter implements RateLimiter {
    private final long replenishRate;     // 令牌填充速率 (个/秒)
    private final long burstCapacity;     // 桶容量

    // 使用 ConcurrentHashMap + AtomicLong, 无锁设计
    private final ConcurrentHashMap<String, TokenBucket> buckets;

    @Override
    public boolean isAllowed(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
            k -> new TokenBucket(replenishRate, burstCapacity));
        return bucket.tryAcquire(1);
    }
}

// 单个令牌桶 (无锁, 基于 CAS)
class TokenBucket {
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTime;
    private final long replenishRate;
    private final long burstCapacity;

    boolean tryAcquire(int permits) {
        refill();
        while (true) {
            long current = tokens.get();
            if (current < permits) return false;
            if (tokens.compareAndSet(current, current - permits)) return true;
        }
    }

    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillTime.get();
        long elapsed = now - last;
        long tokensToAdd = elapsed * replenishRate / 1_000_000_000L;
        if (tokensToAdd > 0 && lastRefillTime.compareAndSet(last, now)) {
            long newTokens = Math.min(burstCapacity, tokens.get() + tokensToAdd);
            tokens.set(newTokens);
        }
    }
}
```

**限流响应:**

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1705312245
Retry-After: 1
Content-Type: application/json

{
  "code": 42901,
  "message": "Rate limit exceeded. Try again in 1 second."
}
```

### 14.2 熔断 (Circuit Breaker)

```
状态机:
                 失败率 ≥ 阈值
    ┌──────┐  ─────────────────►  ┌──────┐
    │CLOSED│                      │ OPEN │
    │(正常) │  ◄─────────────────  │(熔断) │
    └──┬───┘    探测请求成功       └──┬───┘
       │                              │
       │         等待超时后           │
       │      ┌──────────┐           │
       └─────►│HALF_OPEN │◄──────────┘
              │(半开探测)  │
              └──────────┘
                  │
           探测请求失败 → 回到 OPEN
```

```java
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private volatile State state = State.CLOSED;
    private final SlidingWindowCounter failureCounter;  // 滑动窗口统计

    private final double failureRateThreshold;   // 失败率阈值 (e.g., 50%)
    private final Duration waitDurationInOpen;   // OPEN 状态等待时间 (e.g., 30s)
    private final int slidingWindowSize;         // 滑动窗口大小 (e.g., 100)
    private final int halfOpenPermits;           // HALF_OPEN 允许的探测请求数

    public boolean isCallPermitted() {
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> shouldTransitionToHalfOpen();
            case HALF_OPEN -> halfOpenPermits.tryAcquire();
        };
    }

    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            transitionTo(State.CLOSED);
        }
        failureCounter.recordSuccess();
    }

    public void recordFailure() {
        failureCounter.recordFailure();
        if (state == State.CLOSED && failureCounter.getFailureRate() >= failureRateThreshold) {
            transitionTo(State.OPEN);
        } else if (state == State.HALF_OPEN) {
            transitionTo(State.OPEN);
        }
    }
}
```

### 14.3 重试 (Retry)

```java
public class RetryFilter implements GatewayFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        RetryConfig config = getConfig();

        // 只对幂等方法重试
        if (!config.getMethods().contains(exchange.getRequest().method())) {
            return chain.filter(exchange);
        }

        return chain.filter(exchange)
            .retryWhen(Retry.backoff(config.getRetries(), config.getFirstBackoff())
                .maxBackoff(config.getMaxBackoff())
                .jitter(0.5)              // 50% 抖动, 避免重试风暴
                .filter(throwable -> isRetryable(throwable, config))
                .doBeforeRetry(signal -> {
                    log.warn("Retry attempt {} for route {}",
                        signal.totalRetries() + 1,
                        exchange.getAttribute(GATEWAY_ROUTE_ATTR));
                    resetExchange(exchange); // 重置路由标记, 允许重新转发
                })
            );
    }

    private boolean isRetryable(Throwable t, RetryConfig config) {
        if (t instanceof ConnectTimeoutException) return true;
        if (t instanceof UpstreamResponseException ure) {
            return config.getStatuses().contains(ure.status().code());
        }
        return false;
    }
}
```

---

## 15. Admin API

### 15.1 API 设计

```
Admin API (独立端口, e.g., 9090)
│
├── GET    /admin/health              # 健康检查 (K8s liveness)
├── GET    /admin/ready               # 就绪检查 (K8s readiness)
├── GET    /admin/info                # 网关版本、启动时间、配置信息
│
├── GET    /admin/routes              # 查看所有路由
├── GET    /admin/routes/{id}         # 查看单个路由详情
├── POST   /admin/routes             # 动态添加路由
├── PUT    /admin/routes/{id}        # 动态更新路由
├── DELETE /admin/routes/{id}        # 动态删除路由
├── POST   /admin/routes/refresh     # 强制刷新路由表
│
├── GET    /admin/filters            # 查看所有可用 Filter
├── GET    /admin/predicates         # 查看所有可用 Predicate
├── GET    /admin/endpoints          # 查看所有可用 Endpoint 类型
│
├── GET    /admin/upstream           # 查看上游健康状态
├── GET    /admin/upstream/{host}    # 查看指定上游详情 (连接池等)
│
├── GET    /admin/metrics            # Prometheus 格式指标
├── GET    /admin/metrics/json       # JSON 格式指标
│
├── POST   /admin/config/reload      # 重新加载配置文件
├── GET    /admin/config             # 查看当前运行配置
│
└── GET    /admin/plugins            # 查看已加载插件
```

### 15.2 安全

```java
// Admin API 认证: Bearer Token
public class AdminAuthHandler {
    private final String accessToken;

    public boolean authenticate(HttpServerRequest request) {
        String auth = request.requestHeaders().get("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return false;
        }
        return MessageDigest.isEqual(
            auth.substring(7).getBytes(),
            accessToken.getBytes()
        );
    }
}
```

---

## 16. 插件扩展机制

### 16.1 Plugin SPI

```java
// 插件接口
public interface GatewayPlugin {
    String name();
    String version();

    // 生命周期回调
    void onInstall(PluginContext context);    // 安装时
    void onStart(PluginContext context);      // 启动时
    void onStop(PluginContext context);       // 停止时
    void onUninstall(PluginContext context);  // 卸载时
}

// 插件上下文: 提供注册 Filter/Predicate/Endpoint 的能力
public interface PluginContext {
    void registerFilter(GatewayFilterFactory<?> factory);
    void registerPredicate(RoutePredicateFactory<?> factory);
    void registerEndpoint(EndpointFactory<?> factory);
    void registerConfigSource(ConfigSource source);
    void registerAdminEndpoint(String path, AdminHandler handler);

    // 获取共享资源
    MeterRegistry meterRegistry();
    Serializer serializer();
    ScheduledExecutorService scheduler();
}
```

### 16.2 插件发现与加载

```
┌──────────────────────────────────────────────────────────────────┐
│                    Plugin Loading                                │
│                                                                  │
│  方式 1: ServiceLoader (Java SPI)                               │
│  META-INF/services/io.github.xinfra.lab.gateway.plugin.GatewayPlugin
│                                                                  │
│  方式 2: 类路径扫描 (@GatewayPlugin 注解)                       │
│  通过 Reflections 扫描 classpath                                 │
│                                                                  │
│  方式 3: 外部 JAR 目录                                          │
│  plugins/ 目录下的 JAR 文件, URLClassLoader 动态加载            │
│                                                                  │
│  加载顺序:                                                      │
│  1. 内置插件 (classpath)                                        │
│  2. 外部插件 (plugins/ 目录)                                    │
│  3. 按 @Order 排序                                              │
└──────────────────────────────────────────────────────────────────┘
```

### 16.3 内置插件列表

| 插件 | 模块 | 提供的能力 |
|------|------|-----------|
| **RateLimit** | x-gateway-plugin-ratelimit | TokenBucket / SlidingWindow RateLimiter + Filter |
| **Auth** | x-gateway-plugin-auth | JWT / API-Key / OAuth2 AuthFilter |
| **CircuitBreaker** | x-gateway-plugin-circuitbreaker | CircuitBreaker + BulkheadFilter |
| **Observability** | x-gateway-plugin-observability | Prometheus Metrics + OTLP Tracing + AccessLog |

---

## 17. 部署架构

### 17.1 单机部署 (开发/测试)

```bash
java -jar x-api-gateway-dist.jar -c gateway-config.yml
```

### 17.2 Docker 部署

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY x-api-gateway-dist.jar gateway.jar
COPY gateway-config.yml config.yml
EXPOSE 8080 9090
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75", \
  "-Dreactor.netty.ioWorkerCount=auto", \
  "-jar", "gateway.jar", \
  "-c", "config.yml"]
```

### 17.3 Kubernetes 部署

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: x-api-gateway
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  template:
    spec:
      terminationGracePeriodSeconds: 60
      containers:
        - name: gateway
          image: x-api-gateway:latest
          ports:
            - containerPort: 8080
              name: http
            - containerPort: 9090
              name: admin
          resources:
            requests:
              cpu: "2"
              memory: "2Gi"
            limits:
              cpu: "4"
              memory: "4Gi"
          livenessProbe:
            httpGet:
              path: /admin/health
              port: admin
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /admin/ready
              port: admin
            initialDelaySeconds: 5
            periodSeconds: 5
          lifecycle:
            preStop:
              exec:
                command: ["/bin/sh", "-c", "sleep 15"]  # 等待 LB 摘除
          volumeMounts:
            - name: config
              mountPath: /etc/gateway
      volumes:
        - name: config
          configMap:
            name: gateway-config
---
apiVersion: v1
kind: Service
metadata:
  name: x-api-gateway
spec:
  type: LoadBalancer
  ports:
    - port: 80
      targetPort: 8080
      name: http
  selector:
    app: x-api-gateway
```

### 17.4 生产推荐配置

```
硬件:
- CPU: 8 核+ (网关主要消耗 CPU 做 I/O 调度)
- 内存: 8GB+ (主要用于连接池和缓冲区)
- 网络: 万兆网卡 (减少网络瓶颈)
- SSD: 用于 Access Log 写入

JVM:
- JDK 21+ (ZGC, Virtual Threads)
- -XX:+UseZGC                    # 低延迟 GC
- -XX:MaxRAMPercentage=75        # 堆内存占物理内存 75%
- -XX:+AlwaysPreTouch            # 预分配内存页
- -Xlog:gc*:file=gc.log          # GC 日志

操作系统:
- net.core.somaxconn = 65535
- net.ipv4.tcp_max_syn_backlog = 65535
- net.ipv4.tcp_tw_reuse = 1
- fs.file-max = 1000000
- nofile (ulimit) = 1000000
```

---

## 18. Benchmark 目标与测试方案

### 18.1 性能基准

| 场景 | QPS 目标 | P50 延迟 | P99 延迟 | 测试条件 |
|------|---------|---------|---------|---------|
| Proxy (1KB body) | ≥ 50,000 | < 1ms | < 3ms | 8核16G, 100并发, keep-alive |
| Proxy (10KB body) | ≥ 40,000 | < 1ms | < 5ms | 同上 |
| Proxy + 5 Filters | ≥ 40,000 | < 1.5ms | < 5ms | 含 Auth + RateLimit + Transform |
| Proxy + 10 Filters | ≥ 30,000 | < 2ms | < 8ms | 全量 Filter |
| Route Matching (100 routes) | ≥ 50,000 | < 0.1ms | < 0.5ms | Radix Tree |
| Route Matching (1000 routes) | ≥ 45,000 | < 0.2ms | < 1ms | Radix Tree |

### 18.2 测试工具与方法

```bash
# wrk 压测
wrk -t8 -c100 -d30s --latency http://gateway:8080/api/test

# vegeta 压测 (恒定 QPS)
echo "GET http://gateway:8080/api/test" | \
  vegeta attack -rate=50000/s -duration=30s | \
  vegeta report

# JMH 微基准 (路由匹配、Filter 链)
mvn -pl x-api-gateway-benchmark test
```

### 18.3 JMH 基准测试模块

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
public class RouteMatchBenchmark {

    @Param({"10", "100", "1000"})
    private int routeCount;

    private RadixTreeRouteTable routeTable;

    @Setup
    public void setup() {
        routeTable = new RadixTreeRouteTable();
        for (int i = 0; i < routeCount; i++) {
            routeTable.addRoute(createRoute(i), "/api/service-" + i + "/**");
        }
    }

    @Benchmark
    public List<Route> matchRoute() {
        return routeTable.match("/api/service-50/users/123");
    }
}
```

---

## 19. 与现有代码的映射 & 演进路线

### 19.1 现有代码保留与演进

| 现有类 | 状态 | 演进方向 |
|--------|------|----------|
| `ServerWebExchange` | **保留, 增强** | 增加 `mutate()`, 抽象 `GatewayRequest/GatewayResponse` |
| `GatewayFilter` / `GatewayFilterChain` | **保留** | 接口不变 |
| `DefaultGatewayFilterChain` | **保留** | 性能优化 (数组替代 List) |
| `GatewayFilterFactory` | **保留** | 增加更多内置实现 |
| `RoutePredicate` | **保留, 修改** | `test()` 从 `Mono<Boolean>` 改为 `boolean` |
| `RoutePredicateFactory` | **保留** | 增加 Method/Header/Host/Query 等 |
| `Route` / `DefaultRoute` | **保留, 增强** | 增加 `metadata` 字段 |
| `RouteLocator` / `CacheRouteLocator` | **保留, 增强** | 加入 Radix Tree 索引 |
| `RouteDefinitionRouteLocator` | **保留** | 支持 defaultFilters 合并 |
| `GatewayServerBootstrap` | **保留, 增强** | 支持 TLS、Admin API、Plugin 加载 |
| `ReactorHttpHandler` | **保留** | 无变化 |
| `ExceptionHandlingWebHandler` | **保留** | 增强错误响应体 (JSON body) |
| `RoutePredicateWebHandler` | **保留, 优化** | 使用 Radix Tree 加速 |
| `FilteringWebHandler` | **保留, 增强** | 合并 defaultFilters |
| `HttpEndpoint` | **保留, 增强** | 加入 LoadBalancer, 连接池配置 |
| `RoutingFilter` | **保留, 增强** | 加入 Retry、CircuitBreaker 集成 |
| `GatewayConfig` / `GatewayConfigManager` | **保留, 增强** | 支持完整配置模型, 热加载 |
| `EndpointFactoryManager` 等 Manager | **保留, 优化** | 支持 Plugin 注册, 移除 Reflections 依赖 |

### 19.2 需要修复的已知问题

| 问题 | 文件 | 说明 |
|------|------|------|
| BooleanSpec switch fall-through | `RouteLocatorBuilder.java` | AND case 缺少 break, 落入 OR |
| HttpEndpoint 只用 urls.get(0) | `HttpEndpoint.java` | TODO: 接入 LoadBalancer |
| defaultFilters 未合并 | `FilteringWebHandler.java` | GatewayConfig.defaultFilters 未使用 |
| RoutePredicate.test() 返回 Mono | `RoutePredicate.java` | 改为同步 boolean |

### 19.3 演进路线 (里程碑)

```
Phase 1: 核心增强 (MVP → Production-Ready Core)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ✦ 修复已知 Bug (BooleanSpec fall-through, etc.)
  ✦ ServerWebExchange 增强 (mutate, GatewayRequest/Response 抽象)
  ✦ RoutePredicate 改为同步
  ✦ 内置 Predicate: Method, Header, Host, Query
  ✦ 内置 Filter: StripPrefix, RewritePath, AddResponseHeader, RemoveHeader, RequestId
  ✦ LoadBalancer: WeightedRoundRobin, Random
  ✦ 连接池优化 (per-upstream 配置)
  ✦ defaultFilters 合并
  ✦ 优雅关闭
  ✦ 完善单元测试 (> 80% 覆盖率)

Phase 2: 可观测性 + 安全
━━━━━━━━━━━━━━━━━━━━━━━━━
  ✦ Access Log (JSON 格式, stdout/file)
  ✦ Metrics (Micrometer + Prometheus exporter)
  ✦ 健康检查 (主动 + 被动)
  ✦ Admin API (独立端口, 路由管理 + 指标暴露)
  ✦ TLS 终止
  ✦ CORS Filter
  ✦ IP 黑白名单

Phase 3: 弹性 + 安全增强
━━━━━━━━━━━━━━━━━━━━━━━━━
  ✦ 令牌桶限流 (本地)
  ✦ 熔断器 (Circuit Breaker)
  ✦ 重试 (Retry, 指数退避 + 抖动)
  ✦ JWT / API-Key 认证
  ✦ 请求体大小限制
  ✦ 配置热加载 (File Watch)
  ✦ Radix Tree 路由表

Phase 4: 分布式 + 插件化
━━━━━━━━━━━━━━━━━━━━━━━━━
  ✦ Plugin SPI 机制
  ✦ 分布式限流 (Redis)
  ✦ 配置中心集成 (Nacos / etcd)
  ✦ 分布式追踪 (OpenTelemetry)
  ✦ OAuth2 认证
  ✦ 外部插件 JAR 加载

Phase 5: 高级特性
━━━━━━━━━━━━━━━━━━
  ✦ WebSocket 代理
  ✦ HTTP/2 支持
  ✦ 一致性哈希 / 最少连接数 LB
  ✦ 灰度发布 (Weight Predicate)
  ✦ 请求/响应体改写
  ✦ JMH Benchmark 套件
  ✦ Docker / K8s 部署模板
  ✦ 服务发现集成 (Nacos / Consul)
```

---

## 附录 A: 技术选型对比

| 维度 | X-API-Gateway | Spring Cloud Gateway | Kong | BFE | APISIX |
|------|---------------|---------------------|------|-----|--------|
| 语言 | Java | Java | Lua/Go | Go | Lua/Go |
| 网络层 | Reactor Netty | Reactor Netty | Nginx+OpenResty | Go net/http | Nginx+OpenResty |
| 框架依赖 | 无 (轻量) | Spring WebFlux | - | - | - |
| 路由匹配 | Radix Tree | 线性扫描 | Radix Tree | Trie | Radix Tree |
| 配置格式 | YAML | YAML/Java | YAML/DB | JSON | YAML/etcd |
| 插件机制 | SPI + 注解 | Spring Bean | Lua Plugin | Go Plugin | Lua Plugin |
| 分布式限流 | Redis | Redis (需额外) | Redis/DB | - | etcd |
| 性能 (QPS) | 50K+ (目标) | 25K | 35K | 60K+ | 40K+ |

## 附录 B: 类图 (核心)

```
                               ┌───────────┐
                               │  Ordered  │
                               └─────┬─────┘
                                     │
                      ┌──────────────┼──────────────────┐
                      │              │                   │
                ┌─────┴──────┐ ┌────┴────┐      ┌──────┴───────┐
                │GatewayFilter│ │  Route  │      │RoutePredicate│
                └─────┬──────┘ └────┬────┘      └──────┬───────┘
                      │             │                   │
           ┌──────────┤        ┌────┴────┐        ┌────┴──────────┐
           │          │        │Default  │        │PathPredicate   │
    ┌──────┴──────┐   │        │Route    │        │MethodPredicate │
    │GlobalGateway│   │        └─────────┘        │HeaderPredicate │
    │Filter       │   │                           └────────────────┘
    └──────┬──────┘   │
           │          │
    ┌──────┴──────┐   ├── AddHeaderFilter
    │RoutingFilter│   ├── StripPrefixFilter
    └─────────────┘   ├── RateLimitFilter
                      └── ...

   ┌───────────────┐     ┌──────────────────┐
   │  WebHandler   │     │WebExceptionHandler│
   └───────┬───────┘     └────────┬─────────┘
           │                      │
   ┌───────┴──────────┐   ┌──────┴──────────┐
   │WebHandlerDecorator│   │DefaultWebException│
   └───────┬──────────┘   │Handler           │
           │               └──────────────────┘
   ┌───────┼──────────────────────┐
   │       │                      │
┌──┴───────┴──┐  ┌──────────────┐│ ┌─────────────┐
│ExceptionHdl │  │RoutePredicate││ │FilteringWeb │
│WebHandler   │  │WebHandler    ││ │Handler      │
└─────────────┘  └──────────────┘│ └─────────────┘
                                 │
                          ┌──────┴──────┐
                          │MetricsWeb   │
                          │Handler      │
                          └─────────────┘

   ┌────────────┐     ┌─────────────────┐
   │ Endpoint   │     │  RouteLocator   │
   └─────┬──────┘     └────────┬────────┘
         │                     │
   ┌─────┴──────┐     ┌───────┼──────────────┐
   │HttpEndpoint │     │       │              │
   └────────────┘  ┌──┴──────┐│ ┌────────────┴─────┐
                   │CacheRoute││ │RouteDefinition   │
                   │Locator   ││ │RouteLocator      │
                   └──────────┘│ └──────────────────┘
                               │
                       ┌───────┴───────┐
                       │RouteLocator   │
                       │Builder        │
                       └───────────────┘
```

---

> **文档维护说明**: 本文档随代码演进持续更新。每个 Phase 完成后, 更新对应章节的实现状态和性能数据。
