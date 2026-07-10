package io.github.xinfra.lab.gateway.filter.global;

import io.github.xinfra.lab.gateway.commons.Ordered;
import io.github.xinfra.lab.gateway.filter.GatewayFilterChain;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

public class AccessLogGatewayFilter implements GlobalGatewayFilter, Ordered {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("gateway.access");
    private static final String START_TIME_ATTR = "gatewayStartTime";

    @Override
    public int getOrder() {
        return -800;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().putIfAbsent(START_TIME_ATTR, startTime);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    if (!ACCESS_LOG.isInfoEnabled()) return;

                    long duration = System.currentTimeMillis() - startTime;
                    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
                    String routeId = route != null ? route.getId() : "-";
                    String requestId = exchange.getAttributeOrDefault("gatewayRequestId", "-");
                    int statusCode = exchange.getResponse().status() != null
                            ? exchange.getResponse().status().code() : 0;

                    InetSocketAddress remoteAddr = exchange.getRequest().remoteAddress();
                    String clientIp = remoteAddr != null ? remoteAddr.getAddress().getHostAddress() : "-";
                    String method = exchange.getRequest().method().name();
                    String path = exchange.getRequest().fullPath();
                    String userAgent = exchange.getRequest().requestHeaders().get("User-Agent");
                    if (userAgent == null) userAgent = "-";

                    String queryString = exchange.getRequest().uri() != null
                            ? exchange.getRequest().uri() : "";
                    int qIdx = queryString.indexOf('?');
                    queryString = qIdx >= 0 ? queryString.substring(qIdx + 1) : "";

                    String protocol = exchange.getRequest().version() != null
                            ? exchange.getRequest().version().text() : "HTTP/1.1";

                    String referer = exchange.getRequest().requestHeaders().get("Referer");
                    if (referer == null) referer = "-";

                    String logLine = buildJsonLog(requestId, clientIp, method, path,
                            queryString, protocol, statusCode, duration,
                            routeId, userAgent, referer);
                    ACCESS_LOG.info(logLine);
                });
    }

    private String buildJsonLog(String requestId, String clientIp, String method,
                                String path, String queryString, String protocol,
                                int statusCode, long durationMs,
                                String routeId, String userAgent, String referer) {
        StringBuilder sb = new StringBuilder(384);
        sb.append("{\"requestId\":\"").append(escape(requestId)).append('"');
        sb.append(",\"timestamp\":\"").append(Instant.now().toString()).append('"');
        sb.append(",\"clientIp\":\"").append(escape(clientIp)).append('"');
        sb.append(",\"method\":\"").append(method).append('"');
        sb.append(",\"path\":\"").append(escape(path)).append('"');
        if (queryString != null && !queryString.isEmpty()) {
            sb.append(",\"queryString\":\"").append(escape(queryString)).append('"');
        }
        sb.append(",\"protocol\":\"").append(escape(protocol)).append('"');
        sb.append(",\"statusCode\":").append(statusCode);
        sb.append(",\"durationMs\":").append(durationMs);
        sb.append(",\"routeId\":\"").append(escape(routeId)).append('"');
        sb.append(",\"userAgent\":\"").append(escape(userAgent)).append('"');
        if (!"-".equals(referer)) {
            sb.append(",\"referer\":\"").append(escape(referer)).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private String escape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
