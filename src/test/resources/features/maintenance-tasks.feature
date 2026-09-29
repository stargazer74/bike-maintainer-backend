Feature: Maintenance task management

  Background:
    Given a vehicle "bike" exists with body:
      """
      { "name": "Trail Bike", "type": "MOTORCYCLE", "modelYear": 2021, "currentMileage": 5000 }
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

  Scenario: Delete a maintenance task that does not exist
    When a DELETE request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/999999"
    Then the response status code is 404
