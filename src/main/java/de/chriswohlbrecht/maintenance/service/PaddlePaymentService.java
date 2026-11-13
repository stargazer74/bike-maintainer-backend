package de.chriswohlbrecht.maintenance.service;

import de.chriswohlbrecht.maintenance.component.model.ProviderOrder;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.configuration.PaddleProperties;
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
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paddle Billing API (https://developer.paddle.com/api-reference). Only sends what a checkout needs: the price, our
 * random customer reference and the email address (for the invoice) – no IP address, no internal ids. Amounts are
 * strings in the smallest unit of the currency.
 */
@Slf4j
@Service
@Conditional(OperatingModeCondition.Portal.class)
@RequiredArgsConstructor
public class PaddlePaymentService implements IPaymentProviderService {

    /** Key of our customer reference in the custom data of the transaction, which Paddle copies to the subscription. */
    static final String CUSTOMER_REF_KEY = "bm_customer_ref";
    /** Start of the reason of our refunds, followed by the subscription id; Paddle has no metadata on adjustments. */
    static final String REFUND_REASON_PREFIX = "Cancelled after the first year, rest refunded pro rata (§ 309 Nr. 9 BGB): ";
    private static final String CUSTOMER_EXISTS = "customer_already_exists";
    private static final Pattern CUSTOMER_ID = Pattern.compile("ctm_[a-z\\d]+");
    /** Transactions that pay a period: the first purchase (created by us via the API or in the web) and renewals. */
    private static final Set<String> PERIOD_ORIGINS = Set.of("api", "web", "subscription_recurring");
    /** A refunded transaction stays completed (refunds are adjustments), so it still is the payment of its period. */
    private static final Set<String> PAID_TRANSACTION_STATUSES = Set.of("paid", "completed");
    /** Refunds that went through or may still go through; rejected and reversed ones refunded nothing. */
    private static final String COUNTING_REFUND_STATUSES = "pending_approval,approved";
    private static final int TRANSACTION_PAGE_SIZE = 10;
    private static final int ADJUSTMENT_PAGE_SIZE = 50;
    private static final String CANCELED = "canceled";

    private final RestClient paddleRestClient;
    private final PaddleProperties paddleProperties;

    @Override
    public String createCheckout(String customerRef, String email) {
        String customerId = customerIdOf(customerRef, email);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("price_id", paddleProperties.priceId(), "quantity", 1)));
        body.put("customer_id", customerId);
        body.put("custom_data", Map.of(CUSTOMER_REF_KEY, customerRef));
        JsonNode transaction = data(call("create transaction", () -> paddleRestClient.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class)));
        // Only set if a default payment link is configured in Paddle; it points to the payment page of the web app.
        return requiredText(transaction.path("checkout"), "url");
    }

    @Override
    public Optional<String> createCustomerPortal(String subscriptionId) {
        Optional<JsonNode> subscription = callOrNotFound("get subscription", () -> paddleRestClient.get()
                .uri("/subscriptions/{id}", subscriptionId)
                .retrieve()
                .body(JsonNode.class)).map(PaddlePaymentService::data);
        if (subscription.isEmpty()) {
            return Optional.empty();
        }
        String customerId = requiredText(subscription.get(), "customer_id");
        JsonNode session = data(call("create customer portal session", () -> paddleRestClient.post()
                .uri("/customers/{id}/portal-sessions", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("subscription_ids", List.of(subscriptionId)))
                .retrieve()
                .body(JsonNode.class)));
        return Optional.of(requiredText(session.path("urls").path("general"), "overview"));
    }

    @Override
    public Optional<ProviderSubscription> getSubscription(String subscriptionId) {
        return callOrNotFound("get subscription", () -> paddleRestClient.get()
                .uri("/subscriptions/{id}", subscriptionId)
                .retrieve()
                .body(JsonNode.class))
                .map(response -> toSubscription(data(response)));
    }

    /**
     * Looks first: a subscription that is canceled already cannot be canceled again. Any error of the cancel itself is
     * a provider problem, so an account is not deleted while Paddle keeps billing.
     */
    @Override
    public boolean revokeSubscription(String subscriptionId) {
        Optional<ProviderSubscription> current = getSubscription(subscriptionId);
        if (current.isEmpty() || CANCELED.equals(current.get().status())) {
            return false;
        }
        call("cancel subscription", () -> paddleRestClient.post()
                .uri("/subscriptions/{id}/cancel", subscriptionId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("effective_from", "immediately"))
                .retrieve()
                .body(JsonNode.class));
        return true;
    }

    /**
     * Only the newest payment for a period counts, not e.g. a change of the payment method. If it is refunded already,
     * it is still returned (with nothing left to refund) instead of falling back to an earlier period; a newest one that
     * is not paid (yet) gives empty.
     */
    @Override
    public Optional<ProviderOrder> findLastPeriodOrder(String subscriptionId) {
        JsonNode page = call("list transactions", () -> paddleRestClient.get()
                .uri(uri -> uri.path("/transactions")
                        .queryParam("subscription_id", subscriptionId)
                        .queryParam("order_by", "created_at[DESC]")
                        .queryParam("per_page", TRANSACTION_PAGE_SIZE)
                        .build())
                .retrieve()
                .body(JsonNode.class));
        for (JsonNode transaction : page.path("data")) {
            if (PERIOD_ORIGINS.contains(transaction.path("origin").asString(""))) {
                return PAID_TRANSACTION_STATUSES.contains(transaction.path("status").asString(""))
                        ? Optional.of(toOrder(transaction, refunds(requiredText(transaction, "id"))))
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public long markedRefundAmount(String orderId, String marker) {
        long amount = 0;
        for (JsonNode refund : refunds(orderId)) {
            if (refund.path("reason").asString("").equals(REFUND_REASON_PREFIX + marker)) {
                amount += amount(refund.path("totals"), "subtotal");
            }
        }
        return amount;
    }

    @Override
    public Optional<ProviderOrder> getOrder(String orderId) {
        return transaction(orderId).map(transaction -> toOrder(transaction, refunds(orderId)));
    }

    /**
     * A partial refund of the single item of the transaction. With the tax mode {@code external} the amount is before
     * taxes and Paddle adds the matching tax, as {@link IPaymentProviderService#refund} promises.
     */
    @Override
    public void refund(String orderId, long netAmount, String marker) {
        JsonNode transaction = transaction(orderId)
                .orElseThrow(() -> new PaymentProviderUnavailableException("Paddle does not know " + orderId));
        String itemId = requiredText(transaction.path("details").path("line_items").path(0), "id");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", "refund");
        body.put("transaction_id", orderId);
        body.put("reason", REFUND_REASON_PREFIX + marker);
        body.put("type", "partial");
        body.put("tax_mode", "external");
        body.put("items", List.of(Map.of("item_id", itemId, "type", "partial", "amount", Long.toString(netAmount))));
        call("create refund", () -> paddleRestClient.post()
                .uri("/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
    }

    /**
     * Paddle keeps one customer per email address: a second checkout with the same address reuses it (409 names the
     * existing one). Our reference travels on the transaction, not on the customer.
     */
    private String customerIdOf(String customerRef, String email) {
        Map<String, Object> body = Map.of("email", email, "custom_data", Map.of(CUSTOMER_REF_KEY, customerRef));
        try {
            return requiredText(data(paddleRestClient.post()
                    .uri("/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class)), "id");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT && e.getResponseBodyAsString().contains(CUSTOMER_EXISTS)) {
                Matcher matcher = CUSTOMER_ID.matcher(e.getResponseBodyAsString());
                if (matcher.find()) {
                    return matcher.group();
                }
            }
            throw unavailable("create customer", e);
        } catch (RestClientException e) {
            throw unavailable("create customer", e);
        }
    }

    private Optional<JsonNode> transaction(String transactionId) {
        return callOrNotFound("get transaction", () -> paddleRestClient.get()
                .uri("/transactions/{id}", transactionId)
                .retrieve()
                .body(JsonNode.class))
                .map(PaddlePaymentService::data);
    }

    /** Refunds of the transaction that went through or are pending; at most one page, more never happen for us. */
    private JsonNode refunds(String transactionId) {
        return call("list adjustments", () -> paddleRestClient.get()
                .uri(uri -> uri.path("/adjustments")
                        .queryParam("transaction_id", transactionId)
                        .queryParam("action", "refund")
                        .queryParam("status", COUNTING_REFUND_STATUSES)
                        .queryParam("per_page", ADJUSTMENT_PAGE_SIZE)
                        .build())
                .retrieve()
                .body(JsonNode.class)).path("data");
    }

    /** Net = total minus tax (after discounts); refunded = the net part of the refunds so far. */
    static ProviderOrder toOrder(JsonNode transaction, JsonNode refunds) {
        JsonNode totals = transaction.path("details").path("totals");
        long net = amount(totals, "total") - amount(totals, "tax");
        long refunded = 0;
        for (JsonNode refund : refunds) {
            refunded += amount(refund.path("totals"), "subtotal");
        }
        return new ProviderOrder(requiredText(transaction, "id"), net, refunded, Math.max(net - refunded, 0),
                requiredText(transaction, "currency_code"));
    }

    /**
     * A cancellation by the customer is a scheduled change to cancel at the end of the period; the status only becomes
     * {@code canceled} once the subscription has ended (at that date, or right away when revoked or withdrawn).
     */
    static ProviderSubscription toSubscription(JsonNode subscription) {
        boolean cancelScheduled = "cancel".equals(subscription.path("scheduled_change").path("action").asString(""));
        String status = requiredText(subscription, "status");
        JsonNode period = subscription.path("current_billing_period");
        JsonNode price = subscription.path("items").path(0).path("price").path("unit_price");
        JsonNode customerRef = subscription.path("custom_data").path(CUSTOMER_REF_KEY);
        return new ProviderSubscription(
                requiredText(subscription, "id"),
                status,
                dateTime(period.path("ends_at")),
                CANCELED.equals(status) ? dateTime(subscription.path("canceled_at")) : null,
                cancelScheduled,
                customerRef.isString() ? customerRef.asString() : null,
                dateTime(subscription.path("started_at")),
                dateTime(period.path("starts_at")),
                null,
                price.path("amount").isString() ? Long.parseLong(price.path("amount").asString()) : null,
                subscription.path("currency_code").isString() ? subscription.path("currency_code").asString() : null);
    }

    private static JsonNode data(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isObject()) {
            throw new PaymentProviderUnavailableException("Paddle answered without data");
        }
        return data;
    }

    private static long amount(JsonNode node, String field) {
        JsonNode value = node.path(field);
        try {
            if (value.isString()) {
                return Long.parseLong(value.asString());
            }
        } catch (NumberFormatException e) {
            // handled below
        }
        throw new PaymentProviderUnavailableException("Paddle answered without " + field);
    }

    private static OffsetDateTime dateTime(JsonNode value) {
        return value.isString() ? OffsetDateTime.parse(value.asString()) : null;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new PaymentProviderUnavailableException("Paddle answered without " + field);
        }
        return value.asString();
    }

    /** 404 means "unknown" here, everything else is a provider problem. */
    private static Optional<JsonNode> callOrNotFound(String action, Supplier<JsonNode> request) {
        try {
            return Optional.ofNullable(call(action, request));
        } catch (PaymentProviderUnavailableException e) {
            if (e.getCause() instanceof HttpClientErrorException error
                    && error.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static JsonNode call(String action, Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw unavailable(action, e);
        }
    }

    private static PaymentProviderUnavailableException unavailable(String action, RestClientException e) {
        // Never the message: it contains Paddle's response body, which may repeat the customer's email address.
        log.warn("Paddle: {} failed: {}", action, e instanceof RestClientResponseException response
                ? "HTTP " + response.getStatusCode().value() : e.getClass().getSimpleName());
        return new PaymentProviderUnavailableException("Paddle: " + action + " failed", e);
    }
}
