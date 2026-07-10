package io.github.xinfra.lab.gateway.bootstrap;

import java.util.concurrent.CountDownLatch;

public class GatewayMain {

    private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);

    public static void main(String[] args) {
        String configPath = parseConfigPath(args);

        GatewayServerBootstrap bootstrap = new GatewayServerBootstrap();
        if (configPath != null) {
            bootstrap.configPath(configPath);
        }
        bootstrap.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> SHUTDOWN_LATCH.countDown(),
                "gateway-main-shutdown"));

        try {
            SHUTDOWN_LATCH.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String parseConfigPath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("-c".equals(args[i]) || "--config".equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }
}
