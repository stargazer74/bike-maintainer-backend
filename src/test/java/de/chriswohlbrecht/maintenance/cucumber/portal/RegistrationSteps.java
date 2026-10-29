package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.component.IAccountCleanupComponent;
import de.chriswohlbrecht.maintenance.configuration.AccountMailProperties;
import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.support.AltchaTestSupport;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Portal only: needs the {@link RecordingMailService} of the portal context. */
public class RegistrationSteps {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private RecordingMailService mailService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IAccountCleanupComponent accountCleanupComponent;

    @Autowired
    private AccountMailProperties mailProperties;

    @Autowired
    private Clock clock;

    private String lastAltcha;
    private String rememberedToken;
    private EntityExchangeResult<byte[]> rememberedResponse;

    @Before
    public void clearMails() {
        mailService.clear();
    }

    @Given("the browser language is {string}")
    public void theBrowserLanguageIs(String acceptLanguage) {
        apiClient.useAcceptLanguage(acceptLanguage);
    }

    @When("I register as {string} with password {string}")
    public void iRegister(String email, String password) {
        register(new JSONObject().put("email", email).put("password", password).put("altcha", solveChallenge()));
    }

    @When("I register as {string} with password {string} and language {string}")
    public void iRegisterWithLanguage(String email, String password, String language) {
        register(new JSONObject().put("email", email).put("password", password).put("language", language)
                .put("altcha", solveChallenge()));
    }

    @When("I register as {string} with password {string} and the honeypot filled")
    public void iRegisterWithHoneypot(String email, String password) {
        register(new JSONObject().put("email", email).put("password", password).put("website", "https://spam.example")
                .put("altcha", solveChallenge()));
    }

    @When("I register as {string} with password {string} reusing the last ALTCHA solution")
    public void iRegisterReusingAltcha(String email, String password) {
        register(new JSONObject().put("email", email).put("password", password).put("altcha", lastAltcha));
    }

    @When("I request a new verification email for {string}")
    public void iRequestANewVerificationEmail(String email) {
        scenarioContext.setLastResponse(apiClient.post("/api/v1/auth/verify-email/resend",
                new JSONObject().put("email", email).put("altcha", solveChallenge()).toString()));
    }

    @When("I open the verification link from the last email to {string}")
    public void iOpenTheVerificationLink(String email) {
        verify(tokenFromLastMail(email));
    }

    @When("I remember the verification link from the last email to {string}")
    public void iRememberTheVerificationLink(String email) {
        rememberedToken = tokenFromLastMail(email);
    }

    @When("I open the remembered verification link")
    public void iOpenTheRememberedVerificationLink() {
        verify(rememberedToken);
    }

    @Given("the verification link of {string} has expired")
    public void theVerificationLinkHasExpired(String email) {
        jdbcTemplate.update("UPDATE user_token SET expires_at = ? WHERE user_id = (SELECT id FROM app_user WHERE email = ?)",
                LocalDateTime.now(clock).minusMinutes(1), email);
    }

    @Given("the daily email limit is reached")
    public void theDailyEmailLimitIsReached() {
        jdbcTemplate.update("INSERT INTO mail_quota (quota_date, sent_count) VALUES (?, ?)",
                LocalDate.now(clock), mailProperties.dailyLimit());
    }

    @Given("an unverified user {string} registered {int} days ago")
    public void anUnverifiedUserRegisteredDaysAgo(String email, int days) {
        AppUser user = appUserRepository.saveAndFlush(
                AppUser.builder().email(email).role(UserRole.USER).emailVerified(false).build());
        jdbcTemplate.update("UPDATE app_user SET created_at = ? WHERE id = ?",
                LocalDateTime.now(clock).minusDays(days), user.getId());
    }

    @When("the account cleanup runs")
    public void theAccountCleanupRuns() {
        accountCleanupComponent.cleanUp();
    }

    @When("I remember the response")
    public void iRememberTheResponse() {
        rememberedResponse = scenarioContext.getLastResponse();
    }

    @Then("the response equals the remembered response")
    public void theResponseEqualsTheRememberedResponse() {
        EntityExchangeResult<byte[]> current = scenarioContext.getLastResponse();
        assertThat(current.getStatus()).isEqualTo(rememberedResponse.getStatus());
        assertThat(body(current)).isEqualTo(body(rememberedResponse));
    }

    @Then("{int} email(s) has/have been sent to {string}")
    public void emailsHaveBeenSentTo(int count, String email) {
        assertThat(mailService.sentTo(email)).hasSize(count);
    }

    @Then("the last email to {string} has subject {string}")
    public void theLastEmailHasSubject(String email, String subject) {
        assertThat(lastMailTo(email).subject()).isEqualTo(subject);
    }

    @Then("no account exists for {string}")
    public void noAccountExistsFor(String email) {
        assertThat(appUserRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Then("an account exists for {string}")
    public void anAccountExistsFor(String email) {
        assertThat(appUserRepository.findByEmailIgnoreCase(email)).isPresent();
    }

    private String solveChallenge() {
        EntityExchangeResult<byte[]> challenge = apiClient.get("/api/v1/auth/challenge");
        assertThat(challenge.getStatus().value()).as("challenge request").isEqualTo(200);
        lastAltcha = AltchaTestSupport.solve(body(challenge));
        return lastAltcha;
    }

    private void register(JSONObject request) {
        scenarioContext.setLastResponse(apiClient.post("/api/v1/auth/register", request.toString()));
    }

    private void verify(String token) {
        scenarioContext.setLastResponse(apiClient.post("/api/v1/auth/verify-email",
                new JSONObject().put("token", token).toString()));
    }

    private RecordingMailService.SentMail lastMailTo(String email) {
        List<RecordingMailService.SentMail> mails = mailService.sentTo(email);
        assertThat(mails).as("emails to %s", email).isNotEmpty();
        return mails.getLast();
    }

    private String tokenFromLastMail(String email) {
        String body = lastMailTo(email).body();
        assertThat(body).contains(mailProperties.linkBaseUrl() + "/email-bestaetigen?token=");
        Matcher matcher = TOKEN.matcher(body);
        assertThat(matcher.find()).as("token in email body").isTrue();
        return matcher.group(1);
    }

    private static String body(EntityExchangeResult<byte[]> response) {
        byte[] body = response.getResponseBody();
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
}
