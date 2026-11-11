package de.chriswohlbrecht.maintenance.cucumber.steps;

import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

/** Export and import of a vehicle (Epic #82): keeping an export, importing it again and comparing the files. */
public class VehicleExportSteps {

    private static final String IMPORT_PATH = "/api/v1/vehicles/import";

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    private final JsonMapper objectMapper = new JsonMapper();

    @Given("the response body is remembered as {string}")
    public void theResponseBodyIsRememberedAs(String alias) {
        scenarioContext.storeText(alias, scenarioContext.getLastResponseBodyAsString());
    }

    @When("the remembered file {string} is imported")
    public void theRememberedFileIsImported(String alias) {
        scenarioContext.setLastResponse(apiClient.post(IMPORT_PATH, scenarioContext.resolveText(alias)));
    }

    /** Stores the id of the created vehicle, so later steps can use it like a fixture. */
    @Given("the remembered file {string} is imported as vehicle {string}")
    public void theRememberedFileIsImportedAsVehicle(String alias, String vehicleAlias) {
        EntityExchangeResult<byte[]> response = apiClient.post(IMPORT_PATH, scenarioContext.resolveText(alias));
        scenarioContext.setLastResponse(response);
        assertThat(response.getStatus().value()).as("import (response: %s)", new String(response.getResponseBody()))
                .isEqualTo(201);
        scenarioContext.storeId(vehicleAlias, objectMapper.readTree(response.getResponseBody()).get("id").asLong());
    }

    @Then("the created vehicle is stored as {string}")
    public void theCreatedVehicleIsStoredAs(String vehicleAlias) {
        scenarioContext.storeId(vehicleAlias,
                objectMapper.readTree(scenarioContext.getLastResponseBodyAsString()).get("id").asLong());
    }

    /** The vehicle part of two exports, i.e. everything apart from the export time. */
    @Then("the JSON response value at {string} equals the one of the remembered file {string}")
    public void theJsonResponseValueAtEqualsTheOneOfTheRememberedFile(String pointer, String alias) {
        JsonNode actual = objectMapper.readTree(scenarioContext.getLastResponseBodyAsString()).at(pointer);
        JsonNode expected = objectMapper.readTree(scenarioContext.resolveText(alias)).at(pointer);
        assertThat(expected.isMissingNode()).as("value at \"%s\" in remembered file", pointer).isFalse();
        assertThat(actual).isEqualTo(expected);
    }

    @When("an import file with {int} maintenance tasks is sent")
    public void anImportFileWithMaintenanceTasksIsSent(int count) {
        ObjectNode file = importFile();
        ArrayNode tasks = ((ObjectNode) file.get("vehicle")).putArray("maintenanceTasks");
        for (int i = 1; i <= count; i++) {
            tasks.addObject().put("ref", "t" + i).put("name", "Task " + i).put("intervalKm", 1000);
        }
        scenarioContext.setLastResponse(apiClient.post(IMPORT_PATH, file.toString()));
    }

    @When("an import file of {int} bytes is sent")
    public void anImportFileOfBytesIsSent(int bytes) {
        ObjectNode file = importFile();
        String withoutPadding = file.put("padding", "").toString();
        file.put("padding", "x".repeat(bytes - withoutPadding.length()));
        scenarioContext.setLastResponse(apiClient.post(IMPORT_PATH, file.toString()));
    }

    private ObjectNode importFile() {
        ObjectNode file = objectMapper.createObjectNode()
                .put("formatVersion", 1)
                .put("exportedAt", "2026-11-11T10:00:00Z");
        file.putObject("vehicle")
                .put("name", "Generated")
                .put("type", "MOTORCYCLE")
                .put("modelYear", 2020)
                .put("firstRegistrationDate", "2020-04-01")
                .put("currentMileage", 0);
        return file;
    }
}
