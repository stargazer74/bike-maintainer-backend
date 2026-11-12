package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.component.helper.WebhookSignatureHelper;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.configuration.PolarProperties;
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
public class PolarSteps {

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/polar";

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private FakePaymentProviderService paymentProvider;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PolarProperties polarProperties;

    @Autowired
    private Clock clock;

    @Before
    public void resetProvider() {
        paymentProvider.clear();
    }

    @Given("Polar reports subscription {string} of {string} as {string} until in {int} days")
    public void polarReportsSubscription(String subscriptionId, String email, String status, int days) {
        paymentProvider.put(new ProviderSubscription(subscriptionId, status,
                OffsetDateTime.now(clock).plusDays(days), null, false, customerRef(email)));
    }

    @Given("Polar reports subscription {string} of {string} as revoked")
    public void polarReportsRevoked(String subscriptionId, String email) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        paymentProvider.put(new ProviderSubscription(subscriptionId, "canceled", now.plusDays(200),
                now.minusDays(1), false, customerRef(email)));
    }

    @When("Polar sends a signed {string} event for subscription {string}")
    public void polarSendsSignedEvent(String type, String subscriptionId) {
        send(event(type, subscriptionId), true);
    }

    @When("someone sends an unsigned {string} event for subscription {string}")
    public void someoneSendsUnsignedEvent(String type, String subscriptionId) {
        send(event(type, subscriptionId), false);
    }

    @When("an oversized webhook is sent")
    public void anOversizedWebhookIsSent() {
        String body = "{\"type\":\"subscription.updated\",\"padding\":\"" + "x".repeat(70 * 1024) + "\"}";
        send(body, true);
    }

    @Then("Polar revoked subscription {string}")
    public void polarRevoked(String subscriptionId) {
        assertThat(paymentProvider.revoked()).contains(subscriptionId);
    }

    private void send(String body, boolean signed) {
        String id = "msg_" + UUID.randomUUID();
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String signature = signed ? WebhookSignatureHelper.signature(polarProperties.webhookSecret(), id, timestamp,
                body) : "v1,bm90IHNpZ25lZA==";
        scenarioContext.setLastResponse(apiClient.postAsServer(WEBHOOK_PATH, Map.of("webhook-id", id,
                "webhook-timestamp", timestamp, "webhook-signature", signature), body));
    }

    private static String event(String type, String subscriptionId) {
        return "{\"type\":\"" + type + "\",\"timestamp\":\"2026-11-12T12:00:00Z\",\"data\":{\"id\":\""
                + subscriptionId + "\"}}";
    }

    /** The reference created by the checkout; the scenario must have started one. */
    private String customerRef(String email) {
        String ref = appUserRepository.findByEmailIgnoreCase(email).orElseThrow().getBillingCustomerRef();
        assertThat(ref).as("billing reference of %s (start a checkout first)", email).isNotNull();
        return ref;
    }
}
