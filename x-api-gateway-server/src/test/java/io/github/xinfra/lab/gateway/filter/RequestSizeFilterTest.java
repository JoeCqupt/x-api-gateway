package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestSizeFilterTest {

    private ServerWebExchange mockExchange(String contentLength) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        if (contentLength != null) {
            headers.set("Content-Length", contentLength);
        }
        when(request.requestHeaders()).thenReturn(headers);
        return new ServerWebExchange(request, null);
    }

    @Test
    public void testAllowsRequestWithinLimit() {
        RequestSizeGatewayFilterFactory factory = new RequestSizeGatewayFilterFactory();
        RequestSizeGatewayFilterFactory.Config config = new RequestSizeGatewayFilterFactory.Config();
        config.setMaxSize(1024);
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("512");
        filter.filter(exchange, ex -> Mono.empty()).block();
    }

    @Test
    public void testRejectsOversizedRequest() {
        RequestSizeGatewayFilterFactory factory = new RequestSizeGatewayFilterFactory();
        RequestSizeGatewayFilterFactory.Config config = new RequestSizeGatewayFilterFactory.Config();
        config.setMaxSize(1024);
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("2048");
        try {
            filter.filter(exchange, ex -> Mono.empty()).block();
            Assert.fail("Should have thrown");
        } catch (Exception e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof ErrorResponseException) break;
                cause = cause.getCause();
            }
            Assert.assertNotNull("Expected ErrorResponseException", cause);
            Assert.assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE,
                    ((ErrorResponseException) cause).getStatus());
        }
    }

    @Test
    public void testAllowsRequestWithNoContentLength() {
        RequestSizeGatewayFilterFactory factory = new RequestSizeGatewayFilterFactory();
        RequestSizeGatewayFilterFactory.Config config = new RequestSizeGatewayFilterFactory.Config();
        config.setMaxSize(1024);
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange(null);
        filter.filter(exchange, ex -> Mono.empty()).block();
    }

    @Test
    public void testHandlesInvalidContentLength() {
        RequestSizeGatewayFilterFactory factory = new RequestSizeGatewayFilterFactory();
        RequestSizeGatewayFilterFactory.Config config = new RequestSizeGatewayFilterFactory.Config();
        config.setMaxSize(1024);
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("not-a-number");
        filter.filter(exchange, ex -> Mono.empty()).block();
    }

    @Test
    public void testDefaultMaxSize() {
        RequestSizeGatewayFilterFactory.Config config = new RequestSizeGatewayFilterFactory.Config();
        Assert.assertEquals(10 * 1024 * 1024, config.getMaxSize());
    }

    @Test
    public void testFactoryName() {
        Assert.assertEquals("RequestSize", new RequestSizeGatewayFilterFactory().getName());
    }
}
