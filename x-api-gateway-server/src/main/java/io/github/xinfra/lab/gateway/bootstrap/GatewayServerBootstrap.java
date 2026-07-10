package io.github.xinfra.lab.gateway.bootstrap;

import com.google.common.collect.Lists;
import io.github.xinfra.lab.gateway.admin.AdminServer;
import io.github.xinfra.lab.gateway.commons.Assert;
import io.github.xinfra.lab.gateway.commons.binder.Binder;
import io.github.xinfra.lab.gateway.commons.binder.YmlBinder;
import io.github.xinfra.lab.gateway.config.ConfigFileWatcher;
import io.github.xinfra.lab.gateway.config.ConfigSource;
import io.github.xinfra.lab.gateway.config.FileConfigSource;
import io.github.xinfra.lab.gateway.config.GatewayConfig;
import io.github.xinfra.lab.gateway.config.GatewayConfigManager;
import io.github.xinfra.lab.gateway.config.WritableConfigSource;
import io.github.xinfra.lab.gateway.discovery.ServiceDiscovery;
import io.github.xinfra.lab.gateway.endpoint.EndpointFactoryManager;
import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.filter.GatewayFilter;
import io.github.xinfra.lab.gateway.filter.GatewayFilterDefinition;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactory;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactoryManager;
import io.github.xinfra.lab.gateway.filter.OrderedGatewayFilter;
import io.github.xinfra.lab.gateway.filter.global.AccessLogGatewayFilter;
import io.github.xinfra.lab.gateway.filter.global.GlobalGatewayFilter;
import io.github.xinfra.lab.gateway.filter.global.RoutingFilter;
import io.github.xinfra.lab.gateway.filter.global.TracingFilter;
import io.github.xinfra.lab.gateway.handler.AccessLogWebHandler;
import io.github.xinfra.lab.gateway.handler.DefaultWebExceptionHandler;
import io.github.xinfra.lab.gateway.handler.ExceptionHandlingWebHandler;
import io.github.xinfra.lab.gateway.handler.FilteringWebHandler;
import io.github.xinfra.lab.gateway.handler.MetricsWebHandler;
import io.github.xinfra.lab.gateway.handler.ReactorHttpHandler;
import io.github.xinfra.lab.gateway.handler.RoutePredicateWebHandler;
import io.github.xinfra.lab.gateway.handler.WebExceptionHandler;
import io.github.xinfra.lab.gateway.handler.WebHandler;
import io.github.xinfra.lab.gateway.healthcheck.HealthChecker;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.plugin.DefaultPluginContext;
import io.github.xinfra.lab.gateway.plugin.PluginManager;
import io.github.xinfra.lab.gateway.route.CacheRouteLocator;
import io.github.xinfra.lab.gateway.route.GatewayConfigRouteDefinitionLocator;
import io.github.xinfra.lab.gateway.route.RouteDefinitionRouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import lombok.extern.slf4j.Slf4j;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class GatewayServerBootstrap {

    private int port;
    private List<GlobalGatewayFilter> globalFilters;
    private List<GatewayFilter> defaultFilters;
    private List<WebExceptionHandler> webExceptionHandlers;
    private RouteLocator routeLocator;
    private String configPath;
    private DisposableServer server;
    private ReactorHttpHandler httpHandler;
    private Duration shutdownTimeout = Duration.ofSeconds(30);
    private AdminServer adminServer;
    private HealthChecker healthChecker;
    private MetricsCollector metricsCollector = MetricsCollector.INSTANCE;
    private ConfigFileWatcher configFileWatcher;
    private GatewayConfigManager gatewayConfigManager;
    private FileConfigSource fileConfigSource;
    private PluginManager pluginManager;
    private ServiceDiscovery serviceDiscovery;

    public RouteLocator getRouteLocator() {
        return routeLocator;
    }

    public GatewayServerBootstrap port(int port) {
        this.port = port;
        return this;
    }

    public GatewayServerBootstrap globalFilters(List<GlobalGatewayFilter> globalFilters) {
        Assert.notNull(globalFilters, "globalFilters must not be null.");
        this.globalFilters = globalFilters;
        return this;
    }

    public GatewayServerBootstrap defaultFilters(List<GatewayFilter> defaultFilters) {
        Assert.notNull(defaultFilters, "defaultFilters must not be null.");
        this.defaultFilters = defaultFilters;
        return this;
    }

    public GatewayServerBootstrap webExceptionHandlers(List<WebExceptionHandler> webExceptionHandlers) {
        Assert.notNull(webExceptionHandlers, "webExceptionHandlers must not be null.");
        this.webExceptionHandlers = webExceptionHandlers;
        return this;
    }

    public GatewayServerBootstrap routeLocator(RouteLocator routeLocator) {
        Assert.notNull(routeLocator, "routeLocator must not be null");
        this.routeLocator = routeLocator;
        return this;
    }

    public GatewayServerBootstrap configPath(String configPath) {
        Assert.notNull(configPath, "configPath must not be null");
        this.configPath = configPath;
        return this;
    }

    public GatewayServerBootstrap shutdownTimeout(Duration shutdownTimeout) {
        Assert.notNull(shutdownTimeout, "shutdownTimeout must not be null");
        this.shutdownTimeout = shutdownTimeout;
        return this;
    }

    public GatewayServerBootstrap start() {
        GatewayConfig gatewayConfig = null;

        if (routeLocator == null) {
            gatewayConfig = loadGatewayConfig();
            tryLoadServiceDiscovery(gatewayConfig);
            loadPlugins(gatewayConfig);
            routeLocator = buildRouteLocator(gatewayConfig);
        }
        if (globalFilters == null) {
            globalFilters = getDefaultGlobalFilters(gatewayConfig);
        }
        if (webExceptionHandlers == null) {
            webExceptionHandlers = getDefaultWebExceptionHandlers();
        }
        if (defaultFilters == null) {
            defaultFilters = buildDefaultFilters(gatewayConfig);
        }

        if (gatewayConfig != null && gatewayConfig.getServer() != null) {
            if (port == 0) {
                port = gatewayConfig.getServer().getPort();
            }
            shutdownTimeout = Duration.ofMillis(gatewayConfig.getServer().getShutdownTimeoutMs());
        }

        WebHandler chain = new FilteringWebHandler(globalFilters, defaultFilters);
        chain = new RoutePredicateWebHandler(chain, routeLocator);
        chain = new AccessLogWebHandler(chain);
        chain = new MetricsWebHandler(chain, metricsCollector);
        chain = new ExceptionHandlingWebHandler(chain, webExceptionHandlers);
        this.httpHandler = new ReactorHttpHandler(chain);

        HttpServer httpServer = HttpServer.create()
                .handle(httpHandler::handle)
                .bindAddress(() -> new InetSocketAddress(port));

        if (gatewayConfig != null && gatewayConfig.getServer() != null) {
            GatewayConfig.ServerConfig sc = gatewayConfig.getServer();
            if (sc.isHttp2Enabled()) {
                httpServer = httpServer.protocol(HttpProtocol.H2C, HttpProtocol.HTTP11);
                log.info("HTTP/2 (h2c) enabled");
            }
            httpServer = httpServer
                    .childOption(ChannelOption.TCP_NODELAY, sc.isTcpNoDelay())
                    .childOption(ChannelOption.SO_KEEPALIVE, sc.isSoKeepAlive())
                    .option(ChannelOption.SO_BACKLOG, sc.getBacklog());
            if (sc.getWorkerThreads() > 0) {
                httpServer = httpServer.runOn(reactor.netty.resources.LoopResources.create(
                        "gateway", sc.getBossThreads(), sc.getWorkerThreads(), true));
            }
            GatewayConfig.TlsConfig tlsConfig = sc.getTls();
            if (tlsConfig != null && tlsConfig.isEnabled()) {
                httpServer = configureTls(httpServer, tlsConfig, sc.isHttp2Enabled());
            }
        }

        this.server = httpServer.bind().block();

        log.info("X-API-Gateway started on port {}", port);

        startAdminServer(gatewayConfig);
        startHealthChecker(gatewayConfig);
        startConfigFileWatcher();
        startPlugins();
        registerShutdownHook();

        return this;
    }

    public GatewayServerBootstrap stop() {
        if (pluginManager != null) {
            pluginManager.stopPlugins();
        }
        if (configFileWatcher != null) {
            configFileWatcher.stop();
        }
        if (healthChecker != null) {
            healthChecker.stop();
        }
        if (serviceDiscovery != null) {
            serviceDiscovery.stop();
        }
        if (adminServer != null) {
            adminServer.stop();
        }
        if (this.server != null) {
            log.info("Stopping X-API-Gateway...");
            if (httpHandler != null) {
                httpHandler.markShuttingDown();
                long drainMs = Math.min(shutdownTimeout.toMillis() / 2, 10000);
                log.info("Draining active requests (timeout={}ms)...", drainMs);
                if (httpHandler.awaitQuiescence(drainMs)) {
                    log.info("All active requests drained.");
                } else {
                    log.warn("Drain timeout reached, {} requests still active.",
                            httpHandler.getActiveRequests());
                }
            }
            this.server.disposeNow(shutdownTimeout);
            log.info("X-API-Gateway stopped.");
        }
        return this;
    }

    private void startAdminServer(GatewayConfig gatewayConfig) {
        if (gatewayConfig == null || gatewayConfig.getAdmin() == null || !gatewayConfig.getAdmin().isEnabled()) {
            return;
        }
        GatewayConfig.AdminConfig adminConfig = gatewayConfig.getAdmin();
        AdminServer.Config config = new AdminServer.Config();
        config.setEnabled(true);
        config.setPort(adminConfig.getPort());
        config.setAccessToken(adminConfig.getAccessToken());
        if (adminConfig.getAccessToken() == null || adminConfig.getAccessToken().isEmpty()) {
            log.warn("Admin API started WITHOUT access token, all endpoints are unprotected!");
        }
        adminServer = new AdminServer(config, routeLocator, metricsCollector);
        if (pluginManager != null) {
            adminServer.getHandler().setPluginManager(pluginManager);
        }
        if (gatewayConfigManager != null) {
            adminServer.getHandler().setGatewayConfigManager(gatewayConfigManager);
        }
        if (serviceDiscovery != null) {
            adminServer.getHandler().setServiceDiscovery(serviceDiscovery);
        }
        WritableConfigSource writableSource = findWritableConfigSource();
        if (writableSource != null) {
            adminServer.getHandler().setWritableConfigSource(writableSource);
        }
        adminServer.start();
    }

    private void startHealthChecker(GatewayConfig gatewayConfig) {
        if (gatewayConfig == null || gatewayConfig.getHealthCheck() == null
                || !gatewayConfig.getHealthCheck().isEnabled()) {
            return;
        }
        GatewayConfig.HealthCheckConfig hcConfig = gatewayConfig.getHealthCheck();
        HealthChecker.Config config = new HealthChecker.Config();
        config.setEnabled(true);
        config.setIntervalMs(hcConfig.getIntervalMs());
        config.setPath(hcConfig.getPath());
        config.setUnhealthyThreshold(hcConfig.getUnhealthyThreshold());
        config.setHealthyThreshold(hcConfig.getHealthyThreshold());
        config.setTimeoutMs(hcConfig.getTimeoutMs());
        healthChecker = new HealthChecker(config, routeLocator);
        HealthChecker.setInstance(healthChecker);
        healthChecker.start();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered, starting graceful shutdown...");
            stop();
        }, "gateway-shutdown"));
    }

    private GatewayConfig loadGatewayConfig() {
        this.gatewayConfigManager = new GatewayConfigManager();
        if (configPath == null) {
            gatewayConfigManager.loadDefault();
        } else {
            gatewayConfigManager.load(configPath);
        }

        String resolvedPath = gatewayConfigManager.getConfigPath();
        if (resolvedPath != null) {
            this.fileConfigSource = new FileConfigSource(resolvedPath);
            gatewayConfigManager.addConfigSource(fileConfigSource);
        }

        GatewayConfig config = gatewayConfigManager.getGatewayConfig();
        tryLoadNacosConfigSource(config);

        if (!gatewayConfigManager.getConfigSources().isEmpty()) {
            gatewayConfigManager.loadFromSources();
        }

        return gatewayConfigManager.getGatewayConfig();
    }

    private WritableConfigSource findWritableConfigSource() {
        if (gatewayConfigManager != null) {
            for (ConfigSource source : gatewayConfigManager.getConfigSources()) {
                if (source instanceof WritableConfigSource) {
                    return (WritableConfigSource) source;
                }
            }
        }
        if (fileConfigSource != null) {
            return fileConfigSource;
        }
        return null;
    }

    private void tryLoadNacosConfigSource(GatewayConfig gatewayConfig) {
        if (gatewayConfig == null || gatewayConfig.getNacos() == null
                || !gatewayConfig.getNacos().isEnabled()) {
            return;
        }
        GatewayConfig.NacosConfig nacosConfig = gatewayConfig.getNacos();
        try {
            Class<?> factoryClass = Class.forName(
                    "io.github.xinfra.lab.gateway.nacos.NacosConfigSourceFactory");
            Object source = factoryClass.getMethod("create", GatewayConfig.NacosConfig.class)
                    .invoke(null, nacosConfig);
            if (source instanceof ConfigSource) {
                gatewayConfigManager.addConfigSource((ConfigSource) source);
                log.info("Nacos config source loaded: serverAddr={}, dataId={}, group={}",
                        nacosConfig.getServerAddr(), nacosConfig.getDataId(), nacosConfig.getGroup());
                if (source instanceof WritableConfigSource) {
                    this.fileConfigSource = null;
                }
            }
        } catch (ClassNotFoundException e) {
            log.warn("Nacos config source enabled but x-api-gateway-config-nacos module not found on classpath");
        } catch (Exception e) {
            log.error("Failed to create Nacos config source", e);
        }
    }

    private void tryLoadServiceDiscovery(GatewayConfig gatewayConfig) {
        if (gatewayConfig == null || gatewayConfig.getNacos() == null
                || !gatewayConfig.getNacos().isDiscoveryEnabled()) {
            return;
        }
        GatewayConfig.NacosConfig nacosConfig = gatewayConfig.getNacos();
        try {
            Class<?> factoryClass = Class.forName(
                    "io.github.xinfra.lab.gateway.nacos.NacosServiceDiscoveryFactory");
            Object discovery = factoryClass.getMethod("create", GatewayConfig.NacosConfig.class)
                    .invoke(null, nacosConfig);
            if (discovery instanceof ServiceDiscovery) {
                this.serviceDiscovery = (ServiceDiscovery) discovery;
                this.serviceDiscovery.start();
                EndpointFactoryManager.INSTANCE.setServiceDiscovery(this.serviceDiscovery);
                log.info("Nacos service discovery loaded: serverAddr={}", nacosConfig.getServerAddr());
            }
        } catch (ClassNotFoundException e) {
            log.warn("Nacos discovery enabled but x-api-gateway-config-nacos module not found on classpath");
        } catch (Exception e) {
            log.error("Failed to create Nacos service discovery", e);
        }
    }

    private void loadPlugins(GatewayConfig gatewayConfig) {
        if (gatewayConfig == null) return;

        GatewayConfig.PluginConfig pluginConfig = gatewayConfig.getPlugins();
        String pluginsDir = pluginConfig != null ? pluginConfig.getPluginsDir() : "plugins";
        boolean enabled = pluginConfig != null && pluginConfig.isEnabled();

        pluginManager = new PluginManager(new DefaultPluginContext(), pluginsDir);

        if (enabled) {
            pluginManager.loadPlugins();
        }
    }

    private void startPlugins() {
        if (pluginManager != null) {
            pluginManager.startPlugins();
        }
    }

    private void startConfigFileWatcher() {
        if (gatewayConfigManager == null || gatewayConfigManager.getConfigPath() == null) {
            return;
        }
        if (!(routeLocator instanceof CacheRouteLocator)) {
            return;
        }
        configFileWatcher = new ConfigFileWatcher(
                gatewayConfigManager.getConfigPath(),
                gatewayConfigManager,
                (CacheRouteLocator) routeLocator);
        configFileWatcher.start();
    }

    private HttpServer configureTls(HttpServer httpServer, GatewayConfig.TlsConfig tlsConfig, boolean http2Enabled) {
        try {
            SslContextBuilder builder = SslContextBuilder.forServer(
                    new File(tlsConfig.getCertPath()), new File(tlsConfig.getKeyPath()));
            if (tlsConfig.getProtocols() != null && !tlsConfig.getProtocols().isEmpty()) {
                builder.protocols(tlsConfig.getProtocols().toArray(new String[0]));
            }
            SslContext sslContext = builder.build();
            httpServer = httpServer.secure(spec -> spec.sslContext(sslContext));
            if (http2Enabled) {
                httpServer = httpServer.protocol(HttpProtocol.H2, HttpProtocol.HTTP11);
            }
            log.info("TLS enabled with cert={}, key={}", tlsConfig.getCertPath(), tlsConfig.getKeyPath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure TLS", e);
        }
        return httpServer;
    }

    private RouteLocator buildRouteLocator(GatewayConfig gatewayConfig) {
        GatewayConfigRouteDefinitionLocator definitionLocator =
                new GatewayConfigRouteDefinitionLocator(gatewayConfig);
        RouteLocator routeDefinitionRouteLocator =
                new RouteDefinitionRouteLocator(definitionLocator);
        return new CacheRouteLocator(routeDefinitionRouteLocator);
    }

    private List<GatewayFilter> buildDefaultFilters(GatewayConfig gatewayConfig) {
        if (gatewayConfig == null) {
            return Collections.emptyList();
        }
        List<GatewayFilterDefinition> filterDefs = gatewayConfig.getDefaultFilters();
        if (filterDefs == null || filterDefs.isEmpty()) {
            return Collections.emptyList();
        }
        Binder binder = new YmlBinder();
        List<GatewayFilter> filters = new ArrayList<>();
        for (GatewayFilterDefinition def : filterDefs) {
            GatewayFilterFactory factory = GatewayFilterFactoryManager.INSTANCE.lookup(def.getName());
            if (factory == null) {
                throw new IllegalArgumentException("GatewayFilterFactory named: " + def.getName() + " not found");
            }
            Object config = def.getConfig() != null
                    ? binder.binder(factory.getConfigClass(), def.getConfig())
                    : null;
            GatewayFilter filter = factory.apply(config);
            if (!(filter instanceof io.github.xinfra.lab.gateway.commons.Ordered)) {
                Order orderAnnotation = factory.getClass().getAnnotation(Order.class);
                if (orderAnnotation != null) {
                    filter = new OrderedGatewayFilter(filter, orderAnnotation.value());
                }
            }
            filters.add(filter);
        }
        return filters;
    }

    protected List<WebExceptionHandler> getDefaultWebExceptionHandlers() {
        return Lists.newArrayList(new DefaultWebExceptionHandler());
    }

    protected List<GlobalGatewayFilter> getDefaultGlobalFilters(GatewayConfig gatewayConfig) {
        List<GlobalGatewayFilter> filters = Lists.newArrayList(
                new AccessLogGatewayFilter(), new RoutingFilter());

        if (gatewayConfig != null && gatewayConfig.getTracing() != null
                && gatewayConfig.getTracing().isEnabled() && TracingFilter.isAvailable()) {
            String serviceName = gatewayConfig.getTracing().getServiceName();
            filters.add(0, new TracingFilter(serviceName));
            log.info("OpenTelemetry tracing enabled with service name: {}", serviceName);
        }

        return filters;
    }
}
