Feature: Maintenance cost summary

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "bike" exists with body:
      """
      { "name": "Adventure Bike", "type": "MOTORCYCLE", "modelYear": 2022, "firstRegistrationDate": "2022-01-01", "currentMileage": 8000 }
      """

  Scenario: No costs yield an empty summary
    Given a maintenance log "plain" exists for vehicle "bike" with body:
      """
      { "performedAt": "2020-01-15", "mileageAtPerformed": 8200 }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/cost-summary"
    Then the response status code is 200
    And the response body contains "[]"

  Scenario: Costs are summed per currency
    Given a maintenance log "first" exists for vehicle "bike" with body:
      """
      { "performedAt": "2020-01-15", "mileageAtPerformed": 8200, "laborCost": 60.50,
        "materials": [ { "name": "Oil", "quantity": 4, "unit": "LITER", "price": 39.50 } ] }
      """
    And a maintenance log "second" exists for vehicle "bike" with body:
      """
      { "performedAt": "2020-02-15", "mileageAtPerformed": 8300, "laborCost": 50 }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/cost-summary"
    Then the response status code is 200
    And the response body contains "\"currency\":\"EUR\""
    And the response body contains "\"total\":150.00"
    And the response body contains "\"currentYear\":0.00"

  Scenario: Reject the cost summary of an unknown vehicle
    When a GET request is sent to "/api/v1/vehicles/999999/cost-summary"
    Then the response status code is 404

  Scenario: The cost summary of another user's vehicle answers 404
    Given I am logged in as "other@example.org"
    When a GET request is sent to "/api/v1/vehicles/{bike}/cost-summary"
    Then the response status code is 404

  Scenario: Costs are summed per currency and split into the current year
    Given a maintenance log "lastYear" exists for vehicle "bike" with body:
      """
      { "performedAt": "2020-06-01", "mileageAtPerformed": 8100, "laborCost": 100 }
      """
    And a maintenance log "thisYear" exists for vehicle "bike" with body:
      """
      { "performedAt": "{today}", "mileageAtPerformed": 8200, "laborCost": 40.25 }
      """
    When a PATCH request is sent to "/api/v1/auth/me" with body:
      """
      { "currency": "CHF" }
      """
    And a maintenance log "swiss" exists for vehicle "bike" with body:
      """
      { "performedAt": "{today}", "mileageAtPerformed": 8300,
        "materials": [ { "name": "Brake pads", "quantity": 2, "unit": "PIECE", "price": 35 } ] }
      """
    And a GET request is sent to "/api/v1/vehicles/{bike}/cost-summary"
    Then the response status code is 200
    And the JSON response array has 2 elements
    And the JSON response array contains the elements:
      | currency | total  | currentYear |
      | EUR      | 140.25 | 40.25       |
      | CHF      | 35.00  | 35.00       |
