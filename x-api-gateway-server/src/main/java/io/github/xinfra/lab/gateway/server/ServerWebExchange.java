package io.github.xinfra.lab.gateway.server;

import io.github.xinfra.lab.gateway.commons.Assert;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.util.HashMap;
import java.util.Map;

public class ServerWebExchange {
    private HttpServerRequest request;
    private HttpServerResponse response;
    private Map<String, Object> attributes = new HashMap<>();
    private volatile GatewayRequest gatewayRequest;
    private volatile GatewayResponse gatewayResponse;

    public ServerWebExchange(HttpServerRequest request, HttpServerResponse response) {
        this.request = request;
        this.response = response;
    }

    public HttpServerRequest getRequest() {
        return request;
    }

    public HttpServerResponse getResponse() {
        return response;
    }

    public GatewayRequest getGatewayRequest() {
        if (gatewayRequest == null && request != null) {
            gatewayRequest = new GatewayRequest(request);
        }
        return gatewayRequest;
    }

    public GatewayResponse getGatewayResponse() {
        if (gatewayResponse == null && response != null) {
            gatewayResponse = new GatewayResponse(response);
        }
        return gatewayResponse;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }


    public <T> T getRequiredAttribute(String name) {
        T t = getAttribute(name);
        Assert.notNull(t, "Required attribute '" + name + "' is missing");
        return t;
    }

    public <T> T getAttributeOrDefault(String name, T defaultValue) {
        T t = getAttribute(name);
        if (t == null)
            t = defaultValue;
        return t;
    }

    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    public Builder mutate() {
        return new Builder(this);
    }

    public static class Builder {
        private HttpServerRequest request;
        private HttpServerResponse response;
        private Map<String, Object> attributes;

        private Builder(ServerWebExchange exchange) {
            this.request = exchange.request;
            this.response = exchange.response;
            this.attributes = exchange.attributes;
        }

        public Builder request(HttpServerRequest request) {
            this.request = request;
            return this;
        }

        public Builder response(HttpServerResponse response) {
            this.response = response;
            return this;
        }

        public ServerWebExchange build() {
            ServerWebExchange exchange = new ServerWebExchange(request, response);
            exchange.attributes.putAll(this.attributes);
            return exchange;
        }
    }
}
