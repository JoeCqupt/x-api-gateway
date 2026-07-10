package io.github.xinfra.lab.gateway.handler;

import io.github.xinfra.lab.gateway.commons.GatewayHeaders;
import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.isResponseCommitted;
import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.markResponseCommitted;

@Slf4j
public class DefaultWebExceptionHandler implements WebExceptionHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable t) {
        if (isResponseCommitted(exchange)) {
            return Mono.empty();
        }

        ErrorCode errorCode;
        HttpResponseStatus status;

        if (t instanceof ErrorResponseException) {
            ErrorResponseException ex = (ErrorResponseException) t;
            status = ex.getStatus();
            errorCode = ex.getErrorCode();
            if (ex.getHeaders() != null) {
                ex.getHeaders().forEach(entry ->
                        exchange.getResponse().addHeader(entry.getKey(), entry.getValue()));
            }
        } else if (isTimeoutException(t)) {
            errorCode = ErrorCode.GATEWAY_TIMEOUT;
            status = HttpResponseStatus.GATEWAY_TIMEOUT;
            log.warn("Upstream timeout: {}", t.getMessage());
        } else if (isConnectException(t)) {
            errorCode = ErrorCode.BAD_GATEWAY;
            status = HttpResponseStatus.BAD_GATEWAY;
            log.warn("Upstream connect failed: {}", t.getMessage());
        } else {
            errorCode = ErrorCode.SYSTEM_EXCEPTION;
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            log.error("Unhandled exception", t);
        }

        String requestId = exchange.getAttribute("gatewayRequestId");
        String path = exchange.getRequest().path();

        String jsonBody = buildJsonError(status, errorCode, path, requestId);

        exchange.getResponse().status(status);
        exchange.getResponse().addHeader("Content-Type", "application/json;charset=UTF-8");
        exchange.getResponse().addHeader(GatewayHeaders.Names.X_GATEWAY_ERROR_CODE,
                String.valueOf(errorCode.getCode()));
        exchange.getResponse().addHeader(GatewayHeaders.Names.X_GATEWAY_ERROR_MSG,
                errorCode.getMsg());

        markResponseCommitted(exchange);

        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().send(
                Mono.just(Unpooled.wrappedBuffer(bodyBytes))
        ).then();
    }

    private String buildJsonError(HttpResponseStatus status, ErrorCode errorCode,
                                  String path, String requestId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"code\":").append(errorCode.getCode()).append(",");
        sb.append("\"message\":\"").append(escapeJson(errorCode.getMsg())).append("\",");
        sb.append("\"status\":").append(status.code()).append(",");
        sb.append("\"path\":\"").append(escapeJson(path != null ? path : "")).append("\",");
        sb.append("\"timestamp\":\"").append(Instant.now().toString()).append("\"");
        if (requestId != null) {
            sb.append(",\"requestId\":\"").append(escapeJson(requestId)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private boolean isTimeoutException(Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof TimeoutException) return true;
            String name = cause.getClass().getSimpleName();
            if (name.contains("TimeoutException") || name.contains("ReadTimeout")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isConnectException(Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof ConnectException) return true;
            String name = cause.getClass().getSimpleName();
            if (name.contains("ConnectException") || name.contains("ConnectTimeout")) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
