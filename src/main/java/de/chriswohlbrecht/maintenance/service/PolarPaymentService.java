package de.chriswohlbrecht.maintenance.service;

import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.configuration.PolarProperties;
import de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Polar API (https://polar.sh/docs/api-reference). Only sends what a checkout needs: the product, our random
 * customer reference and the email address (for the invoice) – no IP address, no internal ids.
 */
@Slf4j
@Service
@Conditional(OperatingModeCondition.Portal.class)
@RequiredArgsConstructor
public class PolarPaymentService implements IPaymentProviderService {

    private final RestClient polarRestClient;
    private final PolarProperties polarProperties;

    @Override
    public String createCheckout(String customerRef, String email, String successUrl, String returnUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("products", List.of(polarProperties.productId()));
        body.put("external_customer_id", customerRef);
        body.put("customer_email", email);
        body.put("success_url", successUrl);
        body.put("return_url", returnUrl);
        JsonNode checkout = call("create checkout", () -> polarRestClient.post()
                .uri("/v1/checkouts/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        return requiredText(checkout, "url");
    }

    @Override
    public Optional<String> createCustomerPortal(String customerRef, String returnUrl) {
        Map<String, Object> body = Map.of("external_customer_id", customerRef, "return_url", returnUrl);
        return callOrNotFound("create customer session", () -> polarRestClient.post()
                .uri("/v1/customer-sessions/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class))
                .map(session -> requiredText(session, "customer_portal_url"));
    }

    @Override
    public Optional<ProviderSubscription> getSubscription(String subscriptionId) {
        return callOrNotFound("get subscription", () -> polarRestClient.get()
                .uri("/v1/subscriptions/{id}", subscriptionId)
                .retrieve()
                .body(JsonNode.class))
                .map(PolarPaymentService::toSubscription);
    }

    @Override
    public void revokeSubscription(String subscriptionId) {
        callOrNotFound("revoke subscription", () -> polarRestClient.delete()
                .uri("/v1/subscriptions/{id}", subscriptionId)
                .retrieve()
                .body(JsonNode.class));
    }

    static ProviderSubscription toSubscription(JsonNode subscription) {
        JsonNode customer = subscription.path("customer");
        return new ProviderSubscription(
                requiredText(subscription, "id"),
                requiredText(subscription, "status"),
                dateTime(subscription.path("current_period_end")),
                dateTime(subscription.path("ended_at")),
                subscription.path("cancel_at_period_end").asBoolean(false),
                customer.path("external_id").isString() ? customer.path("external_id").asString() : null,
                dateTime(subscription.path("started_at")),
                dateTime(subscription.path("current_period_start")),
                dateTime(subscription.path("canceled_at")),
                subscription.path("amount").isIntegralNumber() ? subscription.path("amount").asLong() : null,
                subscription.path("currency").isString() ? subscription.path("currency").asString() : null);
    }

    private static OffsetDateTime dateTime(JsonNode value) {
        return value.isString() ? OffsetDateTime.parse(value.asString()) : null;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new PaymentProviderUnavailableException("Polar answered without " + field);
        }
        return value.asString();
    }

    /** 404 (and 422 for an unknown customer) means "unknown" here, everything else is a provider problem. */
    private static Optional<JsonNode> callOrNotFound(String action, Supplier<JsonNode> request) {
        try {
            return Optional.ofNullable(call(action, request));
        } catch (PaymentProviderUnavailableException e) {
            if (e.getCause() instanceof HttpClientErrorException error
                    && (error.getStatusCode() == HttpStatus.NOT_FOUND
                    || error.getStatusCode() == HttpStatus.UNPROCESSABLE_CONTENT)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static JsonNode call(String action, Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            log.warn("Polar: {} failed: {}", action, e.getMessage());
            throw new PaymentProviderUnavailableException("Polar: " + action + " failed", e);
        }
    }
}
