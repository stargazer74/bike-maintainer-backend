Feature: Users only see and change their own vehicles

  Background:
    Given I am logged in as "alice@example.org"
    And a vehicle "alicesBike" exists with body:
      """
      { "name": "Alices Enduro", "type": "MOTORCYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-03-01", "currentMileage": 12000 }
      """
    And a maintenance task "alicesTask" exists for vehicle "alicesBike" with body:
      """
      { "name": "Kette schmieren", "intervalKm": 1000 }
      """
    And a maintenance log "alicesLog" exists for vehicle "alicesBike" with body:
      """
      { "performedAt": "2026-09-01", "mileageAtPerformed": 11500, "performedTaskIds": [{alicesTask}] }
      """
    And I am logged in as "bob@example.org"

  Scenario: Vehicle list only contains own vehicles
    Given a vehicle "bobsCar" exists with body:
      """
      { "name": "Bobs Kombi", "type": "CAR", "modelYear": 2018, "firstRegistrationDate": "2018-06-01", "currentMileage": 80000 }
      """
    When a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 200
    And the response body contains "Bobs Kombi"
    And the response body does not contain "Alices Enduro"

  Scenario Outline: Reading another user's data answers 404
    When a GET request is sent to "<path>"
    Then the response status code is 404

    Examples:
      | path                                                       |
      | /api/v1/vehicles/{alicesBike}                              |
      | /api/v1/vehicles/{alicesBike}/maintenance-tasks            |
      | /api/v1/vehicles/{alicesBike}/maintenance-tasks/{alicesTask} |
      | /api/v1/vehicles/{alicesBike}/maintenance-logs             |
      | /api/v1/vehicles/{alicesBike}/maintenance-logs/{alicesLog} |
      | /api/v1/vehicles/{alicesBike}/maintenance-report           |

  Scenario Outline: Deleting another user's data answers 404
    When a DELETE request is sent to "<path>"
    Then the response status code is 404

    Examples:
      | path                                                       |
      | /api/v1/vehicles/{alicesBike}/maintenance-logs/{alicesLog} |
      | /api/v1/vehicles/{alicesBike}/maintenance-tasks/{alicesTask} |
      | /api/v1/vehicles/{alicesBike}                              |

  Scenario: Updating another user's vehicle answers 404
    When a PUT request is sent to "/api/v1/vehicles/{alicesBike}" with body:
      """
      { "name": "Gekapert", "type": "MOTORCYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-03-01", "currentMileage": 1 }
      """
    Then the response status code is 404

  Scenario: Adding tasks or logs to another user's vehicle answers 404
    When a POST request is sent to "/api/v1/vehicles/{alicesBike}/maintenance-tasks" with body:
      """
      { "name": "Fremde Aufgabe", "intervalKm": 500 }
      """
    Then the response status code is 404
    When a POST request is sent to "/api/v1/vehicles/{alicesBike}/maintenance-logs" with body:
      """
      { "performedAt": "2026-09-02", "mileageAtPerformed": 1 }
      """
    Then the response status code is 404

  Scenario: Updating another user's task or log answers 404
    When a PUT request is sent to "/api/v1/vehicles/{alicesBike}/maintenance-tasks/{alicesTask}" with body:
      """
      { "name": "Gekapert", "intervalKm": 1 }
      """
    Then the response status code is 404
    When a PUT request is sent to "/api/v1/vehicles/{alicesBike}/maintenance-logs/{alicesLog}" with body:
      """
      { "performedAt": "2026-09-01", "mileageAtPerformed": 1 }
      """
    Then the response status code is 404

  Scenario: The owner's data is untouched by the other user's attempts
    When a PUT request is sent to "/api/v1/vehicles/{alicesBike}" with body:
      """
      { "name": "Gekapert", "type": "MOTORCYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-03-01", "currentMileage": 1 }
      """
    And a DELETE request is sent to "/api/v1/vehicles/{alicesBike}"
    And I am logged in as "alice@example.org"
    And a GET request is sent to "/api/v1/vehicles/{alicesBike}"
    Then the response status code is 200
    And the JSON response field "name" equals "Alices Enduro"
    And the JSON response field "currentMileage" equals "12000"
