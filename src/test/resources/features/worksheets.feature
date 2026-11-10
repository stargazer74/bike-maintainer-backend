Feature: Worksheets (printable PDF forms)

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "bike" exists with body:
      """
      { "name": "Tourer", "type": "MOTORCYCLE", "make": "BMW", "model": "R1250GS", "modelYear": 2022, "firstRegistrationDate": "2022-01-01", "currentMileage": 20000 }
      """
    And a vehicle "car" exists with body:
      """
      { "name": "Kombi", "type": "CAR", "modelYear": 2018, "firstRegistrationDate": "2018-01-01", "currentMileage": 90000 }
      """
    And a vehicle "bicycle" exists with body:
      """
      { "name": "Rennrad", "type": "BICYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-01-01", "currentMileage": 0 }
      """

  Scenario: A motorcycle offers all templates
    When a GET request is sent to "/api/v1/vehicles/{bike}/worksheets"
    Then the response status code is 200
    And the JSON response array has 4 elements
    And the response body contains "VALVE_CLEARANCE"
    And the response body contains "CHAIN"

  Scenario: A car offers no chain, a bicycle no valve clearance
    When a GET request is sent to "/api/v1/vehicles/{car}/worksheets"
    Then the JSON response array has 3 elements
    And the response body does not contain "CHAIN"
    When a GET request is sent to "/api/v1/vehicles/{bicycle}/worksheets"
    Then the JSON response array has 3 elements
    And the response body does not contain "VALVE_CLEARANCE"

  Scenario: The valve clearance worksheet has a table per cylinder
    When a GET request is sent to "/api/v1/vehicles/{bike}/worksheets/VALVE_CLEARANCE?cylinders=2&valvesPerCylinder=4"
    Then the response status code is 200
    And the response content type is "application/pdf"
    And the response header "Content-Disposition" contains "arbeitsblatt-ventilspiel-tourer-"
    And the response body is a valid PDF document
    And the PDF document contains "Arbeitsblatt Ventilspiel"
    And the PDF document contains "Marke/Modell: BMW R1250GS"
    And the PDF document lists "Zylinder 1" before "Zylinder 2"
    And the PDF document does not contain "Zylinder 3"

  Scenario Outline: The other templates need no parameters
    When a GET request is sent to "/api/v1/vehicles/{bike}/worksheets/<template>"
    Then the response status code is 200
    And the response content type is "application/pdf"
    And the PDF document contains "<title>"

    Examples:
      | template | title                |
      | BRAKES   | Arbeitsblatt Bremsen |
      | CHAIN    | Arbeitsblatt Kette   |
      | TIRES    | Arbeitsblatt Reifen  |

  Scenario Outline: Invalid requests are rejected
    When a GET request is sent to "/api/v1/vehicles/{<vehicle>}/worksheets/<path>"
    Then the response status code is 400

    Examples:
      | vehicle | path                                                |
      | car     | CHAIN                                               |
      | bicycle | VALVE_CLEARANCE?cylinders=1&valvesPerCylinder=2     |
      | bike    | VALVE_CLEARANCE                                     |
      | bike    | VALVE_CLEARANCE?cylinders=2                         |
      | bike    | VALVE_CLEARANCE?cylinders=0&valvesPerCylinder=2     |
      | bike    | VALVE_CLEARANCE?cylinders=9&valvesPerCylinder=2     |
      | bike    | VALVE_CLEARANCE?cylinders=2&valvesPerCylinder=5     |
      | bike    | BRAKES?cylinders=2                                  |
      | bike    | OIL                                                 |

  Scenario: Another user's vehicle answers 404
    Given I am logged in as "bob@example.org"
    When a GET request is sent to "/api/v1/vehicles/{bike}/worksheets"
    Then the response status code is 404
    When a GET request is sent to "/api/v1/vehicles/{bike}/worksheets/BRAKES"
    Then the response status code is 404
