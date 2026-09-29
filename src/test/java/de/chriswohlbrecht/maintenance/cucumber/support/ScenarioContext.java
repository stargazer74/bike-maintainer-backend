package de.chriswohlbrecht.maintenance.cucumber.support;

import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ScenarioScope
public class ScenarioContext {

    private static final Pattern ALIAS_PATTERN = Pattern.compile("\\{(\\w+)}");

    private final Map<String, Long> ids = new HashMap<>();

    private EntityExchangeResult<byte[]> lastResponse;

    public void storeId(String alias, Long id) {
        ids.put(alias, id);
    }

    public Long resolveId(String alias) {
        Long id = ids.get(alias);
        if (id == null) {
            throw new IllegalStateException("No id stored for alias \"" + alias + "\"");
        }
        return id;
    }

    public String resolvePath(String template) {
        Matcher matcher = ALIAS_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, String.valueOf(resolveId(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public EntityExchangeResult<byte[]> getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(EntityExchangeResult<byte[]> lastResponse) {
        this.lastResponse = lastResponse;
    }

    public String getLastResponseBodyAsString() {
        byte[] body = lastResponse.getResponseBody();
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
}
