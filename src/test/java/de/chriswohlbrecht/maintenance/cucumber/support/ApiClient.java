package de.chriswohlbrecht.maintenance.cucumber.support;

import io.cucumber.spring.ScenarioScope;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * HTTP client that behaves like the browser SPA: keeps cookies (session, XSRF-TOKEN) between requests,
 * echoes the CSRF token as X-XSRF-TOKEN on state-changing requests, and sends a client IP via
 * X-Forwarded-For (trusted because the test server sees requests from localhost, an internal proxy).
 *
 * <p>Scenario-scoped, so every scenario starts logged out, with no cookies and its own client IP — the
 * per-IP rate limit therefore does not leak between scenarios.
 */
@Component
@ScenarioScope
public class ApiClient {

    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final String[] TEST_NETS = {"203.0.113.", "198.51.100.", "192.0.2."};
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    private final RestTestClient restTestClient;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private String clientIp = nextTestNetIp();

    public ApiClient(@LocalServerPort int port) {
        this.restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    public EntityExchangeResult<byte[]> get(String path) {
        return exchange(HttpMethod.GET, path, null, false);
    }

    public EntityExchangeResult<byte[]> post(String path, String jsonBody) {
        return exchange(HttpMethod.POST, path, jsonBody, true);
    }

    public EntityExchangeResult<byte[]> postWithoutCsrfToken(String path, String jsonBody) {
        return exchange(HttpMethod.POST, path, jsonBody, false);
    }

    public EntityExchangeResult<byte[]> put(String path, String jsonBody) {
        return exchange(HttpMethod.PUT, path, jsonBody, true);
    }

    public EntityExchangeResult<byte[]> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null, true);
    }

    public void useClientIp(String ip) {
        this.clientIp = ip;
    }

    public boolean hasCookie(String name) {
        return cookies.containsKey(name);
    }

    private EntityExchangeResult<byte[]> exchange(HttpMethod method, String path, String jsonBody,
                                                  boolean withCsrfToken) {
        if (withCsrfToken && !cookies.containsKey(XSRF_COOKIE)) {
            // Like the SPA on startup: any GET makes the server issue the XSRF-TOKEN cookie.
            exchange(HttpMethod.GET, "/api/v1/auth/me", null, false);
        }
        RestTestClient.RequestBodySpec request = restTestClient.method(method).uri(path)
                .header("X-Forwarded-For", clientIp);
        if (!cookies.isEmpty()) {
            request.header(HttpHeaders.COOKIE, cookies.entrySet().stream()
                    .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; ")));
        }
        if (withCsrfToken && cookies.containsKey(XSRF_COOKIE)) {
            request.header(XSRF_HEADER, cookies.get(XSRF_COOKIE));
        }
        if (jsonBody != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
        }
        EntityExchangeResult<byte[]> result = request.exchange().returnResult(byte[].class);
        storeCookies(result.getResponseHeaders().get(HttpHeaders.SET_COOKIE));
        return result;
    }

    private void storeCookies(List<String> setCookieHeaders) {
        if (setCookieHeaders == null) {
            return;
        }
        for (String header : setCookieHeaders) {
            for (HttpCookie cookie : HttpCookie.parse(header)) {
                if (cookie.getMaxAge() == 0 || cookie.getValue().isEmpty()) {
                    cookies.remove(cookie.getName());
                } else {
                    cookies.put(cookie.getName(), cookie.getValue());
                }
            }
        }
    }

    /**
     * Unique per scenario, from the public ranges reserved for documentation/tests (RFC 5737). Private
     * addresses would not work: Tomcat treats them as proxies and skips them in X-Forwarded-For.
     */
    private static String nextTestNetIp() {
        int n = IP_COUNTER.getAndIncrement() % (TEST_NETS.length * 254);
        return TEST_NETS[n / 254] + (n % 254 + 1);
    }
}
