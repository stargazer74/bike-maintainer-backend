package de.chriswohlbrecht.maintenance.cucumber;

import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthSteps {

    /** Password of users created implicitly by "I am logged in as". */
    static final String DEFAULT_PASSWORD = "test-password-123";

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Given("a verified user {string} with password {string} exists")
    public void aVerifiedUserExists(String email, String password) {
        createUser(email, password, true, true);
    }

    @Given("an unverified user {string} with password {string} exists")
    public void anUnverifiedUserExists(String email, String password) {
        createUser(email, password, false, true);
    }

    @Given("a deactivated user {string} with password {string} exists")
    public void aDeactivatedUserExists(String email, String password) {
        createUser(email, password, true, false);
    }

    @Given("I am logged in as {string}")
    public void iAmLoggedInAs(String email) {
        if (appUserRepository.findByEmailIgnoreCase(email).isEmpty()) {
            createUser(email, DEFAULT_PASSWORD, true, true);
        }
        EntityExchangeResult<byte[]> response = login(email, DEFAULT_PASSWORD);
        assertThat(response.getStatus().value())
                .as("login as %s (response: %s)", email, new String(response.getResponseBody()))
                .isEqualTo(200);
    }

    @Given("the client IP is {string}")
    public void theClientIpIs(String ip) {
        apiClient.useClientIp(ip);
    }

    @When("I log in as {string} with password {string}")
    public void iLogInAsWithPassword(String email, String password) {
        scenarioContext.setLastResponse(login(email, password));
    }

    @When("I fail to log in as {string} {int} times")
    public void iFailToLogIn(String email, int times) {
        for (int i = 0; i < times; i++) {
            login(email, "definitely-wrong-password");
        }
    }

    @When("I log out")
    public void iLogOut() {
        scenarioContext.setLastResponse(apiClient.post("/api/v1/auth/logout", null));
    }

    @Then("the client has a {string} cookie")
    public void theClientHasACookie(String name) {
        assertThat(apiClient.hasCookie(name)).as("cookie %s", name).isTrue();
    }

    private EntityExchangeResult<byte[]> login(String email, String password) {
        return apiClient.post("/api/v1/auth/login",
                "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}");
    }

    private void createUser(String email, String password, boolean emailVerified, boolean active) {
        appUserRepository.save(AppUser.builder()
                .email(email.trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.USER)
                .emailVerified(emailVerified)
                .active(active)
                .build());
    }
}
