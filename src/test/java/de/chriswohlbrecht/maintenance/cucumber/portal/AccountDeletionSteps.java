package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Portal only: deleting the own account. */
public class AccountDeletionSteps {

    private static final String PATH = "/api/v1/auth/me";

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @When("I delete my account with password {string}")
    public void iDeleteMyAccount(String password) {
        scenarioContext.setLastResponse(apiClient.delete(PATH, new JSONObject().put("password", password).toString()));
    }

    @When("I delete my account with password {string} {int} times")
    public void iDeleteMyAccountTimes(String password, int times) {
        for (int i = 0; i < times; i++) {
            iDeleteMyAccount(password);
        }
    }

    @When("I delete my account with the body:")
    public void iDeleteMyAccountWithTheBody(String body) {
        scenarioContext.setLastResponse(apiClient.delete(PATH, body));
    }

    @Then("the user {string} no longer exists")
    public void theUserNoLongerExists(String email) {
        assertThat(appUserRepository.findByEmailIgnoreCase(email)).as("user %s", email).isEmpty();
    }

    @Then("I am no longer logged in")
    public void iAmNoLongerLoggedIn() {
        assertThat(apiClient.get(PATH).getStatus().value()).isEqualTo(401);
    }

    @Then("the database holds {int} vehicle(s), {int} maintenance task(s) and {int} maintenance log(s)")
    public void theDatabaseHolds(int vehicles, int tasks, int logs) {
        assertThat(count("vehicle")).as("vehicles").isEqualTo(vehicles);
        assertThat(count("maintenance_task")).as("maintenance tasks").isEqualTo(tasks);
        assertThat(count("maintenance_log")).as("maintenance logs").isEqualTo(logs);
        assertThat(count("maintenance_log_task")).as("performed tasks of logs").isEqualTo(logs);
        assertThat(count("maintenance_log_material")).as("materials of logs").isEqualTo(logs);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
