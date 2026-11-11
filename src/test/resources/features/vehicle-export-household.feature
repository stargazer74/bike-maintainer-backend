@household
Feature: Export and import of a vehicle in household mode (Epic #82)

  Scenario: A profile exports a vehicle and imports it again
    Given I have selected a new profile "Anna"
    And a vehicle "bike" exists with body:
      """
      { "name": "Annas Roller", "type": "MOTORCYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-04-01", "currentMileage": 3000 }
      """
    And a maintenance task "oil" exists for vehicle "bike" with body:
      """
      { "name": "Oelwechsel", "intervalKm": 3000 }
      """
    And a GET request is sent to "/api/v1/vehicles/{bike}/export"
    And the response body is remembered as "file"
    When the remembered file "file" is imported as vehicle "copy"
    Then the vehicle "copy" has 1 maintenance task
