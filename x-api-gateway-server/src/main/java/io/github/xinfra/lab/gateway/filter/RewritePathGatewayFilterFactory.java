package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import lombok.Data;

import java.util.regex.Pattern;

@Order(-40)
public class RewritePathGatewayFilterFactory
        extends AbstractConfigurable<RewritePathGatewayFilterFactory.Config>
        implements GatewayFilterFactory<RewritePathGatewayFilterFactory.Config> {

    public static final String NAME = "RewritePath";

    public RewritePathGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        Pattern pattern = Pattern.compile(config.getRegexp());
        String replacement = config.getReplacement().replace("$\\", "$");

        return (exchange, chain) -> {
            String path = exchange.getRequest().fullPath();
            String newPath = pattern.matcher(path).replaceAll(replacement);
            exchange.getAttributes().put("gatewayOriginalPath", path);
            exchange.getAttributes().put("gatewayRewrittenPath", newPath);
            return chain.filter(exchange);
        };
    }

    @Data
    public static class Config {
        private String regexp;
        private String replacement;
    }
}
