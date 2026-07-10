# Changelog

本 Changelog 记录 X-API-Gateway 从初始骨架到完整功能实现的所有变更。

---

## [Unreleased] — 2026-07-10

### Phase 1: 核心增强

- **修复 RouteLocatorBuilder BooleanSpec**: AND case 缺少 break 导致落入 OR 的 bug
- **RoutePredicate 改为同步**: `test()` 从 `Mono<Boolean>` 改为 `boolean`，路由匹配纯 CPU 操作不需要响应式包装
- **ServerWebExchange 增强**: 增加 `mutate()` 方法，抽象 `GatewayRequest` / `GatewayResponse` 接口
- **内置 Predicate**: 新增 Method、Header、Host、Query、Cookie、RemoteAddr、Weight、Time 共 8 种路由谓词
- **内置 Filter**: 新增 StripPrefix、RewritePath、AddResponseHeader、RemoveHeader、RemoveResponseHeader、RequestId、RequestSize、SecureHeaders
- **负载均衡**: 实现 WeightedRoundRobin（平滑加权轮询）、Random（加权随机）负载均衡策略
- **HttpEndpoint 增强**: 接入 LoadBalancer，支持多上游实例 + 权重配置 + 连接池参数（connectTimeout/readTimeout/poolMaxConnections/poolMaxIdleTime/poolMaxLifeTime）
- **defaultFilters 合并**: FilteringWebHandler 支持合并 globalFilters + defaultFilters + routeFilters
- **优雅关闭**: ReactorHttpHandler 支持 `markShuttingDown()` + `awaitQuiescence()`，GatewayServerBootstrap 注册 shutdown hook
- **DefaultGatewayFilterChain 优化**: 改用数组 + index 替代 List 迭代器
- **完善单元测试**: 路由匹配、Filter 链、Predicate 组合等核心路径测试

### Phase 2: 可观测性与安全

- **Access Log**: 新增 `AccessLogGatewayFilter`（全局）和 `AccessLogWebHandler`，JSON 格式访问日志
- **Metrics**: 新增 `MetricsCollector` + `MetricsWebHandler`，采集请求总数、延迟分布、活跃请求数等指标
- **健康检查**: 新增 `HealthChecker`，支持主动 HTTP 探测 + 被动结果记录，连续失败/成功阈值控制
- **Admin API**: 新增独立端口的管理 API（`AdminServer` + `AdminHandler`），支持路由查看/动态增删改/强制刷新/指标暴露/健康检查/就绪检查
- **TLS 终止**: GatewayServerBootstrap 支持 TLS 配置（certPath/keyPath/protocols）
- **HTTP/2**: 支持 h2c 和 h2 (TLS) 协议
- **CORS Filter**: 新增 `CorsGatewayFilterFactory`，支持 allowedOrigins/Methods/Headers/maxAge
- **IP 黑白名单**: 新增 `IpFilterGatewayFilterFactory`，支持 CIDR 格式

### Phase 3: 弹性与安全增强

- **令牌桶限流**: 新增 `LocalRateLimiter`（无锁 CAS 实现）+ `RateLimitGatewayFilterFactory`
- **滑动窗口限流**: 新增 `SlidingWindowRateLimiter`
- **熔断器**: 新增 `CircuitBreakerGatewayFilterFactory`，滑动窗口统计 + CLOSED/OPEN/HALF_OPEN 状态机
- **并发控制**: 新增 `BulkheadGatewayFilterFactory`（Semaphore 实现）
- **重试**: 新增 `RetryGatewayFilterFactory`，支持指数退避 + 抖动 + 可重试状态码/方法配置
- **JWT 认证**: 新增 `AuthGatewayFilterFactory`，支持 JWT / API-Key / HMAC 认证
- **请求体大小限制**: 新增 `RequestSizeGatewayFilterFactory`
- **配置热加载**: 新增 `ConfigFileWatcher`（基于 WatchService），文件变更自动刷新路由表
- **Radix Tree 路由表**: 新增 `RadixTreeRouteTable` + `RadixTreeNode`，O(k) 路由匹配性能

### Phase 4: 分布式与插件化

