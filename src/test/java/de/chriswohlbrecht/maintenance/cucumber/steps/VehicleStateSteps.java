package de.chriswohlbrecht.maintenance.cucumber.steps;

import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the stored state of a vehicle through the API, e.g. after a request that should have changed it. */
public class VehicleStateSteps {

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    private final JsonMapper objectMapper = new JsonMapper();

    @Then("the vehicle {string} has {int} maintenance task(s)")
    public void theVehicleHasMaintenanceTasks(String vehicleAlias, int count) {
        assertThat(list(vehicleAlias, "maintenance-tasks")).hasSize(count);
    }

    @Then("the vehicle {string} has {int} maintenance log(s)")
    public void theVehicleHasMaintenanceLogs(String vehicleAlias, int count) {
        assertThat(list(vehicleAlias, "maintenance-logs")).hasSize(count);
    }

    /** Every row names a task by its name and lists the fields it must have, e.g. | name | status | oneTime |. */
    @Then("the vehicle {string} has the maintenance tasks:")
    public void theVehicleHasTheMaintenanceTasks(String vehicleAlias, DataTable table) {
        List<JsonNode> tasks = list(vehicleAlias, "maintenance-tasks");
        for (Map<String, String> row : table.asMaps()) {
            JsonNode task = tasks.stream()
                    .filter(candidate -> candidate.get("name").asText().equals(row.get("name")))
                    .findFirst()
                    .orElse(null);
            assertThat(task).as("task \"%s\" in %s", row.get("name"), tasks).isNotNull();
            for (Map.Entry<String, String> field : row.entrySet()) {
                JsonNode value = task.get(field.getKey());
                String actual = value == null || value.isNull() ? "" : value.asText();
                assertThat(actual).as("field \"%s\" of task \"%s\"", field.getKey(), row.get("name"))
                        .isEqualTo(field.getValue() == null ? "" : scenarioContext.resolvePath(field.getValue()));
            }
        }
    }

    private List<JsonNode> list(String vehicleAlias, String resource) {
        String path = "/api/v1/vehicles/" + scenarioContext.resolveId(vehicleAlias) + "/" + resource;
        EntityExchangeResult<byte[]> response = apiClient.get(path);
        assertThat(response.getStatus().value()).as("GET %s", path).isEqualTo(200);
        List<JsonNode> result = new ArrayList<>();
        objectMapper.readTree(response.getResponseBody()).forEach(result::add);
        return result;
    }
}
