package io.github.xinfra.lab.gateway.handler;

import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

public class AccessLogWebHandler extends WebHandlerDecorator {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("gateway.access.handler");
    private static final String START_TIME_ATTR = "gatewayAccessLogStartTime";

    public AccessLogWebHandler(WebHandler delegate) {
        super(delegate);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange) {
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        return super.handle(exchange)
                .doFinally(signalType -> {
                    if (!ACCESS_LOG.isInfoEnabled()) return;

                    Long startTime = exchange.getAttribute(START_TIME_ATTR);
                    if (startTime == null) return;

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

                    ACCESS_LOG.info(buildJsonLog(requestId, clientIp, method, path,
                            statusCode, duration, routeId));
                });
    }

    private String buildJsonLog(String requestId, String clientIp, String method,
                                String path, int statusCode, long durationMs,
                                String routeId) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"requestId\":\"").append(escape(requestId)).append('"');
        sb.append(",\"timestamp\":\"").append(Instant.now().toString()).append('"');
        sb.append(",\"clientIp\":\"").append(escape(clientIp)).append('"');
        sb.append(",\"method\":\"").append(method).append('"');
        sb.append(",\"path\":\"").append(escape(path)).append('"');
        sb.append(",\"statusCode\":").append(statusCode);
        sb.append(",\"durationMs\":").append(durationMs);
        sb.append(",\"routeId\":\"").append(escape(routeId)).append('"');
        sb.append('}');
        return sb.toString();
    }

    private String escape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
