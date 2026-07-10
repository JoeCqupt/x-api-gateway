package io.github.xinfra.lab.gateway.filter;

import org.junit.Assert;
import org.junit.Test;

public class BodyRewriteFilterTest {

    @Test
    public void testRequestBodyAddField() {
        RequestBodyRewriteGatewayFilterFactory.Config config = new RequestBodyRewriteGatewayFilterFactory.Config();
        config.setRewriteType("addField");
        config.setFieldName("traceId");
        config.setFieldValue("abc123");

        String result = RequestBodyRewriteGatewayFilterFactory.rewriteBody(
                "{\"name\":\"test\"}", config);
        Assert.assertTrue(result.contains("\"traceId\":\"abc123\""));
        Assert.assertTrue(result.contains("\"name\":\"test\""));
    }

    @Test
    public void testRequestBodyAddFieldToEmpty() {
        RequestBodyRewriteGatewayFilterFactory.Config config = new RequestBodyRewriteGatewayFilterFactory.Config();
        config.setRewriteType("addField");
        config.setFieldName("key");
        config.setFieldValue("value");

        String result = RequestBodyRewriteGatewayFilterFactory.rewriteBody("{}", config);
        Assert.assertEquals("{\"key\":\"value\"}", result);
    }

    @Test
    public void testRequestBodyRemoveField() {
        RequestBodyRewriteGatewayFilterFactory.Config config = new RequestBodyRewriteGatewayFilterFactory.Config();
        config.setRewriteType("removeField");
        config.setFieldName("secret");

        String result = RequestBodyRewriteGatewayFilterFactory.rewriteBody(
                "{\"name\":\"test\",\"secret\":\"hidden\"}", config);
        Assert.assertFalse(result.contains("secret"));
        Assert.assertTrue(result.contains("name"));
    }

    @Test
    public void testRequestBodyReplace() {
        RequestBodyRewriteGatewayFilterFactory.Config config = new RequestBodyRewriteGatewayFilterFactory.Config();
        config.setRewriteType("replace");
        config.setRegex("oldValue");
        config.setReplacement("newValue");

        String result = RequestBodyRewriteGatewayFilterFactory.rewriteBody(
                "{\"key\":\"oldValue\"}", config);
        Assert.assertEquals("{\"key\":\"newValue\"}", result);
    }

    @Test
    public void testRequestBodyUnknownType() {
        RequestBodyRewriteGatewayFilterFactory.Config config = new RequestBodyRewriteGatewayFilterFactory.Config();
        config.setRewriteType("unknown");

        String body = "{\"key\":\"value\"}";
        String result = RequestBodyRewriteGatewayFilterFactory.rewriteBody(body, config);
        Assert.assertEquals(body, result);
    }

    @Test
    public void testResponseBodyAddField() {
        ResponseBodyRewriteGatewayFilterFactory.Config config = new ResponseBodyRewriteGatewayFilterFactory.Config();
        config.setRewriteType("addField");
        config.setFieldName("gateway");
        config.setFieldValue("processed");

        String result = ResponseBodyRewriteGatewayFilterFactory.rewriteBody(
                "{\"data\":\"hello\"}", config);
        Assert.assertTrue(result.contains("\"gateway\":\"processed\""));
    }

    @Test
    public void testResponseBodyRemoveField() {
        ResponseBodyRewriteGatewayFilterFactory.Config config = new ResponseBodyRewriteGatewayFilterFactory.Config();
        config.setRewriteType("removeField");
        config.setFieldName("internal");

        String result = ResponseBodyRewriteGatewayFilterFactory.rewriteBody(
                "{\"data\":\"hello\",\"internal\":\"debug\"}", config);
        Assert.assertFalse(result.contains("internal"));
        Assert.assertTrue(result.contains("data"));
    }

    @Test
    public void testResponseBodyReplace() {
        ResponseBodyRewriteGatewayFilterFactory.Config config = new ResponseBodyRewriteGatewayFilterFactory.Config();
        config.setRewriteType("replace");
        config.setRegex("\\d{4}-\\d{4}-\\d{4}-\\d{4}");
        config.setReplacement("****-****-****-****");

        String result = ResponseBodyRewriteGatewayFilterFactory.rewriteBody(
                "{\"card\":\"1234-5678-9012-3456\"}", config);
        Assert.assertEquals("{\"card\":\"****-****-****-****\"}", result);
    }

    @Test
    public void testRequestBodyFactoryName() {
        RequestBodyRewriteGatewayFilterFactory factory = new RequestBodyRewriteGatewayFilterFactory();
        Assert.assertEquals("RequestBodyRewrite", factory.getName());
    }

    @Test
    public void testResponseBodyFactoryName() {
        ResponseBodyRewriteGatewayFilterFactory factory = new ResponseBodyRewriteGatewayFilterFactory();
        Assert.assertEquals("ResponseBodyRewrite", factory.getName());
    }
}
