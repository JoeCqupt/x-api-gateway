package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Order(-300)
public class SecureHeadersGatewayFilterFactory
        extends AbstractConfigurable<SecureHeadersGatewayFilterFactory.Config>
        implements GatewayFilterFactory<SecureHeadersGatewayFilterFactory.Config> {

    public static final String NAME = "SecureHeaders";

    public SecureHeadersGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        boolean setForwarded = config == null || config.isSetForwardedHeaders();
        boolean addSecurity = config == null || config.isAddSecurityHeaders();

        return (exchange, chain) -> {
            if (setForwarded) {
                exchange.getRequest().requestHeaders().remove("X-Internal-Token");

                InetSocketAddress remoteAddress = exchange.getRequest().remoteAddress();
                if (remoteAddress != null && remoteAddress.getAddress() != null) {
                    String clientIp = remoteAddress.getAddress().getHostAddress();
                    exchange.getRequest().requestHeaders().set("X-Real-IP", clientIp);

                    String existing = exchange.getRequest().requestHeaders().get("X-Forwarded-For");
                    if (existing != null && !existing.isEmpty()) {
                        exchange.getRequest().requestHeaders().set("X-Forwarded-For",
                                existing + ", " + clientIp);
                    } else {
                        exchange.getRequest().requestHeaders().set("X-Forwarded-For", clientIp);
                    }
                }
            }

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                if (addSecurity) {
                    exchange.getResponse().addHeader("X-Content-Type-Options", "nosniff");
                    exchange.getResponse().addHeader("X-Frame-Options", "DENY");
                    exchange.getResponse().addHeader("X-XSS-Protection", "1; mode=block");
                    exchange.getResponse().responseHeaders().remove("Server");
                    exchange.getResponse().responseHeaders().remove("X-Powered-By");
                }
            }));
        };
    }

    @Data
    public static class Config {
        private boolean addSecurityHeaders = true;
        private boolean setForwardedHeaders = true;
    }
}
