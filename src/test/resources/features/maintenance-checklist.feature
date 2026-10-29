Feature: Maintenance checklist download

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "bike" exists with body:
      """
      { "name": "Tourer", "type": "MOTORCYCLE", "make": "BMW", "model": "R1250GS", "modelYear": 2022, "firstRegistrationDate": "2022-01-01", "currentMileage": 20000 }
      """
    And a maintenance task "chain" exists for vehicle "bike" with body:
      """
      { "name": "Kette schmieren", "intervalKm": 30000 }
      """
    And a maintenance task "oil" exists for vehicle "bike" with body:
      """
      { "name": "Oelwechsel", "intervalKm": 10000 }
      """
    And a maintenance task "valves" exists for vehicle "bike" with body:
      """
      { "name": "Ventilspiel", "intervalKm": 20500 }
      """

  Scenario: The checklist lists the selected tasks, most urgent first
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-checklist?taskId={chain}&taskId={oil}&taskId={valves}"
    Then the response status code is 200
    And the response content type is "application/pdf"
    And the response header "Content-Disposition" contains "checkliste-tourer-"
    And the response body is a valid PDF document
    And the PDF document contains "Wartungs-Checkliste"
    And the PDF document lists "Oelwechsel" before "Ventilspiel"
    And the PDF document lists "Ventilspiel" before "Kette schmieren"

  Scenario: Only the selected tasks are on the checklist
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-checklist?taskId={chain}"
    Then the response status code is 200
    And the PDF document contains "Kette schmieren"
    And the PDF document does not contain "Oelwechsel"

  Scenario Outline: Invalid selections are rejected
    Given a vehicle "other" exists with body:
      """
      { "name": "Zweitrad", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 100 }
      """
    And a maintenance task "foreign" exists for vehicle "other" with body:
      """
      { "name": "Fremd", "intervalKm": 1000 }
      """
    And a maintenance task "inactive" exists for vehicle "bike" with body:
      """
      { "name": "Inaktiv", "intervalKm": 1000, "active": false }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-checklist<query>"
    Then the response status code is 400

    Examples:
      | query                       |
      |                             |
      | ?taskId={foreign}           |
      | ?taskId={oil}&taskId=999999 |
      | ?taskId={inactive}          |
      | ?taskId=abc                 |

  Scenario: A one-time task that is done cannot be put on the checklist
    Given a maintenance task "inspection" exists for vehicle "bike" with body:
      """
      { "name": "Erstinspektion", "intervalKm": 1000, "oneTime": true }
      """
    And a maintenance log "firstService" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-01-01", "mileageAtPerformed": 1000, "performedTaskIds": [{inspection}] }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-checklist?taskId={inspection}"
    Then the response status code is 400

  Scenario: Another user's vehicle answers 404
    Given I am logged in as "bob@example.org"
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-checklist?taskId={oil}"
    Then the response status code is 404
