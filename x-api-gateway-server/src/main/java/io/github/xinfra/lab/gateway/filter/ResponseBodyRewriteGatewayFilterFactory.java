package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import lombok.Data;

import java.util.regex.Pattern;

@Order(520)
public class ResponseBodyRewriteGatewayFilterFactory
        extends AbstractConfigurable<ResponseBodyRewriteGatewayFilterFactory.Config>
        implements GatewayFilterFactory<ResponseBodyRewriteGatewayFilterFactory.Config> {

    public static final String NAME = "ResponseBodyRewrite";

    public ResponseBodyRewriteGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            exchange.getAttributes().put("gatewayResponseRewriteConfig", config);
            return chain.filter(exchange);
        };
    }

    public static String rewriteBody(String body, Config config) {
        String type = config.getRewriteType();
        if ("addField".equals(type)) {
            return addJsonField(body, config.getFieldName(), config.getFieldValue());
        } else if ("removeField".equals(type)) {
            return removeJsonField(body, config.getFieldName());
        } else if ("replace".equals(type)) {
            return replaceContent(body, config.getRegex(), config.getReplacement());
        }
        return body;
    }

    private static String addJsonField(String json, String fieldName, String fieldValue) {
        if (json == null || json.isEmpty() || "{}".equals(json.trim())) {
            return "{\"" + fieldName + "\":\"" + fieldValue + "\"}";
        }
        String trimmed = json.trim();
        if (trimmed.endsWith("}")) {
            String insertion = "\"" + fieldName + "\":\"" + fieldValue + "\"";
            int lastBrace = trimmed.lastIndexOf('}');
            String before = trimmed.substring(0, lastBrace).trim();
            if (before.endsWith("{")) {
                return before + insertion + "}";
            }
            return before + "," + insertion + "}";
        }
        return json;
    }

    private static String removeJsonField(String json, String fieldName) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String pattern = ",?\\s*\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"[^\"]*\"\\s*,?";
        String result = json.replaceAll(pattern, "");
        result = result.replaceAll("\\{\\s*,", "{");
        result = result.replaceAll(",\\s*}", "}");
        return result;
    }

    private static String replaceContent(String body, String regex, String replacement) {
        if (regex == null || replacement == null) {
            return body;
        }
        return Pattern.compile(regex).matcher(body).replaceAll(replacement);
    }

    @Data
    public static class Config {
        private String rewriteType;
        private String fieldName;
        private String fieldValue;
        private String regex;
        private String replacement;
    }
}
