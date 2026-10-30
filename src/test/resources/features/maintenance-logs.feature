Feature: Maintenance log management

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "bike" exists with body:
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

  Scenario: New work without an interval becomes a one-time task done by the log
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      {
        "performedAt": "2026-03-01",
        "mileageAtPerformed": 8600,
        "performedTaskIds": [{oilChange}],
        "newTasks": [ { "name": "Bremsleitung getauscht", "description": "Stahlflex vorne" } ]
      }
      """
    Then the response status code is 201
    And the JSON response array field "performedTaskIds" contains "{oilChange}"
    And the vehicle "bike" has 2 maintenance tasks
    And the vehicle "bike" has the maintenance tasks:
      | name                  | description     | oneTime | status | lastServiceMileage |
      | Bremsleitung getauscht | Stahlflex vorne | true    | DONE   | 8600               |
      | Oil change            |                 | false   | OK     | 8600               |

  Scenario: New work with an interval becomes a recurring task, the log counts as its first performance
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      {
        "performedAt": "2026-03-01",
        "mileageAtPerformed": 8000,
        "newTasks": [
          { "name": "Kette reinigen", "intervalKm": 5000 },
          { "name": "Spiegel ersetzt" }
        ]
      }
      """
    Then the response status code is 201
    And the vehicle "bike" has the maintenance tasks:
      | name            | oneTime | intervalKm | lastServiceMileage | kmRemaining | status |
      | Kette reinigen  | false   | 5000       | 8000               | 5000        | OK     |
      | Spiegel ersetzt | true    |            | 8000               |             | DONE   |

  Scenario: Editing a log can add new work as well
    Given a maintenance log "service" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-03-01", "mileageAtPerformed": 8600, "performedTaskIds": [{oilChange}] }
      """
    When a PUT request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{service}" with body:
      """
      {
        "performedAt": "2026-03-01",
        "mileageAtPerformed": 8600,
        "performedTaskIds": [{oilChange}],
        "newTasks": [ { "name": "Spiegel ersetzt" } ]
      }
      """
    Then the response status code is 200
    And the vehicle "bike" has the maintenance tasks:
      | name            | status |
      | Spiegel ersetzt | DONE   |

  Scenario Outline: An invalid request saves neither the log nor any new task
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      { "performedAt": "2026-03-01", "mileageAtPerformed": 8600, <fields> }
      """
    Then the response status code is 400
    And the vehicle "bike" has 1 maintenance task
    And the vehicle "bike" has 0 maintenance logs

    Examples:
      | fields                                                                                  |
      | "performedTaskIds": [999999], "newTasks": [ { "name": "Spiegel ersetzt" } ]             |
      | "newTasks": [ { "name": "Spiegel ersetzt" }, { "name": "" } ]                           |
      | "newTasks": [ { "name": "Spiegel ersetzt" }, { "name": "Kette", "intervalKm": 0 } ]     |
      | "newTasks": [ { "name": "Spiegel ersetzt", "description": "" } ]                        |

  Scenario: Deleting the log leaves the new task, open again
    Given a maintenance log "repair" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-03-01", "mileageAtPerformed": 8600, "newTasks": [ { "name": "Spiegel ersetzt" } ] }
      """
    When a DELETE request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{repair}"
    Then the response status code is 204
    And the vehicle "bike" has the maintenance tasks:
      | name            | oneTime | lastServiceMileage |
      | Spiegel ersetzt | true    |                    |

  Scenario: New work for another user's vehicle answers 404
    Given I am logged in as "bob@example.org"
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      { "performedAt": "2026-03-01", "mileageAtPerformed": 8600, "newTasks": [ { "name": "Fremd" } ] }
      """
    Then the response status code is 404
