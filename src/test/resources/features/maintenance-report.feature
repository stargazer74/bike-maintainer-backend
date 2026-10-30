Feature: Maintenance report download

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "bike" exists with body:
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

  Scenario: The report contains only the selected entries, in chronological order
    Given a maintenance log "brakes" exists for vehicle "bike" with body:
      """
      { "performedAt": "2025-03-15", "mileageAtPerformed": 15000, "notes": "Bremsen entlueftet" }
      """
    And a maintenance log "chain" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-06-01", "mileageAtPerformed": 19900, "notes": "Kette gespannt" }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-report?logId={chain}&logId={brakes}&logId={chain}"
    Then the response status code is 200
    And the response header "Content-Disposition" contains "maintenance-report-vehicle-{bike}.pdf"
    And the PDF document lists "Bremsen entlueftet" before "Kette gespannt"
    And the PDF document does not contain "Annual service"

  Scenario: Without a selection the report contains all entries
    Given a maintenance log "brakes" exists for vehicle "bike" with body:
      """
      { "performedAt": "2025-03-15", "mileageAtPerformed": 15000, "notes": "Bremsen entlueftet" }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-report"
    Then the response status code is 200
    And the PDF document lists "Bremsen entlueftet" before "Annual service"

  Scenario: A single entry gets the date of the maintenance in the file name
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-report?logId={service}"
    Then the response status code is 200
    And the response header "Content-Disposition" contains "maintenance-report-vehicle-{bike}-2026-01-01.pdf"
    And the PDF document contains "Annual service"

  Scenario Outline: Invalid selections are rejected
    Given a vehicle "other" exists with body:
      """
      { "name": "Zweitrad", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 100 }
      """
    And a maintenance log "foreign" exists for vehicle "other" with body:
      """
      { "performedAt": "2025-01-01", "mileageAtPerformed": 50, "notes": "Fremder Eintrag" }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-report<query>"
    Then the response status code is 400

    Examples:
      | query                          |
      | ?logId={foreign}               |
      | ?logId={service}&logId=999999  |
      | ?logId=abc                     |
      | ?logId=0                       |
      | ?logId=                        |

  Scenario: An entry of another user's vehicle is rejected
    Given I am logged in as "bob@example.org"
    And a vehicle "bobsBike" exists with body:
      """
      { "name": "Bobs Rad", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 100 }
      """
    When a GET request is sent to "/api/v1/vehicles/{bobsBike}/maintenance-report?logId={service}"
    Then the response status code is 400

  Scenario: Another user's vehicle answers 404
    Given I am logged in as "bob@example.org"
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-report?logId={service}"
    Then the response status code is 404
