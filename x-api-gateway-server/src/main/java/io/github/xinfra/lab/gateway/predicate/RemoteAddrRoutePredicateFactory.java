package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class RemoteAddrRoutePredicateFactory
        extends AbstractConfigurable<RemoteAddrRoutePredicateFactory.Config>
        implements RoutePredicateFactory<RemoteAddrRoutePredicateFactory.Config> {

    public static final String NAME = "RemoteAddr";

    public RemoteAddrRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public RoutePredicate<ServerWebExchange> apply(Config config) {
        List<CidrRange> ranges = new ArrayList<>();
        if (config.getSources() != null) {
            for (String source : config.getSources()) {
                ranges.add(CidrRange.parse(source));
            }
        }

        return exchange -> {
            InetSocketAddress remoteAddress = exchange.getRequest().remoteAddress();
            if (remoteAddress == null || remoteAddress.getAddress() == null) {
                return false;
            }
            byte[] addr = remoteAddress.getAddress().getAddress();
            for (CidrRange range : ranges) {
                if (range.matches(addr)) {
                    return true;
                }
            }
            return false;
        };
    }

    static class CidrRange {
        final byte[] networkAddress;
        final int prefixLength;

        CidrRange(byte[] networkAddress, int prefixLength) {
            this.networkAddress = networkAddress;
            this.prefixLength = prefixLength;
        }

        static CidrRange parse(String cidr) {
            String[] parts = cidr.split("/");
            try {
                byte[] addr = InetAddress.getByName(parts[0]).getAddress();
                int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : addr.length * 8;
                return new CidrRange(addr, prefix);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr, e);
            }
        }

        boolean matches(byte[] address) {
            if (address.length != networkAddress.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != networkAddress[i]) {
                    return false;
                }
            }
            if (remainingBits > 0 && fullBytes < address.length) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                if ((address[fullBytes] & mask) != (networkAddress[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Data
    public static class Config {
        private List<String> sources;
    }
}
