Feature: Vehicle templates – maintenance plans from the catalog (Epic #150)

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "guzzi" exists with body:
      """
      { "name": "Guzzi", "type": "MOTORCYCLE", "make": "Moto Guzzi", "model": "V7", "modelYear": 2020, "firstRegistrationDate": "2020-03-01", "currentMileage": 5000 }
      """

  Scenario Outline: The search finds a template regardless of spelling
    When a GET request is sent to "/api/v1/vehicle-templates?make=<make>&model=<model>"
    Then the response status code is 200
    And the JSON response array has 1 element
    And the response body contains "moto-guzzi-v7-2012"

    Examples:
      | make       | model |
      | moto guzzi | V7    |
      | MOTO-GUZZI | V 7   |

  Scenario: The detail view is in the user's language
    When a GET request is sent to "/api/v1/vehicle-templates/moto-guzzi-v7-2012"
    Then the response status code is 200
    And the response body contains "Eigene Bremsflüssigkeit"
    Given a PATCH request is sent to "/api/v1/auth/me" with body:
      """
      { "language": "en" }
      """
    When a GET request is sent to "/api/v1/vehicle-templates/moto-guzzi-v7-2012"
    Then the response body contains "Own brake fluid"

  Scenario: An unknown template answers 404
    When a GET request is sent to "/api/v1/vehicle-templates/does-not-exist"
    Then the response status code is 404

  Scenario: Applying a template creates the tasks with their template key
    When a POST request is sent to "/api/v1/vehicles/{guzzi}/template" with body:
      """
      { "templateId": "moto-guzzi-v7-2012", "tasks": [ { "key": "brake-fluid" } ] }
      """
    Then the response status code is 200
    And the JSON response field "created" equals "1"
    And the JSON response field "skipped" equals "0"
    When a GET request is sent to "/api/v1/vehicles/{guzzi}/maintenance-tasks"
    Then the JSON response array has 1 element
    And the response body contains "\"templateKey\":\"brake-fluid\""

  Scenario: Applying the same template again creates nothing
    Given a POST request is sent to "/api/v1/vehicles/{guzzi}/template" with body:
      """
      { "templateId": "moto-guzzi-v7-2012", "tasks": [ { "key": "brake-fluid" } ] }
      """
    When a POST request is sent to "/api/v1/vehicles/{guzzi}/template" with body:
      """
      { "templateId": "moto-guzzi-v7-2012", "tasks": [ { "key": "brake-fluid" } ] }
      """
    Then the response status code is 200
    And the JSON response field "created" equals "0"
    And the JSON response field "skipped" equals "1"
    When a GET request is sent to "/api/v1/vehicles/{guzzi}/maintenance-tasks"
    Then the JSON response array has 1 element

  Scenario: An empty task list is rejected
    When a POST request is sent to "/api/v1/vehicles/{guzzi}/template" with body:
      """
      { "templateId": "moto-guzzi-v7-2012", "tasks": [] }
      """
    Then the response status code is 400

  Scenario: A read-only account cannot apply a template
    Given the trial of "rider@example.org" has ended
    When a POST request is sent to "/api/v1/vehicles/{guzzi}/template" with body:
      """
      { "templateId": "moto-guzzi-v7-2012", "tasks": [ { "key": "brake-fluid" } ] }
      """
    Then the response status code is 403
    And the JSON response field "code" equals "SUBSCRIPTION_REQUIRED"

  Scenario: Applying a template to another user's vehicle answers 404
    Given I am logged in as "other@example.org"
    When a POST request is sent to "/api/v1/vehicles/{guzzi}/template" with body:
      """
      { "templateId": "moto-guzzi-v7-2012", "tasks": [ { "key": "brake-fluid" } ] }
      """
    Then the response status code is 404