- **Plugin SPI**: 新增 `GatewayPlugin` / `PluginContext` / `PluginManager` 插件机制，支持 Java SPI + 外部 JAR 目录加载
- **分布式限流**: 新增 `RedisRateLimiter`，基于 Lettuce + Lua 脚本的 Redis 令牌桶
- **Nacos 配置中心**: 新增 `x-api-gateway-config-nacos` 模块，`NacosConfigSource` 实现配置推送
- **分布式追踪**: 新增 `TracingFilter`（全局），集成 OpenTelemetry API（W3C TraceContext 传播）
- **ConfigSource SPI**: 新增 `ConfigSource` / `FileConfigSource` / `WritableConfigSource` 配置源接口体系
- **GatewayConfigManager 增强**: 支持多配置源合并、优先级排序、动态刷新

### Phase 5: 高级特性

- **WebSocket 代理**: 新增 `WebSocketEndpoint` + `WebSocketEndpointFactory`，完整 WebSocket 上游代理
- **高级负载均衡**: 新增 LeastConnections（最少连接）、ConsistentHash（一致性哈希）、IPHash（IP 哈希）策略
- **灰度发布**: `WeightRoutePredicateFactory` 支持按权重分流
- **请求/响应体改写**: 新增 `RequestBodyRewriteGatewayFilterFactory` + `ResponseBodyRewriteGatewayFilterFactory`
- **JMH Benchmark**: 新增 `x-api-gateway-benchmark` 模块，包含 FilterChainBenchmark + RouteMatchBenchmark
- **Fat-jar 打包**: 新增 `x-api-gateway-dist` 模块，maven-shade-plugin 打包
- **Docker 支持**: 新增 `docker/Dockerfile` + `docker/config.yml` + `docker/logback.xml`
- **K8s 部署模板**: 新增 `deploy/k8s/deployment.yaml` + `deploy/k8s/configmap.yaml`
- **启动脚本**: 新增 `bin/start.sh`
- **Admin API 路由管理**: 支持动态 POST/PUT/DELETE 路由 + 配置持久化 (WritableConfigSource)
- **事件总线**: 新增 `GatewayEventBus` + `RouteRefreshEvent`

### Phase 10: 服务发现集成

- **ServiceDiscovery SPI**: 新增 `ServiceDiscovery` / `ServiceInstance` / `StaticServiceDiscovery` 接口
- **Nacos 服务发现**: 新增 `NacosServiceDiscovery` + `NacosServiceDiscoveryFactory`
- **HttpEndpoint serviceId 支持**: 配置 `serviceId` 后自动从服务发现获取实例列表，动态更新 UpstreamTarget
- **WebSocketEndpoint 服务发现**: WebSocketEndpointFactory 支持 ServiceDiscovery 注入
- **EndpointFactoryManager 增强**: 新增 `setServiceDiscovery()` 统一传播到 Http/WebSocket 工厂
- **Bootstrap 集成**: `tryLoadServiceDiscovery()` 反射加载 Nacos 服务发现，生命周期管理
- **Admin API 服务查询**: 新增 `/admin/services/{serviceId}` 端点

### 生产就绪修复

- **配置安全**: 移除所有 `accessToken: changeme` 默认密码，改为空字符串 + 启动时 WARN 日志
- **WebSocket 连接池**: WebSocketEndpoint 使用 `ConnectionProvider.builder("ws-upstream")` 配置连接池
- **HealthChecker 连接池**: 使用 `ConnectionProvider.builder("health-check").maxConnections(16)` 专用连接池
- **CI/CD 增强**: GitHub Actions 重写为 test → build (artifact upload) → docker (ghcr.io) 三阶段流水线
- **ProxyBenchmark**: 新增端到端 HTTP 代理 JMH 基准测试
- **RedisRateLimiterTest**: 新增 5 个单元测试（allowed/denied/error fallback/key prefix/close）

### 文档

- 新增完整架构与使用文档 (`docs/ARCHITECTURE.md`)
- 新增本 Changelog (`CHANGELOG.md`)

### 模块新增

| 模块 | 说明 |
|------|------|
| `x-api-gateway-config-nacos` | Nacos 配置源 + 服务发现 |
| `x-api-gateway-integration-tests` | 端到端集成测试 |
| `x-api-gateway-benchmark` | JMH 性能基准测试 |
| `x-api-gateway-dist` | Fat-jar 打包分发 |

### 测试

- 总计 401 个测试用例，0 失败
  - x-api-gateway-server: 371 tests
  - x-api-gateway-config-nacos: 23 tests
  - x-api-gateway-integration-tests: 7 tests
