package de.chriswohlbrecht.maintenance.cucumber.household;

import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class ProfileSteps {

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    private final JsonMapper objectMapper = new JsonMapper();

    @Given("a profile {string} exists")
    public void aProfileExists(String displayName) {
        EntityExchangeResult<byte[]> response = createProfile(displayName);
        assertThat(response.getStatus().value())
                .as("profile creation for \"%s\" (response: %s)", displayName, new String(response.getResponseBody()))
                .isEqualTo(201);
        scenarioContext.storeId(displayName, objectMapper.readTree(response.getResponseBody()).get("id").asLong());
    }

    @When("I create a profile {string}")
    public void iCreateAProfile(String displayName) {
        scenarioContext.setLastResponse(createProfile(displayName));
    }

    @When("I select the profile {string}")
    public void iSelectTheProfile(String displayName) {
        scenarioContext.setLastResponse(
                apiClient.post("/api/v1/profiles/" + scenarioContext.resolveId(displayName) + "/select", null));
    }

    /** Creates the profile and uses the app as it, like "I am logged in as" in portal mode. */
    @Given("I have selected a new profile {string}")
    public void iHaveSelectedANewProfile(String displayName) {
        aProfileExists(displayName);
        iSelectTheProfile(displayName);
        assertThat(scenarioContext.getLastResponse().getStatus().value())
                .as("select profile %s (response: %s)", displayName, scenarioContext.getLastResponseBodyAsString())
                .isEqualTo(200);
    }

    @Given("the profile {string} is known")
    public void theProfileIsKnown(String displayName) {
        EntityExchangeResult<byte[]> response = apiClient.get("/api/v1/profiles");
        for (JsonNode profile : objectMapper.readTree(response.getResponseBody())) {
            if (displayName.equals(profile.get("displayName").asString())) {
                scenarioContext.storeId(displayName, profile.get("id").asLong());
                return;
            }
        }
        throw new AssertionError("profile " + displayName + " not found in " + new String(response.getResponseBody()));
    }

    private EntityExchangeResult<byte[]> createProfile(String displayName) {
        return apiClient.post("/api/v1/profiles", "{\"displayName\": \"" + displayName + "\"}");
    }
}
