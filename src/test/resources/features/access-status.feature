Feature: Access status – trial, subscription and read-only accounts (portal mode)

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "bike" exists with body:
      """
      { "name": "Bike", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-03-01", "currentMileage": 1000 }
      """
    And a maintenance task "oil" exists for vehicle "bike" with body:
      """
      { "name": "Oil change", "intervalKm": 6000 }
      """

  Scenario: A verified account is in its trial
    When a GET request is sent to "/api/v1/auth/me"
    Then the response status code is 200
    And the JSON response field "accessStatus" equals "TRIAL"
    And the JSON response field "hasSubscription" equals "false"

  Scenario: The initial admin is never restricted
    When I log in as "admin@localhost" with password "initial-admin-password"
    And a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "FULL"

  Scenario: After the trial the account is read-only
    Given the trial of "rider@example.org" has ended
    When a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "EXPIRED"
    When a POST request is sent to "/api/v1/vehicles" with body:
      """
      { "name": "Second", "type": "CAR", "modelYear": 2021, "firstRegistrationDate": "2021-01-01", "currentMileage": 10 }
      """
    Then the response status code is 403
    And the JSON response field "code" equals "SUBSCRIPTION_REQUIRED"

  Scenario Outline: A read-only account cannot write vehicle data
    Given the trial of "rider@example.org" has ended
    When a <method> request is sent to "<path>" with body:
      """
      <body>
      """
    Then the response status code is 403
    And the JSON response field "code" equals "SUBSCRIPTION_REQUIRED"

    Examples:
      | method | path                                         | body                                                                                                           |
      | PUT    | /api/v1/vehicles/{bike}                      | { "name": "Renamed", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-03-01", "currentMileage": 2000 } |
      | POST   | /api/v1/vehicles/{bike}/maintenance-tasks    | { "name": "Chain", "intervalKm": 1000 }                                                                        |
      | PUT    | /api/v1/vehicles/{bike}/maintenance-tasks/{oil} | { "name": "Oil", "intervalKm": 5000 }                                                                       |
      | POST   | /api/v1/vehicles/{bike}/maintenance-logs     | { "performedAt": "2026-01-10", "mileageAtPerformed": 1500, "performedTaskIds": [] }                            |
      | PUT    | /api/v1/vehicles/{bike}/specifications       | { "cylinders": 2 }                                                                                             |

  Scenario: A read-only account cannot download worksheets or import vehicles
    Given a GET request is sent to "/api/v1/vehicles/{bike}/export"
    And the response body is remembered as "file"
    And the trial of "rider@example.org" has ended
    When a GET request is sent to "/api/v1/vehicles/{bike}/worksheets/TIRES"
    Then the response status code is 403
    And the JSON response field "code" equals "SUBSCRIPTION_REQUIRED"
    When the remembered file "file" is imported
    Then the response status code is 403
    And the JSON response field "code" equals "SUBSCRIPTION_REQUIRED"

  Scenario: A read-only account can still read, export, print the report, change settings and delete
    Given the trial of "rider@example.org" has ended
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks"
    Then the response status code is 200
    When a GET request is sent to "/api/v1/vehicles/{bike}/export"
    Then the response status code is 200
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-report"
    Then the response status code is 200
    When a PATCH request is sent to "/api/v1/auth/me" with body:
      """
      { "language": "en" }
      """
    Then the response status code is 200
    When a DELETE request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks/{oil}"
    Then the response status code is 204
    When a DELETE request is sent to "/api/v1/vehicles/{bike}"
    Then the response status code is 204

  Scenario: A subscription allows writing after the trial
    Given the trial of "rider@example.org" has ended
    And "rider@example.org" has a subscription paid until in 300 days
    When a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "ACTIVE"
    And the JSON response field "hasSubscription" equals "true"
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-tasks" with body:
      """
      { "name": "Chain", "intervalKm": 1000 }
      """
    Then the response status code is 201

  Scenario: An ended subscription leaves the account read-only
    Given the trial of "rider@example.org" has ended
    And "rider@example.org" had a subscription that ended 2 days ago
    When a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "EXPIRED"
    And the JSON response field "hasSubscription" equals "true"
