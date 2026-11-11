package de.chriswohlbrecht.maintenance.cucumber.support;

import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ScenarioScope
public class ScenarioContext {

    private static final Pattern ALIAS_PATTERN = Pattern.compile("\\{(\\w+)}");
    /** Dates relative to today, e.g. {today}, {today-20d}, {today-13m}, for checks that depend on the date. */
    private static final Pattern DATE_PATTERN = Pattern.compile("\\{today(?:([+-]\\d+)([dm]))?}");

    private final Map<String, Long> ids = new HashMap<>();
    private final Map<String, String> texts = new HashMap<>();

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

    /** A response body kept for a later step, e.g. an export file that is imported again. */
    public void storeText(String alias, String text) {
        texts.put(alias, text);
    }

    public String resolveText(String alias) {
        String text = texts.get(alias);
        if (text == null) {
            throw new IllegalStateException("No text stored for alias \"" + alias + "\"");
        }
        return text;
    }

    public String resolvePath(String template) {
        Matcher matcher = ALIAS_PATTERN.matcher(resolveDates(template));
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, String.valueOf(resolveId(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolveDates(String template) {
        Matcher matcher = DATE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            LocalDate date = LocalDate.now();
            if (matcher.group(1) != null) {
                int amount = Integer.parseInt(matcher.group(1));
                date = "d".equals(matcher.group(2)) ? date.plusDays(amount) : date.plusMonths(amount);
            }
            matcher.appendReplacement(result, date.toString());
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
