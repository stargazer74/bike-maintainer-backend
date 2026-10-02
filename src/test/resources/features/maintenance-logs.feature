Feature: Maintenance log management

  Background:
    Given a vehicle "bike" exists with body:
      """
      { "name": "Adventure Bike", "type": "MOTORCYCLE", "modelYear": 2022, "firstRegistrationDate": "2022-01-01", "currentMileage": 8000 }
      """
    And a maintenance task "oilChange" exists for vehicle "bike" with body:
      """
      { "name": "Oil change", "intervalKm": 5000 }
      """

  Scenario: Create a maintenance log without performed tasks
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      {
        "performedAt": "2026-01-15",
        "mileageAtPerformed": 8200,
        "notes": "Routine check"
      }
      """
    Then the response status code is 201
    And the JSON response field "mileageAtPerformed" equals "8200"
    And the JSON response field "vehicleId" equals "{bike}"

  Scenario: Create a maintenance log with performed tasks
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      {
        "performedAt": "2026-02-01",
        "mileageAtPerformed": 8500,
        "performedTaskIds": [{oilChange}]
      }
      """
    Then the response status code is 201
    And the JSON response array field "performedTaskIds" contains "{oilChange}"

  Scenario: Reject creating a maintenance log with an unknown task id
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      {
        "performedAt": "2026-02-01",
        "mileageAtPerformed": 8500,
        "performedTaskIds": [999999]
      }
      """
    Then the response status code is 400

  Scenario: Reject creating a maintenance log for an unknown vehicle
    When a POST request is sent to "/api/v1/vehicles/999999/maintenance-logs" with body:
      """
      { "performedAt": "2026-02-01", "mileageAtPerformed": 100 }
      """
    Then the response status code is 404

  Scenario: Reject creating a maintenance log with negative mileage
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      { "performedAt": "2026-02-01", "mileageAtPerformed": -5 }
      """
    Then the response status code is 400

  Scenario: Retrieve an existing maintenance log
    Given a maintenance log "service" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-01-01", "mileageAtPerformed": 8100 }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{service}"
    Then the response status code is 200
    And the JSON response field "mileageAtPerformed" equals "8100"

  Scenario: Retrieve a maintenance log that does not exist
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/999999"
    Then the response status code is 404

  Scenario: List maintenance logs of a vehicle
    Given a maintenance log "listed" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-01-01", "mileageAtPerformed": 8100, "notes": "Listed log entry" }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-logs"
    Then the response status code is 200
    And the response body contains "Listed log entry"

  Scenario: Update an existing maintenance log
    Given a maintenance log "toUpdate" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-01-01", "mileageAtPerformed": 8100 }
      """
    When a PUT request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{toUpdate}" with body:
      """
      { "performedAt": "2026-01-02", "mileageAtPerformed": 8150, "notes": "Updated" }
      """
    Then the response status code is 200
    And the JSON response field "mileageAtPerformed" equals "8150"
    And the JSON response field "notes" equals "Updated"

  Scenario: Delete an existing maintenance log
    Given a maintenance log "toDelete" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-01-01", "mileageAtPerformed": 8100 }
      """
    When a DELETE request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{toDelete}"
    Then the response status code is 204
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{toDelete}"
    Then the response status code is 404

  Scenario: Delete a maintenance log that does not exist
    When a DELETE request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/999999"
    Then the response status code is 404
