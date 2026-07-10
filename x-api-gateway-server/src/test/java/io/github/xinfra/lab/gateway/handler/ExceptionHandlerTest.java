package io.github.xinfra.lab.gateway.handler;

import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ExceptionHandlerTest {

    private ServerWebExchange mockExchange() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.path()).thenReturn("/test");
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());

        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.status(any(HttpResponseStatus.class))).thenReturn(response);
        when(response.addHeader(anyString(), anyString())).thenReturn(response);
        reactor.netty.NettyOutbound outbound = mock(reactor.netty.NettyOutbound.class);
        when(outbound.then()).thenReturn(Mono.empty());
        when(response.send(any(Mono.class))).thenReturn(outbound);

        return new ServerWebExchange(request, response);
    }

    @Test
    public void testTimeoutExceptionMapsTo504() {
        DefaultWebExceptionHandler handler = new DefaultWebExceptionHandler();
        ServerWebExchange exchange = mockExchange();

        handler.handle(exchange, new TimeoutException("Read timed out")).block();

        verify(exchange.getResponse()).status(HttpResponseStatus.GATEWAY_TIMEOUT);
    }

    @Test
    public void testWrappedTimeoutExceptionMapsTo504() {
        DefaultWebExceptionHandler handler = new DefaultWebExceptionHandler();
        ServerWebExchange exchange = mockExchange();

        RuntimeException wrapped = new RuntimeException("upstream error",
                new TimeoutException("Connection timed out"));
        handler.handle(exchange, wrapped).block();

        verify(exchange.getResponse()).status(HttpResponseStatus.GATEWAY_TIMEOUT);
    }

    @Test
    public void testConnectExceptionMapsTo502() {
        DefaultWebExceptionHandler handler = new DefaultWebExceptionHandler();
        ServerWebExchange exchange = mockExchange();

        handler.handle(exchange, new ConnectException("Connection refused")).block();

        verify(exchange.getResponse()).status(HttpResponseStatus.BAD_GATEWAY);
    }

    @Test
    public void testErrorResponseExceptionPreserved() {
        DefaultWebExceptionHandler handler = new DefaultWebExceptionHandler();
        ServerWebExchange exchange = mockExchange();

        ErrorResponseException ex = new ErrorResponseException(
                HttpResponseStatus.NOT_FOUND, ErrorCode.ROUTE_NO_MATCH);
        handler.handle(exchange, ex).block();

        verify(exchange.getResponse()).status(HttpResponseStatus.NOT_FOUND);
    }

    @Test
    public void testUnknownExceptionMapsTo500() {
        DefaultWebExceptionHandler handler = new DefaultWebExceptionHandler();
        ServerWebExchange exchange = mockExchange();

        handler.handle(exchange, new IllegalStateException("unexpected")).block();

        verify(exchange.getResponse()).status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testSkipsCommittedResponse() {
        DefaultWebExceptionHandler handler = new DefaultWebExceptionHandler();
        ServerWebExchange exchange = mockExchange();
        exchange.getAttributes().put("responseCommitted", true);

        Mono<Void> result = handler.handle(exchange, new RuntimeException("error"));
        result.block();

        verify(exchange.getResponse(), never()).status(any(HttpResponseStatus.class));
    }
}
