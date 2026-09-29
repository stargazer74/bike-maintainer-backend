package de.chriswohlbrecht.maintenance.cucumber;

import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import io.cucumber.java.en.Given;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class FixtureSteps {

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    private final JsonMapper objectMapper = new JsonMapper();

    @Given("a vehicle {string} exists with body:")
    public void aVehicleExistsWithBody(String alias, String body) {
        EntityExchangeResult<byte[]> response = apiClient.post("/api/v1/vehicles", scenarioContext.resolvePath(body));
        assertCreated(response, "vehicle", alias);
        scenarioContext.storeId(alias, extractId(response));
    }

    @Given("a maintenance task {string} exists for vehicle {string} with body:")
    public void aMaintenanceTaskExistsForVehicleWithBody(String alias, String vehicleAlias, String body) {
        String path = "/api/v1/vehicles/" + scenarioContext.resolveId(vehicleAlias) + "/maintenance-tasks";
        EntityExchangeResult<byte[]> response = apiClient.post(path, scenarioContext.resolvePath(body));
        assertCreated(response, "maintenance task", alias);
        scenarioContext.storeId(alias, extractId(response));
    }

    @Given("a maintenance log {string} exists for vehicle {string} with body:")
    public void aMaintenanceLogExistsForVehicleWithBody(String alias, String vehicleAlias, String body) {
        String path = "/api/v1/vehicles/" + scenarioContext.resolveId(vehicleAlias) + "/maintenance-logs";
        EntityExchangeResult<byte[]> response = apiClient.post(path, scenarioContext.resolvePath(body));
        assertCreated(response, "maintenance log", alias);
        scenarioContext.storeId(alias, extractId(response));
    }

    private void assertCreated(EntityExchangeResult<byte[]> response, String entityName, String alias) {
        assertThat(response.getStatus().value())
                .as("%s fixture creation for alias \"%s\" (response: %s)",
                        entityName, alias, new String(response.getResponseBody()))
                .isEqualTo(201);
    }

    private Long extractId(EntityExchangeResult<byte[]> response) {
        return objectMapper.readTree(response.getResponseBody()).get("id").asLong();
    }
}
