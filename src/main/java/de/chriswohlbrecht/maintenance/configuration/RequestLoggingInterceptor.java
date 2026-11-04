package de.chriswohlbrecht.maintenance.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Puts the HTTP method and the matched route pattern (e.g. {@code /api/v1/vehicles/{vehicleId}}) into the MDC, so
 * that structured log lines (ECS JSON in the portal) can be filtered by endpoint. Never the concrete URL or the
 * query string: they may contain IDs or tokens. Requests that match no handler get no route.
 */
public class RequestLoggingInterceptor implements HandlerInterceptor {

    static final String METHOD_KEY = "http.method";
    static final String ROUTE_KEY = "http.route";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MDC.put(METHOD_KEY, request.getMethod());
        if (request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String route) {
            MDC.put(ROUTE_KEY, route);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        MDC.remove(METHOD_KEY);
        MDC.remove(ROUTE_KEY);
    }
}
