Feature: Maintenance task management

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "bike" exists with body:
      """
      { "name": "Trail Bike", "type": "MOTORCYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-01-01", "currentMileage": 5000 }
      """

  Scenario: Create a maintenance task for a vehicle
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks" with body:
      """
      {
        "name": "Oil change",
        "description": "Change engine oil and filter",
        "intervalKm": 5000,
        "intervalMonths": 12
      }
      """
    Then the response status code is 201
    And the JSON response field "name" equals "Oil change"
    And the JSON response field "vehicleId" equals "{bike}"

  Scenario: Reject creating a maintenance task for an unknown vehicle
    When a POST request is sent to "/api/v1/vehicles/999999/maintenance-tasks" with body:
      """
      { "name": "Oil change" }
      """
    Then the response status code is 404

  Scenario: Reject creating a maintenance task without a name
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks" with body:
      """
      { "description": "Missing name", "oneTime": true }
      """
    Then the response status code is 400

  Scenario: Reject creating a maintenance task without an interval unless it is one-time
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks" with body:
      """
      { "name": "Undefined interval task" }
      """
    Then the response status code is 400

  Scenario: Create a one-time maintenance task without an interval
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks" with body:
      """
      { "name": "Replace battery", "oneTime": true }
      """
    Then the response status code is 201
    And the JSON response field "oneTime" equals "true"

  Scenario: Retrieve an existing maintenance task
    Given a maintenance task "oilChange" exists for vehicle "bike" with body:
      """
      { "name": "Oil change", "intervalKm": 5000 }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{oilChange}"
    Then the response status code is 200
    And the JSON response field "name" equals "Oil change"

  Scenario: Retrieve a maintenance task that does not exist
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/999999"
    Then the response status code is 404

  Scenario: List maintenance tasks of a vehicle
    Given a maintenance task "brakes" exists for vehicle "bike" with body:
      """
      { "name": "Check brakes", "intervalKm": 2000 }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks"
    Then the response status code is 200
    And the response body contains "Check brakes"

  Scenario: Update an existing maintenance task
    Given a maintenance task "chain" exists for vehicle "bike" with body:
      """
      { "name": "Lube chain", "intervalKm": 500 }
      """
    When a PUT request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{chain}" with body:
      """
      { "name": "Lube and clean chain", "intervalKm": 600 }
      """
    Then the response status code is 200
    And the JSON response field "name" equals "Lube and clean chain"

  Scenario: Delete an existing maintenance task
    Given a maintenance task "toDelete" exists for vehicle "bike" with body:
      """
      { "name": "Disposable task", "oneTime": true }
      """
    When a DELETE request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{toDelete}"
    Then the response status code is 204
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{toDelete}"
    Then the response status code is 404

  Scenario: Reject deleting a maintenance task that was already performed
    Given a maintenance task "performedTask" exists for vehicle "bike" with body:
      """
      { "name": "Chain lube", "intervalKm": 1000 }
      """
    And a maintenance log "performedLog" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-01-15", "mileageAtPerformed": 4000, "performedTaskIds": [{performedTask}] }
      """
    When a DELETE request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{performedTask}"
    Then the response status code is 409
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{performedTask}"
    Then the response status code is 200

  Scenario: Delete a maintenance task that does not exist
    When a DELETE request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/999999"
    Then the response status code is 404

  Scenario: A never-performed task is overdue once the vehicle has passed its interval
    Given a maintenance task "overdueOil" exists for vehicle "bike" with body:
      """
      { "name": "Oil change", "intervalKm": 1000 }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{overdueOil}"
    Then the response status code is 200
    And the JSON response field "status" equals "OVERDUE"

  Scenario: A never-performed task is ok while well within its interval
    Given a maintenance task "freshFilter" exists for vehicle "bike" with body:
      """
      { "name": "Air filter", "intervalKm": 20000 }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{freshFilter}"
    Then the response status code is 200
    And the JSON response field "status" equals "OK"

  Scenario: Update a maintenance task that does not exist
    When a PUT request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/999999" with body:
      """
      { "name": "Ghost task", "intervalKm": 1000 }
      """
    Then the response status code is 404

  Scenario Outline: Reject updating a maintenance task with invalid values
    Given a maintenance task "chain" exists for vehicle "bike" with body:
      """
      { "name": "Lube chain", "intervalKm": 500 }
      """
    When a PUT request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{chain}" with body:
      """
      <body>
      """
    Then the response status code is 400
    And the vehicle "bike" has the maintenance tasks:
      | name       | intervalKm |
      | Lube chain | 500        |

    Examples:
      | case                | body                                         |
      | blank name          | { "name": "", "intervalKm": 500 }            |
      | zero km interval    | { "name": "Lube chain", "intervalKm": 0 }    |
      | zero month interval | { "name": "Lube chain", "intervalMonths": 0 } |
      | no interval         | { "name": "Lube chain" }                     |

  Scenario: A deactivated task stays in the history but cannot go on the checklist
    Given a maintenance task "chain" exists for vehicle "bike" with body:
      """
      { "name": "Lube chain", "intervalKm": 1000 }
      """
    And a maintenance log "lubed" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-01-15", "mileageAtPerformed": 4800, "performedTaskIds": [{chain}] }
      """
    When a PUT request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{chain}" with body:
      """
      { "name": "Lube chain", "intervalKm": 1000, "active": false }
      """
    Then the response status code is 200
    And the JSON response field "active" equals "false"
    And the vehicle "bike" has the maintenance tasks:
      | name       | active |
      | Lube chain | false  |
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{lubed}"
    Then the JSON response array field "performedTaskIds" contains "{chain}"
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-checklist?taskId={chain}"
    Then the response status code is 400

  Scenario: A task is due soon within 1000 km of its interval
    Given a maintenance task "oil" exists for vehicle "bike" with body:
      """
      { "name": "Oil change", "intervalKm": 5500 }
      """
    Then the vehicle "bike" has the maintenance tasks:
      | name       | status   | kmRemaining |
      | Oil change | DUE_SOON | 500         |

  Scenario: A task with an interval in months is measured from the first registration
    Given a vehicle "newBike" exists with body:
      """
      { "name": "New Bike", "type": "MOTORCYCLE", "modelYear": 2026, "firstRegistrationDate": "{today-20d}", "currentMileage": 0 }
      """
    And a maintenance task "inspection" exists for vehicle "newBike" with body:
      """
      { "name": "First inspection", "intervalMonths": 1 }
      """
    And a maintenance task "service" exists for vehicle "newBike" with body:
      """
      { "name": "Annual service", "intervalMonths": 12 }
      """
    And a maintenance task "tyres" exists for vehicle "bike" with body:
      """
      { "name": "Tyre check", "intervalMonths": 12 }
      """
    Then the vehicle "newBike" has the maintenance tasks:
      | name             | status   |
      | First inspection | DUE_SOON |
      | Annual service   | OK       |
    And the vehicle "bike" has the maintenance tasks:
      | name       | status  |
      | Tyre check | OVERDUE |

  Scenario: Whichever comes first: the time interval makes a task overdue despite low mileage
    Given a maintenance task "service" exists for vehicle "bike" with body:
      """
      { "name": "Service", "intervalKm": 20000, "intervalMonths": 12 }
      """
    Then the vehicle "bike" has the maintenance tasks:
      | name    | status  |
      | Service | OVERDUE |

  Scenario: Whichever comes first: the mileage makes a task overdue despite recent maintenance
    Given a maintenance task "service" exists for vehicle "bike" with body:
      """
      { "name": "Service", "intervalKm": 1000, "intervalMonths": 24 }
      """
    And a maintenance log "lastService" exists for vehicle "bike" with body:
      """
      { "performedAt": "{today-30d}", "mileageAtPerformed": 3500, "performedTaskIds": [{service}] }
      """
    Then the vehicle "bike" has the maintenance tasks:
      | name    | status  | kmRemaining |
      | Service | OVERDUE | -500        |

  Scenario: Recording maintenance resets the status of the task
    Given a maintenance task "oil" exists for vehicle "bike" with body:
      """
      { "name": "Oil change", "intervalKm": 3000 }
      """
    And the vehicle "bike" has the maintenance tasks:
      | name       | status  |
      | Oil change | OVERDUE |
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      { "performedAt": "{today}", "mileageAtPerformed": 5000, "performedTaskIds": [{oil}] }
      """
    Then the response status code is 201
    And the vehicle "bike" has the maintenance tasks:
      | name       | status | kmRemaining | lastServiceMileage | lastServiceDate |
      | Oil change | OK     | 3000        | 5000               | {today}         |

  Scenario: A performed one-time task is done
    Given a maintenance task "battery" exists for vehicle "bike" with body:
      """
      { "name": "Replace battery", "oneTime": true }
      """
    And a maintenance log "replaced" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-01-15", "mileageAtPerformed": 4900, "performedTaskIds": [{battery}] }
      """
    Then the vehicle "bike" has the maintenance tasks:
      | name            | status |
      | Replace battery | DONE   |
