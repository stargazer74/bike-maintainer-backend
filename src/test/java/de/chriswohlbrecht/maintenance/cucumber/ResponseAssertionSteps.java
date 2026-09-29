package de.chriswohlbrecht.maintenance.cucumber;

import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ResponseAssertionSteps {

    @Autowired
    private ScenarioContext scenarioContext;

    private final JsonMapper objectMapper = new JsonMapper();

    @Then("the response status code is {int}")
    public void theResponseStatusCodeIs(int expectedStatus) {
        assertThat(scenarioContext.getLastResponse().getStatus().value()).isEqualTo(expectedStatus);
    }

    @Then("the JSON response field {string} equals {string}")
    public void theJsonResponseFieldEquals(String field, String expectedValue) {
        JsonNode node = parseLastResponseBody();
        assertThat(node.get(field)).as("field \"%s\" in response body: %s", field, node).isNotNull();
        assertThat(node.get(field).asText()).isEqualTo(scenarioContext.resolvePath(expectedValue));
    }

    @Then("the JSON response array field {string} contains {string}")
    public void theJsonResponseArrayFieldContains(String field, String expectedValue) {
        JsonNode array = parseLastResponseBody().get(field);
        assertThat(array).as("array field \"%s\"", field).isNotNull();
        String resolvedExpected = scenarioContext.resolvePath(expectedValue);
        boolean found = false;
        for (JsonNode element : array) {
            if (element.asText().equals(resolvedExpected)) {
                found = true;
                break;
            }
        }
        assertThat(found).as("array field \"%s\" to contain \"%s\"", field, resolvedExpected).isTrue();
    }

    @Then("the response body contains {string}")
    public void theResponseBodyContains(String expectedSubstring) {
        assertThat(scenarioContext.getLastResponseBodyAsString())
                .contains(scenarioContext.resolvePath(expectedSubstring));
    }

    @Then("the response body is empty")
    public void theResponseBodyIsEmpty() {
        assertThat(scenarioContext.getLastResponseBodyAsString()).isEmpty();
    }

    @Then("the response content type is {string}")
    public void theResponseContentTypeIs(String expectedContentType) {
        assertThat(scenarioContext.getLastResponse().getResponseHeaders().getFirst("Content-Type"))
                .contains(expectedContentType);
    }

    @Then("the response header {string} contains {string}")
    public void theResponseHeaderContains(String headerName, String expectedValue) {
        assertThat(scenarioContext.getLastResponse().getResponseHeaders().getFirst(headerName))
                .contains(scenarioContext.resolvePath(expectedValue));
    }

    @Then("the response body is a valid PDF document")
    public void theResponseBodyIsAValidPdfDocument() {
        byte[] body = scenarioContext.getLastResponse().getResponseBody();
        assertThat(body).isNotEmpty();
        assertThat(new String(body, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    private JsonNode parseLastResponseBody() {
        return objectMapper.readTree(scenarioContext.getLastResponseBodyAsString());
    }
}
