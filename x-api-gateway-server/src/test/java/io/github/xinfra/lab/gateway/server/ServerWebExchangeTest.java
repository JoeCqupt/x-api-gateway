package io.github.xinfra.lab.gateway.server;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Assert;
import org.junit.Test;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class ServerWebExchangeTest {

    @Test
    public void testMutatePreservesAttributes() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        HttpServerResponse response = mock(HttpServerResponse.class);

        ServerWebExchange original = new ServerWebExchange(request, response);
        original.getAttributes().put("key1", "value1");

        ServerWebExchange mutated = original.mutate().build();

        Assert.assertEquals("value1", mutated.getAttribute("key1"));
        Assert.assertSame(request, mutated.getRequest());
        Assert.assertSame(response, mutated.getResponse());
    }

    @Test
    public void testMutateReplacesRequest() {
        HttpServerRequest request1 = mock(HttpServerRequest.class);
        HttpServerRequest request2 = mock(HttpServerRequest.class);
        HttpServerResponse response = mock(HttpServerResponse.class);

        ServerWebExchange original = new ServerWebExchange(request1, response);
        ServerWebExchange mutated = original.mutate().request(request2).build();

        Assert.assertSame(request2, mutated.getRequest());
        Assert.assertSame(response, mutated.getResponse());
    }

    @Test
    public void testMutateReplacesResponse() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        HttpServerResponse response1 = mock(HttpServerResponse.class);
        HttpServerResponse response2 = mock(HttpServerResponse.class);

        ServerWebExchange original = new ServerWebExchange(request, response1);
        ServerWebExchange mutated = original.mutate().response(response2).build();

        Assert.assertSame(request, mutated.getRequest());
        Assert.assertSame(response2, mutated.getResponse());
    }

    @Test
    public void testGatewayRequestWrapper() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("X-Test", "value");
        when(request.requestHeaders()).thenReturn(headers);
        when(request.path()).thenReturn("/api/test");
        when(request.method()).thenReturn(HttpMethod.GET);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        GatewayRequest gr = exchange.getGatewayRequest();

        Assert.assertNotNull(gr);
        Assert.assertEquals("/api/test", gr.path());
        Assert.assertEquals(HttpMethod.GET, gr.method());
        Assert.assertEquals("value", gr.headers().get("X-Test"));
        Assert.assertSame(request, gr.unwrap());
    }

    @Test
    public void testGatewayResponseWrapper() {
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.responseHeaders()).thenReturn(new DefaultHttpHeaders());

        ServerWebExchange exchange = new ServerWebExchange(null, response);
        GatewayResponse gr = exchange.getGatewayResponse();

        Assert.assertNotNull(gr);
        Assert.assertSame(response, gr.unwrap());
    }

    @Test
    public void testGatewayRequestCachedInstance() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        ServerWebExchange exchange = new ServerWebExchange(request, null);

        GatewayRequest gr1 = exchange.getGatewayRequest();
        GatewayRequest gr2 = exchange.getGatewayRequest();
        Assert.assertSame(gr1, gr2);
    }

    @Test
    public void testGatewayRequestQueryParams() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.uri()).thenReturn("/api/test?name=alice&age=30&name=bob");

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        GatewayRequest gr = exchange.getGatewayRequest();

        Map<String, List<String>> params = gr.queryParams();
        Assert.assertNotNull(params);
        Assert.assertEquals(2, params.get("name").size());
        Assert.assertEquals("alice", params.get("name").get(0));
        Assert.assertEquals("bob", params.get("name").get(1));
        Assert.assertEquals("30", params.get("age").get(0));
    }

    @Test
    public void testGatewayRequestQueryParamsEmpty() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.uri()).thenReturn(null);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        GatewayRequest gr = exchange.getGatewayRequest();

        Assert.assertTrue(gr.queryParams().isEmpty());
    }

    @Test
    public void testGatewayRequestCookies() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("Cookie", "session=abc123; theme=dark");
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        GatewayRequest gr = exchange.getGatewayRequest();

        Map<String, String> cookies = gr.cookies();
        Assert.assertEquals("abc123", cookies.get("session"));
        Assert.assertEquals("dark", cookies.get("theme"));
    }

    @Test
    public void testGatewayRequestCookiesEmpty() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        GatewayRequest gr = exchange.getGatewayRequest();

        Assert.assertTrue(gr.cookies().isEmpty());
    }

    @Test
    public void testGatewayResponseIsCommitted() {
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.responseHeaders()).thenReturn(new DefaultHttpHeaders());

        ServerWebExchange exchange = new ServerWebExchange(null, response);
        GatewayResponse gr = exchange.getGatewayResponse();

        Assert.assertFalse(gr.isCommitted());
    }

    @Test
    public void testGatewayResponseStatus() {
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.status()).thenReturn(HttpResponseStatus.OK);

        ServerWebExchange exchange = new ServerWebExchange(null, response);
        GatewayResponse gr = exchange.getGatewayResponse();

        Assert.assertEquals(HttpResponseStatus.OK, gr.status());
    }
}
