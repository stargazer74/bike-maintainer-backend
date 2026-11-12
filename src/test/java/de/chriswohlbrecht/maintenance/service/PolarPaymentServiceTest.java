package de.chriswohlbrecht.maintenance.service;

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
import static org.assertj.core.api.Assertions.assertThatCode;
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
    void revokeSubscriptionDeletesItAndToleratesAnUnknownOne() {
        server.expect(requestTo(API + "/v1/subscriptions/sub_1")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"id\":\"sub_1\",\"status\":\"canceled\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/v1/subscriptions/sub_2")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatCode(() -> {
            service.revokeSubscription("sub_1");
            service.revokeSubscription("sub_2");
        }).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void forbiddenIsAProviderProblemAndNotUnknown() {
        server.expect(requestTo(API + "/v1/subscriptions/sub_1")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.getSubscription("sub_1"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }
}
