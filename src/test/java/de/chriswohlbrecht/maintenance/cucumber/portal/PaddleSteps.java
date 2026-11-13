package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.component.helper.WebhookSignatureHelper;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.PaddleProperties;
import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The payment provider: its state lives in {@link FakePaymentProviderService}, its webhooks are signed here. */
public class PaddleSteps {

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/paddle";

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private FakePaymentProviderService paymentProvider;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PaddleProperties paddleProperties;

    @Autowired
    private Clock clock;

    @Before
    public void resetProvider() {
        paymentProvider.clear();
    }

    @Given("Paddle reports subscription {string} of {string} as {string} until in {int} days")
    public void paddleReportsSubscription(String subscriptionId, String email, String status, int days) {
        paymentProvider.put(new ProviderSubscription(subscriptionId, status,
                OffsetDateTime.now(clock).plusDays(days), null, false, customerRef(email)));
    }

    @Given("Paddle reports subscription {string} of {string} as canceled")
    public void paddleReportsCanceled(String subscriptionId, String email) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        paymentProvider.put(new ProviderSubscription(subscriptionId, "canceled", null, now.minusDays(1), false,
                customerRef(email)));
    }

    @When("Paddle sends a signed {string} event for subscription {string}")
    public void paddleSendsSignedEvent(String type, String subscriptionId) {
        send(event(type, subscriptionId), true);
    }

    @When("someone sends an unsigned {string} event for subscription {string}")
    public void someoneSendsUnsignedEvent(String type, String subscriptionId) {
        send(event(type, subscriptionId), false);
    }

    @When("an oversized webhook is sent")
    public void anOversizedWebhookIsSent() {
        String body = "{\"event_type\":\"subscription.updated\",\"padding\":\"" + "x".repeat(70 * 1024) + "\"}";
        send(body, true);
    }

    @Then("Paddle revoked subscription {string}")
    public void paddleRevoked(String subscriptionId) {
        assertThat(paymentProvider.revoked()).contains(subscriptionId);
    }

    private void send(String body, boolean signed) {
        long timestamp = clock.instant().getEpochSecond();
        String signature = signed ? WebhookSignatureHelper.signatureHeader(paddleProperties.webhookSecret(), timestamp,
                body) : "ts=" + timestamp + ";h1=" + "0".repeat(64);
        scenarioContext.setLastResponse(apiClient.postAsServer(WEBHOOK_PATH, Map.of("Paddle-Signature", signature),
                body));
    }

    private static String event(String type, String subscriptionId) {
        return "{\"event_id\":\"evt_" + UUID.randomUUID() + "\",\"event_type\":\"" + type
                + "\",\"occurred_at\":\"2026-11-12T12:00:00Z\",\"data\":{\"id\":\"" + subscriptionId + "\"}}";
    }

    /** The reference created by the checkout; the scenario must have started one. */
    private String customerRef(String email) {
        String ref = appUserRepository.findByEmailIgnoreCase(email).orElseThrow().getBillingCustomerRef();
        assertThat(ref).as("billing reference of %s (start a checkout first)", email).isNotNull();
        return ref;
    }
}
