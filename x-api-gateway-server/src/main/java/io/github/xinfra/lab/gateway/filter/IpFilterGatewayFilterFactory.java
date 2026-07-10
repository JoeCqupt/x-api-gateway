package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Order(-400)
public class IpFilterGatewayFilterFactory
        extends AbstractConfigurable<IpFilterGatewayFilterFactory.Config>
        implements GatewayFilterFactory<IpFilterGatewayFilterFactory.Config> {

    public static final String NAME = "IpFilter";

    public IpFilterGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        if (config == null) {
            config = new Config();
        }
        AtomicReference<Config> configRef = new AtomicReference<>(config);

        GatewayFilter inner = (exchange, chain) -> {
            Config ipConfig = configRef.get();
            InetSocketAddress remoteAddr = exchange.getRequest().remoteAddress();
            if (remoteAddr == null) {
                return chain.filter(exchange);
            }
            String clientIp = remoteAddr.getAddress().getHostAddress();

            if (ipConfig.getWhitelist() != null && !ipConfig.getWhitelist().isEmpty()) {
                if (!matchesAny(clientIp, ipConfig.getWhitelist())) {
                    return Mono.error(new ErrorResponseException(
                            HttpResponseStatus.FORBIDDEN, ErrorCode.FORBIDDEN));
                }
            }

            if (ipConfig.getBlacklist() != null && !ipConfig.getBlacklist().isEmpty()) {
                if (matchesAny(clientIp, ipConfig.getBlacklist())) {
                    return Mono.error(new ErrorResponseException(
                            HttpResponseStatus.FORBIDDEN, ErrorCode.FORBIDDEN));
                }
            }

            return chain.filter(exchange);
        };

        return new ConfigurableGatewayFilter<>(configRef, inner, -400, NAME);
    }

    static boolean matchesAny(String clientIp, List<String> rules) {
        for (String rule : rules) {
            if (matchesCidr(clientIp, rule)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesCidr(String clientIp, String cidr) {
        try {
            if (!cidr.contains("/")) {
                return clientIp.equals(cidr);
            }

            String[] parts = cidr.split("/");
            InetAddress network = InetAddress.getByName(parts[0]);
            int prefixLen = Integer.parseInt(parts[1]);

            byte[] networkBytes = network.getAddress();
            byte[] clientBytes = InetAddress.getByName(clientIp).getAddress();

            if (networkBytes.length != clientBytes.length) {
                return false;
            }

            int fullBytes = prefixLen / 8;
            int remainingBits = prefixLen % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != clientBytes[i]) {
                    return false;
                }
            }

            if (remainingBits > 0 && fullBytes < networkBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((networkBytes[fullBytes] & mask) != (clientBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @Data
    public static class Config {
        private List<String> whitelist = Collections.emptyList();
        private List<String> blacklist = Collections.emptyList();
    }
}
