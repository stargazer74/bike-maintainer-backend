package de.chriswohlbrecht.maintenance.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingInterceptorTest {

    private final RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsMethodAndRouteIntoMdc() {
        MockHttpServletRequest request = request("/api/v1/vehicles/7", "/api/v1/vehicles/{vehicleId}");

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("http.method")).isEqualTo("GET");
        assertThat(MDC.get("http.route")).isEqualTo("/api/v1/vehicles/{vehicleId}");
    }

    @Test
    void removesKeysAfterCompletion() {
        MockHttpServletRequest request = request("/api/v1/vehicles/7", "/api/v1/vehicles/{vehicleId}");
        interceptor.preHandle(request, response, new Object());

        interceptor.afterCompletion(request, response, new Object(), new IllegalStateException("boom"));

        assertThat(MDC.get("http.method")).isNull();
        assertThat(MDC.get("http.route")).isNull();
    }

    @Test
    void leavesRouteOutWithoutPattern() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favicon.ico");

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("http.route")).isNull();
        assertThat(MDC.get("http.method")).isEqualTo("GET");
    }

    @Test
    void neverUsesQueryString() {
        MockHttpServletRequest request = request("/api/v1/vehicles/7", "/api/v1/vehicles/{vehicleId}");
        request.setQueryString("x=secret");

        interceptor.preHandle(request, response, new Object());

        Map<String, String> mdc = MDC.getCopyOfContextMap();
        assertThat(mdc.values()).noneMatch(value -> value.contains("7") || value.contains("secret"));
    }

    private static MockHttpServletRequest request(String uri, String pattern) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
        return request;
    }
}
