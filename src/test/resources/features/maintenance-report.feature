Feature: Maintenance report download

  Background:
    Given a vehicle "bike" exists with body:
      """
      { "name": "Tourer", "type": "MOTORCYCLE", "make": "BMW", "model": "R1250GS", "modelYear": 2022, "firstRegistrationDate": "2022-01-01", "currentMileage": 20000 }
      """
    And a maintenance task "oilChange" exists for vehicle "bike" with body:
      """
      { "name": "Oil change", "intervalKm": 10000 }
      """
    And a maintenance log "service" exists for vehicle "bike" with body:
      """
      {
        "performedAt": "2026-01-01",
        "mileageAtPerformed": 19500,
        "notes": "Annual service",
        "performedTaskIds": [{oilChange}]
      }
      """

  Scenario: Download the maintenance report of a vehicle with logs
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-report"
    Then the response status code is 200
    And the response content type is "application/pdf"
    And the response header "Content-Disposition" contains "maintenance-report-vehicle-{bike}.pdf"
    And the response body is a valid PDF document

  Scenario: Download the maintenance report of a vehicle without logs
    Given a vehicle "empty" exists with body:
      """
      { "name": "Fresh Bike", "type": "MOTORCYCLE", "modelYear": 2024, "firstRegistrationDate": "2024-01-01", "currentMileage": 0 }
      """
    When a GET request is sent to "/api/v1/vehicles/{empty}/maintenance-report"
    Then the response status code is 200
    And the response body is a valid PDF document

  Scenario: Download the maintenance report of an unknown vehicle
    When a GET request is sent to "/api/v1/vehicles/999999/maintenance-report"
    Then the response status code is 404
