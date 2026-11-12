package de.chriswohlbrecht.maintenance.service;

import de.chriswohlbrecht.maintenance.component.model.ProviderOrder;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.PolarProperties;
import de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PolarPaymentServiceTest {

    private static final String API = "https://sandbox-api.polar.sh";

    private MockRestServiceServer server;
    private PolarPaymentService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(API);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new PolarPaymentService(builder.build(),
                new PolarProperties(API, "token", "whsec_c2VjcmV0", "product-1"));
    }

    /** Only product, our random reference, the email address and the two addresses – no IP, no internal id. */
    @Test
    void createCheckoutSendsOnlyWhatTheCheckoutNeeds() {
        server.expect(requestTo(API + "/v1/checkouts/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.products[0]").value("product-1"))
                .andExpect(jsonPath("$.external_customer_id").value("ref-1"))
                .andExpect(jsonPath("$.customer_email").value("rider@example.org"))
                .andExpect(jsonPath("$.success_url").value("https://app.example.org/konto?abo=danke"))
                .andExpect(jsonPath("$.return_url").value("https://app.example.org/konto"))
                .andExpect(jsonPath("$.customer_ip_address").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"co_1\",\"url\":\"https://polar.sh/checkout/co_1\"}",
                        MediaType.APPLICATION_JSON));

        String url = service.createCheckout("ref-1", "rider@example.org", "https://app.example.org/konto?abo=danke",
                "https://app.example.org/konto");

        assertThat(url).isEqualTo("https://polar.sh/checkout/co_1");
        server.verify();
    }

    @Test
    void createCheckoutWithoutUrlInTheAnswerIsAProviderProblem() {
        server.expect(requestTo(API + "/v1/checkouts/"))
                .andRespond(withSuccess("{\"id\":\"co_1\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.createCheckout("ref-1", "a@b.c", "s", "r"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void serverErrorIsAProviderProblem() {
        server.expect(requestTo(API + "/v1/checkouts/")).andRespond(withServerError());

        assertThatThrownBy(() -> service.createCheckout("ref-1", "a@b.c", "s", "r"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void createCustomerPortalReturnsThePortalAddress() {
        server.expect(requestTo(API + "/v1/customer-sessions/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"external_customer_id\":\"ref-1\",\"return_url\":\"https://x/konto\"}"))
                .andRespond(withSuccess("{\"customer_portal_url\":\"https://polar.sh/portal/abc\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(service.createCustomerPortal("ref-1", "https://x/konto")).contains("https://polar.sh/portal/abc");
    }

    @Test
    void unknownCustomerHasNoPortal() {
        server.expect(requestTo(API + "/v1/customer-sessions/")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(service.createCustomerPortal("ref-1", "https://x/konto")).isEmpty();
    }

    @Test
    void getSubscriptionReadsStatusPeriodAndCustomerReference() {
        server.expect(requestTo(API + "/v1/subscriptions/sub_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"sub_1","status":"active","current_period_end":"2027-11-12T10:00:00Z",
                         "ended_at":null,"cancel_at_period_end":true,"customer":{"external_id":"ref-1"}}
                        """, MediaType.APPLICATION_JSON));

        Optional<ProviderSubscription> subscription = service.getSubscription("sub_1");

        assertThat(subscription).contains(new ProviderSubscription("sub_1", "active",
                OffsetDateTime.parse("2027-11-12T10:00:00Z"), null, true, "ref-1"));
    }

    @Test
    void getSubscriptionReadsPeriodStartCancellationAndPrice() {
        server.expect(requestTo(API + "/v1/subscriptions/sub_1")).andRespond(withSuccess("""
                {"id":"sub_1","status":"active","current_period_end":"2027-11-12T10:00:00Z",
                 "current_period_start":"2026-11-12T10:00:00Z","started_at":"2025-11-12T10:00:00Z",
                 "canceled_at":"2027-01-05T08:00:00Z","cancel_at_period_end":true,"amount":2400,"currency":"eur",
                 "customer":{"external_id":"ref-1"}}
                """, MediaType.APPLICATION_JSON));

        assertThat(service.getSubscription("sub_1")).contains(new ProviderSubscription("sub_1", "active",
                OffsetDateTime.parse("2027-11-12T10:00:00Z"), null, true, "ref-1",
                OffsetDateTime.parse("2025-11-12T10:00:00Z"), OffsetDateTime.parse("2026-11-12T10:00:00Z"),
                OffsetDateTime.parse("2027-01-05T08:00:00Z"), 2400L, "eur"));
    }

    @Test
    void subscriptionWithoutCustomerReferenceIsRead() {
        server.expect(requestTo(API + "/v1/subscriptions/sub_1")).andRespond(withSuccess(
                "{\"id\":\"sub_1\",\"status\":\"canceled\",\"ended_at\":\"2026-11-01T00:00:00Z\",\"customer\":{}}",
                MediaType.APPLICATION_JSON));

        assertThat(service.getSubscription("sub_1")).hasValueSatisfying(subscription -> {
            assertThat(subscription.customerRef()).isNull();
            assertThat(subscription.cancelAtPeriodEnd()).isFalse();
            assertThat(subscription.endedAt()).isEqualTo(OffsetDateTime.parse("2026-11-01T00:00:00Z"));
        });
    }

    @Test
    void unknownSubscriptionIsEmpty() {
        server.expect(requestTo(API + "/v1/subscriptions/sub_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(service.getSubscription("sub_x")).isEmpty();
    }

    @Test
    void revokeSubscriptionDeletesItAndToleratesAnUnknownOrEndedOne() {
        server.expect(requestTo(API + "/v1/subscriptions/sub_1")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"id\":\"sub_1\",\"status\":\"canceled\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/v1/subscriptions/sub_2")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(API + "/v1/subscriptions/sub_3")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"AlreadyCanceledSubscription\"}"));

        assertThat(service.revokeSubscription("sub_1")).isTrue();
        assertThat(service.revokeSubscription("sub_2")).isFalse();
        assertThat(service.revokeSubscription("sub_3")).isFalse();
        server.verify();
    }

    @Test
    void revokeFailsWhenPolarIsDown() {
        server.expect(requestTo(API + "/v1/subscriptions/sub_1")).andRespond(withServerError());

        assertThatThrownBy(() -> service.revokeSubscription("sub_1"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    /** Skips a payment that is not for a period (plan change) and one that is not paid. */
    @Test
    void findLastPeriodOrderTakesTheNewestPaidPeriodPayment() {
        server.expect(requestTo(API + "/v1/orders/?subscription_id=sub_1&sorting=-created_at&limit=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"items": [
                          {"id": "ord_4", "billing_reason": "subscription_update", "status": "paid",
                           "net_amount": 500, "refunded_amount": 0, "refundable_amount": 500, "currency": "eur"},
                          {"id": "ord_3", "billing_reason": "subscription_cycle", "status": "pending",
                           "net_amount": 2400, "refunded_amount": 0, "refundable_amount": 0, "currency": "eur"},
                          {"id": "ord_2", "billing_reason": "subscription_cycle", "status": "partially_refunded",
                           "net_amount": 2400, "refunded_amount": 100, "refundable_amount": 2300, "currency": "eur"},
                          {"id": "ord_1", "billing_reason": "subscription_create", "status": "paid",
                           "net_amount": 2400, "refunded_amount": 0, "refundable_amount": 2400, "currency": "eur"}
                        ]}""", MediaType.APPLICATION_JSON));

        assertThat(service.findLastPeriodOrder("sub_1"))
                .contains(new ProviderOrder("ord_2", 2400, 100, 2300, "eur"));
    }

    @Test
    void findLastPeriodOrderIsEmptyWithoutPaidPeriodPayment() {
        server.expect(requestTo(API + "/v1/orders/?subscription_id=sub_1&sorting=-created_at&limit=10"))
                .andRespond(withSuccess("{\"items\": []}", MediaType.APPLICATION_JSON));

        assertThat(service.findLastPeriodOrder("sub_1")).isEmpty();
    }

    @Test
    void getOrderReadsTheAmountsAndToleratesAnUnknownOne() {
        server.expect(requestTo(API + "/v1/orders/ord_1")).andRespond(withSuccess("""
                {"id": "ord_1", "net_amount": 2400, "refunded_amount": 1841, "refundable_amount": 559,
                 "currency": "eur"}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/v1/orders/ord_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(service.getOrder("ord_1")).contains(new ProviderOrder("ord_1", 2400, 1841, 559, "eur"));
        assertThat(service.getOrder("ord_x")).isEmpty();
    }

    @Test
    void orderWithoutAmountIsAProviderProblem() {
        server.expect(requestTo(API + "/v1/orders/ord_1")).andRespond(withSuccess(
                "{\"id\": \"ord_1\", \"currency\": \"eur\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getOrder("ord_1")).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    /** Net amount in cents, marked with our metadata; no personal data. */
    @Test
    void refundSendsTheNetAmountAndOurMarker() {
        server.expect(requestTo(API + "/v1/refunds/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.order_id").value("ord_1"))
                .andExpect(jsonPath("$.reason").value("customer_request"))
                .andExpect(jsonPath("$.amount").value(1841))
                .andExpect(jsonPath("$.metadata.bm_early_end").value("sub_1"))
                .andRespond(withSuccess("{\"id\":\"ref_1\"}", MediaType.APPLICATION_JSON));

        service.refund("ord_1", 1841, "sub_1");

        server.verify();
    }

    @Test
    void refundFailsWhenPolarRefuses() {
        server.expect(requestTo(API + "/v1/refunds/")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.refund("ord_1", 1841, "sub_1"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void forbiddenIsAProviderProblemAndNotUnknown() {
        server.expect(requestTo(API + "/v1/subscriptions/sub_1")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.getSubscription("sub_1"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }
}
