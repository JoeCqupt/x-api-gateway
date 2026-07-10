package io.github.xinfra.lab.gateway.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // 4xxxx mean client error
    ROUTE_NO_MATCH(40001, "Route no match"),
    UNAUTHORIZED(40101, "Unauthorized"),
    FORBIDDEN(40301, "Forbidden"),
    REQUEST_TOO_LARGE(41301, "Request entity too large"),
    RATE_LIMITED(42901, "Rate limit exceeded"),

    // 5xxxx mean server error
    SYSTEM_EXCEPTION(50001, "System exception"),
    BAD_GATEWAY(50201, "Bad gateway"),
    SERVICE_UNAVAILABLE(50301, "Service unavailable"),
    CIRCUIT_BREAKER_OPEN(50302, "Circuit breaker is open"),
    GATEWAY_TIMEOUT(50401, "Gateway timeout"),
    ;
    int code;
    String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
