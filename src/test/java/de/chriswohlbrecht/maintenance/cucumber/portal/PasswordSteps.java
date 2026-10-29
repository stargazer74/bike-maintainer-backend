package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import de.chriswohlbrecht.maintenance.support.AltchaTestSupport;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Portal only: password reset by email and password change, including a second browser for session checks. */
public class PasswordSteps {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private RecordingMailService mailService;

    @Autowired
    private AccountMailProperties mailProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @LocalServerPort
    private int port;

    private ApiClient secondBrowser;
    private String rememberedToken;

    @When("I request a password reset for {string}")
    public void iRequestAPasswordReset(String email) {
        EntityExchangeResult<byte[]> challenge = apiClient.get("/api/v1/auth/challenge");
        assertThat(challenge.getStatus().value()).as("challenge request").isEqualTo(200);
        String altcha = AltchaTestSupport.solve(new String(challenge.getResponseBody(), StandardCharsets.UTF_8));
        scenarioContext.setLastResponse(apiClient.post("/api/v1/auth/password-reset/request",
                new JSONObject().put("email", email).put("altcha", altcha).toString()));
    }

    @When("I reset the password with the link from the last email to {string} to {string}")
    public void iResetThePasswordWithTheLink(String email, String newPassword) {
        confirm(tokenFromLastMail(email, "/passwort-zuruecksetzen?token="), newPassword);
    }

    @When("I reset the password with the token from the last email to {string} to {string}")
    public void iResetThePasswordWithAnyToken(String email, String newPassword) {
        confirm(tokenFromLastMail(email, "token="), newPassword);
    }

    @When("I remember the password reset link from the last email to {string}")
    public void iRememberThePasswordResetLink(String email) {
        rememberedToken = tokenFromLastMail(email, "/passwort-zuruecksetzen?token=");
    }

    @When("I reset the password with the remembered link to {string}")
    public void iResetThePasswordWithTheRememberedLink(String newPassword) {
        confirm(rememberedToken, newPassword);
    }

    @Given("the password reset link of {string} has expired")
    public void thePasswordResetLinkHasExpired(String email) {
        jdbcTemplate.update("UPDATE user_token SET expires_at = ? WHERE type = 'PASSWORD_RESET'"
                        + " AND user_id = (SELECT id FROM app_user WHERE email = ?)",
                LocalDateTime.now(clock).minusMinutes(1), email);
    }

    @When("I change my password from {string} to {string}")
    public void iChangeMyPassword(String currentPassword, String newPassword) {
        scenarioContext.setLastResponse(apiClient.put("/api/v1/auth/password", new JSONObject()
                .put("currentPassword", currentPassword).put("newPassword", newPassword).toString()));
    }

    @When("I change my password from {string} to {string} {int} times")
    public void iChangeMyPasswordTimes(String currentPassword, String newPassword, int times) {
        for (int i = 0; i < times; i++) {
            iChangeMyPassword(currentPassword, newPassword);
        }
    }

    @Given("I am logged in as {string} with password {string} in a second browser")
    public void iAmLoggedInInASecondBrowser(String email, String password) {
        secondBrowser = new ApiClient(port);
        EntityExchangeResult<byte[]> response = secondBrowser.post("/api/v1/auth/login",
                new JSONObject().put("email", email).put("password", password).toString());
        assertThat(response.getStatus().value()).as("login in second browser").isEqualTo(200);
        assertThat(secondBrowser.get("/api/v1/auth/me").getStatus().value()).isEqualTo(200);
    }

    @Then("the second browser is no longer logged in")
    public void theSecondBrowserIsNoLongerLoggedIn() {
        assertThat(secondBrowser.get("/api/v1/auth/me").getStatus().value()).isEqualTo(401);
    }

    @Then("I am still logged in")
    public void iAmStillLoggedIn() {
        assertThat(apiClient.get("/api/v1/auth/me").getStatus().value()).isEqualTo(200);
    }

    private void confirm(String token, String newPassword) {
        scenarioContext.setLastResponse(apiClient.post("/api/v1/auth/password-reset/confirm",
                new JSONObject().put("token", token).put("newPassword", newPassword).toString()));
    }

    private String tokenFromLastMail(String email, String linkPart) {
        List<RecordingMailService.SentMail> mails = mailService.sentTo(email);
        assertThat(mails).as("emails to %s", email).isNotEmpty();
        String body = mails.getLast().body();
        if (linkPart.startsWith("/")) {
            assertThat(body).contains(mailProperties.linkBaseUrl() + linkPart);
        }
        Matcher matcher = TOKEN.matcher(body);
        assertThat(matcher.find()).as("token in email body").isTrue();
        return matcher.group(1);
    }
}
