package de.chriswohlbrecht.maintenance.cucumber;

import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

public class HttpSteps {

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    @When("a GET request is sent to {string}")
    public void aGetRequestIsSentTo(String path) {
        scenarioContext.setLastResponse(apiClient.get(scenarioContext.resolvePath(path)));
    }

    @When("a POST request is sent to {string} with body:")
    public void aPostRequestIsSentToWithBody(String path, String body) {
        scenarioContext.setLastResponse(
                apiClient.post(scenarioContext.resolvePath(path), scenarioContext.resolvePath(body)));
    }

    @When("a PUT request is sent to {string} with body:")
    public void aPutRequestIsSentToWithBody(String path, String body) {
        scenarioContext.setLastResponse(
                apiClient.put(scenarioContext.resolvePath(path), scenarioContext.resolvePath(body)));
    }

    @When("a DELETE request is sent to {string}")
    public void aDeleteRequestIsSentTo(String path) {
        scenarioContext.setLastResponse(apiClient.delete(scenarioContext.resolvePath(path)));
    }
}
