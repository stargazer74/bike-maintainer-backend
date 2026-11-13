package de.chriswohlbrecht.maintenance.service;

import de.chriswohlbrecht.maintenance.component.model.ProviderOrder;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.PaddleProperties;
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
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaddlePaymentServiceTest {

    private static final String API = "https://sandbox-api.paddle.com";
    private static final String SUBSCRIPTION = """
            {"data":{"id":"sub_1","status":"active","customer_id":"ctm_1","currency_code":"EUR",
             "started_at":"2025-11-12T10:00:00Z","canceled_at":null,
             "current_billing_period":{"starts_at":"2026-11-12T10:00:00Z","ends_at":"2027-11-12T10:00:00Z"},
             "scheduled_change":null,"custom_data":{"bm_customer_ref":"ref-1"},
             "items":[{"price":{"id":"pri_1","unit_price":{"amount":"1499","currency_code":"EUR"}}}]}}""";
    private static final String TRANSACTION = """
            {"id":"txn_1","status":"completed","origin":"subscription_recurring","currency_code":"EUR",
             "details":{"totals":{"subtotal":"1260","discount":"0","tax":"239","total":"1499"},
                        "line_items":[{"id":"txnitm_1"}]}}""";
    private static final String NO_REFUNDS = "{\"data\":[]}";

    private MockRestServiceServer server;
    private PaddlePaymentService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(API);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new PaddlePaymentService(builder.build(),
                new PaddleProperties(API, "key", "pdl_ntfset_secret", "pri_1", "test_token"));
    }

    /** Only price, our random reference and the email address – no IP, no internal id. */
    @Test
    void createCheckoutSendsOnlyWhatTheCheckoutNeeds() {
        server.expect(requestTo(API + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.email").value("rider@example.org"))
                .andExpect(jsonPath("$.custom_data.bm_customer_ref").value("ref-1"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"ctm_1\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/transactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.items[0].price_id").value("pri_1"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.customer_id").value("ctm_1"))
                .andExpect(jsonPath("$.custom_data.bm_customer_ref").value("ref-1"))
                .andExpect(jsonPath("$.customer_ip_address").doesNotExist())
                .andRespond(withSuccess("{\"data\":{\"id\":\"txn_1\",\"checkout\":{\"url\":"
                        + "\"https://app.example.org/abo/bezahlen?_ptxn=txn_1\"}}}", MediaType.APPLICATION_JSON));

        assertThat(service.createCheckout("ref-1", "rider@example.org"))
                .isEqualTo("https://app.example.org/abo/bezahlen?_ptxn=txn_1");
        server.verify();
    }

    /** Paddle keeps one customer per email address: the existing one named in the 409 is reused. */
    @Test
    void createCheckoutReusesTheExistingCustomer() {
        server.expect(requestTo(API + "/customers"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"request_error\",\"code\":\"customer_already_exists\","
                                + "\"detail\":\"Customer email conflicts with existing customer ID "
                                + "ctm_01ghbk4xjn4qdsmstcwzgcgg35.\"}}"));
        server.expect(requestTo(API + "/transactions"))
                .andExpect(jsonPath("$.customer_id").value("ctm_01ghbk4xjn4qdsmstcwzgcgg35"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"txn_1\",\"checkout\":{\"url\":\"https://pay\"}}}",
                        MediaType.APPLICATION_JSON));

        assertThat(service.createCheckout("ref-1", "rider@example.org")).isEqualTo("https://pay");
    }

    @Test
    void otherCustomerErrorsAreAProviderProblem() {
        server.expect(requestTo(API + "/customers"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"customer_already_exists\",\"detail\":\"no id\"}}"));
        assertThatThrownBy(() -> service.createCheckout("ref-1", "a@b.c"))
                .isInstanceOf(PaymentProviderUnavailableException.class);

        server.reset();
        server.expect(requestTo(API + "/customers")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createCheckout("ref-1", "a@b.c"))
                .isInstanceOf(PaymentProviderUnavailableException.class);

        server.reset();
        server.expect(requestTo(API + "/customers")).andRespond(withServerError());
        assertThatThrownBy(() -> service.createCheckout("ref-1", "a@b.c"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    /** Without a default payment link Paddle returns no checkout address: a configuration problem, not a page. */
    @Test
    void createCheckoutWithoutUrlIsAProviderProblem() {
        server.expect(requestTo(API + "/customers"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"ctm_1\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/transactions"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"txn_1\",\"checkout\":{\"url\":null}}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.createCheckout("ref-1", "a@b.c"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void answerWithoutDataIsAProviderProblem() {
        server.expect(requestTo(API + "/customers"))
                .andRespond(withSuccess("{\"meta\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.createCheckout("ref-1", "a@b.c"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void customerPortalOfTheSubscriptionsCustomer() {
        server.expect(requestTo(API + "/subscriptions/sub_1"))
                .andRespond(withSuccess(SUBSCRIPTION, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/customers/ctm_1/portal-sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.subscription_ids[0]").value("sub_1"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"cpls_1\",\"urls\":{\"general\":{\"overview\":"
                        + "\"https://customer-portal.paddle.com/cpl_1\"}}}}", MediaType.APPLICATION_JSON));

        assertThat(service.createCustomerPortal("sub_1")).contains("https://customer-portal.paddle.com/cpl_1");
    }

    @Test
    void customerPortalOfAnUnknownSubscriptionIsEmpty() {
        server.expect(requestTo(API + "/subscriptions/sub_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(service.createCustomerPortal("sub_x")).isEmpty();
    }

    @Test
    void getSubscriptionMapsTheProvidersState() {
        server.expect(requestTo(API + "/subscriptions/sub_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SUBSCRIPTION, MediaType.APPLICATION_JSON));

        assertThat(service.getSubscription("sub_1")).contains(new ProviderSubscription("sub_1", "active",
                OffsetDateTime.parse("2027-11-12T10:00:00Z"), null, false, "ref-1",
                OffsetDateTime.parse("2025-11-12T10:00:00Z"), OffsetDateTime.parse("2026-11-12T10:00:00Z"), null,
                1499L, "EUR"));
    }

    /** A cancellation by the customer is a scheduled change; the subscription only ends at its date. */
    @Test
    void scheduledCancelMeansCancelledAtPeriodEnd() {
        server.expect(requestTo(API + "/subscriptions/sub_1"))
                .andRespond(withSuccess(SUBSCRIPTION.replace("\"scheduled_change\":null",
                        "\"scheduled_change\":{\"action\":\"cancel\",\"effective_at\":\"2027-11-12T10:00:00Z\"}"),
                        MediaType.APPLICATION_JSON));

        ProviderSubscription subscription = service.getSubscription("sub_1").orElseThrow();
        assertThat(subscription.cancelAtPeriodEnd()).isTrue();
        assertThat(subscription.endedAt()).isNull();
    }

    @Test
    void canceledSubscriptionEndedWhenItWasCanceled() {
        server.expect(requestTo(API + "/subscriptions/sub_1"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"sub_1\",\"status\":\"canceled\","
                        + "\"canceled_at\":\"2027-01-05T09:00:00Z\",\"current_billing_period\":null,"
                        + "\"custom_data\":null,\"items\":[]}}", MediaType.APPLICATION_JSON));

        ProviderSubscription subscription = service.getSubscription("sub_1").orElseThrow();
        assertThat(subscription.endedAt()).isEqualTo(OffsetDateTime.parse("2027-01-05T09:00:00Z"));
        assertThat(subscription.currentPeriodEnd()).isNull();
        assertThat(subscription.customerRef()).isNull();
        assertThat(subscription.amount()).isNull();
        assertThat(subscription.currency()).isNull();
    }

    @Test
    void unknownSubscriptionIsEmptyAndErrorsAreProviderProblems() {
        server.expect(requestTo(API + "/subscriptions/sub_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(service.getSubscription("sub_x")).isEmpty();

        server.reset();
        server.expect(requestTo(API + "/subscriptions/sub_x")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.getSubscription("sub_x"))
                .isInstanceOf(PaymentProviderUnavailableException.class);

        server.reset();
        server.expect(requestTo(API + "/subscriptions/sub_x"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"sub_x\"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.getSubscription("sub_x"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void revokeCancelsImmediately() {
        server.expect(requestTo(API + "/subscriptions/sub_1"))
                .andRespond(withSuccess(SUBSCRIPTION, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/subscriptions/sub_1/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.effective_from").value("immediately"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"sub_1\",\"status\":\"canceled\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(service.revokeSubscription("sub_1")).isTrue();
        server.verify();
    }

    @Test
    void revokeOfAnUnknownOrCanceledSubscriptionDoesNothing() {
        server.expect(requestTo(API + "/subscriptions/sub_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(service.revokeSubscription("sub_x")).isFalse();

        server.reset();
        server.expect(requestTo(API + "/subscriptions/sub_1"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"sub_1\",\"status\":\"canceled\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(never(), requestTo(API + "/subscriptions/sub_1/cancel"));
        assertThat(service.revokeSubscription("sub_1")).isFalse();
        server.verify();
    }

    /** An account must not be deleted while Paddle keeps billing: a failed cancel is a provider problem. */
    @Test
    void failedRevokeIsAProviderProblem() {
        server.expect(requestTo(API + "/subscriptions/sub_1"))
                .andRespond(withSuccess(SUBSCRIPTION, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/subscriptions/sub_1/cancel")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.revokeSubscription("sub_1"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    /** Net = total minus tax; refunds so far count with their net part, also pending ones. */
    @Test
    void lastPeriodOrderIsTheNewestPaymentOfAPeriod() {
        server.expect(requestTo(API + "/transactions?subscription_id=sub_1&order_by=created_at%5BDESC%5D&per_page=10"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"txn_pm\",\"status\":\"completed\","
                        + "\"origin\":\"subscription_payment_method_change\"}," + TRANSACTION + "]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/adjustments?transaction_id=txn_1&action=refund"
                        + "&status=pending_approval,approved&per_page=50"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"adj_1\",\"status\":\"approved\","
                        + "\"totals\":{\"subtotal\":\"300\",\"tax\":\"57\",\"total\":\"357\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(service.findLastPeriodOrder("sub_1"))
                .contains(new ProviderOrder("txn_1", 1260, 300, 960, "EUR"));
    }

    @Test
    void newestPeriodPaymentNotPaidYetGivesNothing() {
        server.expect(requestTo(API + "/transactions?subscription_id=sub_1&order_by=created_at%5BDESC%5D&per_page=10"))
                .andRespond(withSuccess("{\"data\":[" + TRANSACTION.replace("\"completed\"", "\"past_due\"") + ","
                        + TRANSACTION.replace("txn_1", "txn_0") + "]}", MediaType.APPLICATION_JSON));

        assertThat(service.findLastPeriodOrder("sub_1")).isEmpty();
    }

    @Test
    void noPeriodPaymentGivesNothing() {
        server.expect(requestTo(API + "/transactions?subscription_id=sub_1&order_by=created_at%5BDESC%5D&per_page=10"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(service.findLastPeriodOrder("sub_1")).isEmpty();
    }

    @Test
    void getOrderWithItsRefunds() {
        server.expect(requestTo(API + "/transactions/txn_1"))
                .andRespond(withSuccess("{\"data\":" + TRANSACTION + "}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/adjustments?transaction_id=txn_1&action=refund"
                        + "&status=pending_approval,approved&per_page=50"))
                .andRespond(withSuccess(NO_REFUNDS, MediaType.APPLICATION_JSON));

        assertThat(service.getOrder("txn_1")).contains(new ProviderOrder("txn_1", 1260, 0, 1260, "EUR"));
    }

    @Test
    void unknownOrderIsEmpty() {
        server.expect(requestTo(API + "/transactions/txn_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(service.getOrder("txn_x")).isEqualTo(Optional.empty());
    }

    @Test
    void orderWithoutAmountsIsAProviderProblem() {
        server.expect(requestTo(API + "/transactions/txn_1"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"txn_1\",\"currency_code\":\"EUR\","
                        + "\"details\":{\"totals\":{\"total\":\"abc\",\"tax\":\"0\"}}}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/adjustments?transaction_id=txn_1&action=refund"
                        + "&status=pending_approval,approved&per_page=50"))
                .andRespond(withSuccess(NO_REFUNDS, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getOrder("txn_1")).isInstanceOf(PaymentProviderUnavailableException.class);
    }

    /** Only our own refunds of that subscription count, recognised by their reason. */
    @Test
    void markedRefundAmountSumsOurRefunds() {
        server.expect(requestTo(API + "/adjustments?transaction_id=txn_1&action=refund"
                        + "&status=pending_approval,approved&per_page=50"))
                .andRespond(withSuccess("{\"data\":["
                        + "{\"reason\":\"" + PaddlePaymentService.REFUND_REASON_PREFIX + "sub_1\","
                        + "\"totals\":{\"subtotal\":\"500\"}},"
                        + "{\"reason\":\"requested by customer\",\"totals\":{\"subtotal\":\"100\"}},"
                        + "{\"reason\":\"" + PaddlePaymentService.REFUND_REASON_PREFIX + "sub_2\","
                        + "\"totals\":{\"subtotal\":\"70\"}}]}", MediaType.APPLICATION_JSON));

        assertThat(service.markedRefundAmount("txn_1", "sub_1")).isEqualTo(500);
    }

    /** A partial refund of the single item; with the tax mode external Paddle adds the tax to the net amount. */
    @Test
    void refundIsAPartialAdjustmentBeforeTax() {
        server.expect(requestTo(API + "/transactions/txn_1"))
                .andRespond(withSuccess("{\"data\":" + TRANSACTION + "}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/adjustments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.action").value("refund"))
                .andExpect(jsonPath("$.transaction_id").value("txn_1"))
                .andExpect(jsonPath("$.reason").value(PaddlePaymentService.REFUND_REASON_PREFIX + "sub_1"))
                .andExpect(jsonPath("$.type").value("partial"))
                .andExpect(jsonPath("$.tax_mode").value("external"))
                .andExpect(jsonPath("$.items[0].item_id").value("txnitm_1"))
                .andExpect(jsonPath("$.items[0].type").value("partial"))
                .andExpect(jsonPath("$.items[0].amount").value("963"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"adj_1\",\"status\":\"pending_approval\"}}",
                        MediaType.APPLICATION_JSON));

        service.refund("txn_1", 963, "sub_1");
        server.verify();
    }

    @Test
    void refundOfAnUnknownOrderIsAProviderProblem() {
        server.expect(requestTo(API + "/transactions/txn_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.refund("txn_x", 100, "sub_1"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void sandboxIsRecognisedFromTheApiAddress() {
        assertThat(new PaddleProperties(API, null, null, null, null).sandbox()).isTrue();
        assertThat(new PaddleProperties("https://api.paddle.com", null, null, null, null).sandbox()).isFalse();
    }
}
