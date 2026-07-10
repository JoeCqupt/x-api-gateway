package io.github.xinfra.lab.gateway.it;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.xinfra.lab.gateway.bootstrap.GatewayServerBootstrap;
import io.netty.buffer.Unpooled;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import javax.net.ServerSocketFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class GatewayIntegrationTest {

    private DisposableServer upstreamServer;
    private GatewayServerBootstrap gateway;
    private int upstreamPort;
    private int gatewayPort;
    private int adminPort;
    private File configFile;
    private final Gson gson = new Gson();

    @Before
    public void setup() throws IOException {
        upstreamPort = findAvailableTcpPort();
        gatewayPort = findAvailableTcpPort();
        adminPort = findAvailableTcpPort();

        upstreamServer = HttpServer.create()
                .handle((req, res) -> {
                    String path = req.fullPath();
                    String method = req.method().name();
                    JsonObject body = new JsonObject();
                    body.addProperty("path", path);
                    body.addProperty("method", method);
                    body.addProperty("upstream", "mock");
                    String xRequestId = req.requestHeaders().get("X-Request-Id");
                    if (xRequestId != null) {
                        body.addProperty("X-Request-Id", xRequestId);
                    }
                    String customHeader = req.requestHeaders().get("X-Custom");
                    if (customHeader != null) {
                        body.addProperty("X-Custom", customHeader);
                    }
                    return res.sendString(Mono.just(gson.toJson(body)));
                })
                .bindAddress(() -> new InetSocketAddress(upstreamPort))
                .bind().block();

        configFile = File.createTempFile("gateway-it-", ".yml");
        configFile.deleteOnExit();
        writeConfig(configFile);

        gateway = new GatewayServerBootstrap()
                .port(gatewayPort)
                .configPath(configFile.getAbsolutePath())
                .start();
    }

    @After
    public void teardown() {
        if (gateway != null) {
            gateway.stop();
        }
        if (upstreamServer != null) {
            upstreamServer.disposeNow();
        }
        if (configFile != null) {
            configFile.delete();
        }
    }

    @Test
    public void testEndToEndProxy() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + gatewayPort + "/api/test/hello")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        JsonObject json = gson.fromJson(body, JsonObject.class);
        Assert.assertEquals("mock", json.get("upstream").getAsString());
        Assert.assertEquals("/test/hello", json.get("path").getAsString());
    }

    @Test
    public void testAdminHealthEndpoint() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/health")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("\"status\":\"UP\""));
    }

    @Test
    public void testAdminRoutesListWithAuth() {
        String body = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer integration-test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("it-proxy-route"));
    }

    @Test
    public void testAdminUnauthorized() {
        int status = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(401, status);
    }

    @Test
    public void testAdminAddAndDeleteRoute() {
        JsonObject routeJson = new JsonObject();
        routeJson.addProperty("id", "dynamic-route");
        routeJson.addProperty("order", 1);
        JsonArray predicates = new JsonArray();
        JsonObject pathPred = new JsonObject();
        pathPred.addProperty("name", "Path");
        pathPred.addProperty("config", "pattern: /dynamic/.*");
        predicates.add(pathPred);
        routeJson.add("predicates", predicates);
        JsonObject endpoint = new JsonObject();
        endpoint.addProperty("name", "Http");
        endpoint.addProperty("config", "urls:\n  - http://127.0.0.1:" + upstreamPort);
        routeJson.add("endpoint", endpoint);

        String addBody = gson.toJson(routeJson);

        String addResponse = HttpClient.create()
                .headers(h -> {
                    h.add("Authorization", "Bearer integration-test-token");
                    h.add("Content-Type", "application/json");
                })
                .post()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .send(Mono.just(Unpooled.wrappedBuffer(addBody.getBytes(StandardCharsets.UTF_8))))
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(addResponse);
        Assert.assertTrue("Add route should succeed: " + addResponse,
                addResponse.contains("\"message\":\"Route added\""));

        String routesBody = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer integration-test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .responseContent()
                .aggregate()
                .asString()
                .block();
        Assert.assertTrue(routesBody.contains("dynamic-route"));

        int deleteStatus = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer integration-test-token"))
                .delete()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/dynamic-route")
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(200, deleteStatus);
    }

    @Test
    public void testAdminInfoEndpoint() {
        String body = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer integration-test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/info")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("x-api-gateway"));
    }

    @Test
    public void testGracefulShutdown() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + gatewayPort + "/api/test/before-shutdown")
                .responseContent()
                .aggregate()
                .asString()
                .block();
        Assert.assertNotNull(body);

        gateway.stop();
        gateway = null;

        try {
            HttpClient.create()
                    .get()
                    .uri("http://127.0.0.1:" + gatewayPort + "/api/test/after-shutdown")
                    .responseContent()
                    .aggregate()
                    .asString()
                    .block();
            Assert.fail("Should have thrown exception after shutdown");
        } catch (Exception e) {
            // connection refused — expected
        }
    }

    private void writeConfig(File file) throws IOException {
        String config = "server:\n" +
                "  port: " + gatewayPort + "\n" +
                "  shutdownTimeoutMs: 5000\n" +
                "\n" +
                "admin:\n" +
                "  enabled: true\n" +
                "  port: " + adminPort + "\n" +
                "  accessToken: integration-test-token\n" +
                "\n" +
                "healthCheck:\n" +
                "  enabled: false\n" +
                "\n" +
                "routes:\n" +
                "  - id: it-proxy-route\n" +
                "    order: 1\n" +
                "    predicates:\n" +
                "      - name: Path\n" +
                "        config: \"pattern: /api/.*\"\n" +
                "    filters:\n" +
                "      - name: StripPrefix\n" +
                "        config: \"parts: 1\"\n" +
                "    endpoint:\n" +
                "      name: Http\n" +
                "      config: |\n" +
                "        urls:\n" +
                "          - http://127.0.0.1:" + upstreamPort + "\n";

        FileWriter writer = new FileWriter(file);
        writer.write(config);
        writer.close();
    }

    private static int findAvailableTcpPort() {
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            int port = 10000 + random.nextInt(55000);
            try {
                ServerSocket ss = ServerSocketFactory.getDefault()
                        .createServerSocket(port, 1, InetAddress.getByName("localhost"));
                ss.close();
                return port;
            } catch (Exception e) {
                // try next
            }
        }
        throw new IllegalStateException("Could not find available TCP port");
    }
}
