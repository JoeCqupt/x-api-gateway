package io.github.xinfra.lab.gateway.commons;

public interface GatewayHeaders {

    interface Names {
        String X_GATEWAY_ERROR_CODE = "x-gateway-error-code";
        String X_GATEWAY_ERROR_MSG = "x-gateway-error-msg";
        String X_REQUEST_ID = "X-Request-Id";
        String X_FORWARDED_FOR = "X-Forwarded-For";
        String X_REAL_IP = "X-Real-IP";
        String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    }
}
