Feature: Target values, measured values and shim calculator

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "bike" exists with body:
      """
      { "name": "Tourer", "type": "MOTORCYCLE", "modelYear": 2022, "firstRegistrationDate": "2022-01-01", "currentMileage": 20000 }
      """

  Scenario: A vehicle without target values returns an empty object
    When a GET request is sent to "/api/v1/vehicles/{bike}/specifications"
    Then the response status code is 200
    And the JSON response has no value at "/cylinders"

  Scenario: Target values are replaced as a whole
    When a PUT request is sent to "/api/v1/vehicles/{bike}/specifications" with body:
      """
      { "cylinders": 2, "valvesPerCylinder": 4, "valveAdjustment": "SHIM",
        "intakeClearanceMin": 0.10, "intakeClearanceMax": 0.15, "shimStep": 0.025, "tirePressureFront": 2.5 }
      """
    Then the response status code is 200
    And the JSON response value at "/valveAdjustment" equals "SHIM"
    And the JSON response value at "/intakeClearanceMax" equals "0.15"
    When a PUT request is sent to "/api/v1/vehicles/{bike}/specifications" with body:
      """
      { "cylinders": 4 }
      """
    And a GET request is sent to "/api/v1/vehicles/{bike}/specifications"
    Then the JSON response value at "/cylinders" equals "4"
    And the JSON response has no value at "/intakeClearanceMax"

  Scenario Outline: Invalid target values are rejected
    When a PUT request is sent to "/api/v1/vehicles/{bike}/specifications" with body:
      """
      <body>
      """
    Then the response status code is 400

    Examples:
      | body                                                        |
      | { "intakeClearanceMin": 0.20, "intakeClearanceMax": 0.10 }  |
      | { "shimMin": 3.0, "shimMax": 1.2 }                          |
      | { "chainSlackMin": 40, "chainSlackMax": 30 }                |
      | { "intakeClearanceMin": 0.1234 }                            |
      | { "tirePressureFront": 2.555 }                              |
      | { "cylinders": 9 }                                          |
      | { "valveAdjustment": "MAGIC" }                              |

  Scenario: Measurements are saved with the log and checked against the target values
    Given a PUT request is sent to "/api/v1/vehicles/{bike}/specifications" with body:
      """
      { "intakeClearanceMin": 0.10, "intakeClearanceMax": 0.15, "brakePadMinFront": 2.0, "tirePressureFront": 2.5,
        "treadDepthMin": 1.6, "chainSlackMin": 25, "chainSlackMax": 35, "chainWearLinks": 20, "chainWearMaxLength": 319 }
      """
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      { "performedAt": "2026-11-01", "mileageAtPerformed": 20000, "measurements": {
          "valveClearance": { "valves": [
            { "cylinder": 1, "valve": "EXHAUST", "index": 1, "measured": 0.22 },
            { "cylinder": 1, "valve": "INTAKE", "index": 1, "measured": 0.08, "shimOld": 1.800, "shimNew": 1.750 } ] },
          "brakes": { "padFront": 1.5, "discConditionFront": "  Riefen  " },
          "chain": { "slack": 30, "wearLength": 318.5, "wearLinks": 20, "cleaned": true },
          "tires": { "pressureFront": 2.45, "treadFront": 1.2, "dotFront": "2324" } } }
      """
    Then the response status code is 201
    And the JSON response value at "/measurements/valveClearance/valves/0/valve" equals "INTAKE"
    And the JSON response value at "/measurements/valveClearance/valves/0/measuredStatus" equals "OUT_OF_RANGE"
    And the JSON response value at "/measurements/valveClearance/valves/0/shimNew" equals "1.75"
    And the JSON response value at "/measurements/valveClearance/valves/1/measuredStatus" equals "UNKNOWN"
    And the JSON response value at "/measurements/brakes/padFrontStatus" equals "OUT_OF_RANGE"
    And the JSON response value at "/measurements/brakes/padRearStatus" equals "UNKNOWN"
    And the JSON response value at "/measurements/brakes/discConditionFront" equals "Riefen"
    And the JSON response value at "/measurements/chain/slackStatus" equals "OK"
    And the JSON response value at "/measurements/chain/wearLengthStatus" equals "OK"
    And the JSON response value at "/measurements/tires/pressureFrontStatus" equals "OK"
    And the JSON response value at "/measurements/tires/treadFrontStatus" equals "OUT_OF_RANGE"

  Scenario: Updating a log without measurements keeps them, with measurements replaces them
    Given a maintenance log "check" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-11-01", "mileageAtPerformed": 20000, "measurements": {
          "brakes": { "padFront": 3.0 }, "tires": { "treadFront": 4.0 } } }
      """
    When a PUT request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{check}" with body:
      """
      { "performedAt": "2026-11-01", "mileageAtPerformed": 20000, "notes": "ohne Messwerte" }
      """
    Then the response status code is 200
    And the JSON response value at "/measurements/brakes/padFront" equals "3.0"
    When a PUT request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{check}" with body:
      """
      { "performedAt": "2026-11-01", "mileageAtPerformed": 20000, "measurements": { "tires": { "treadFront": 3.5 } } }
      """
    Then the response status code is 200
    And the JSON response has no value at "/measurements/brakes"
    And the JSON response value at "/measurements/tires/treadFront" equals "3.5"
    When a PUT request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{check}" with body:
      """
      { "performedAt": "2026-11-01", "mileageAtPerformed": 20000, "measurements": { } }
      """
    Then the JSON response has no value at "/measurements"
    When a GET request is sent to "/api/v1/vehicles/{bike}/maintenance-logs"
    Then the response status code is 200
    And the JSON response has no value at "/0/measurements"

  Scenario Outline: Invalid measurements are rejected and nothing is saved
    When a POST request is sent to "/api/v1/vehicles/{bike}/maintenance-logs" with body:
      """
      { "performedAt": "2026-11-01", "mileageAtPerformed": 20000, "measurements": <measurements> }
      """
    Then the response status code is 400
    And the vehicle "bike" has 0 maintenance logs

    Examples:
      | measurements                                                                                                   |
      | { "valveClearance": { "valves": [ { "cylinder": 1, "valve": "INTAKE", "index": 1 }, { "cylinder": 1, "valve": "INTAKE", "index": 1 } ] } } |
      | { "valveClearance": { "valves": [ ] } }                                                                        |
      | { "valveClearance": { "valves": [ { "cylinder": 9, "valve": "INTAKE", "index": 1 } ] } }                       |
      | { "brakes": { "padFront": 1.2345 } }                                                                           |
      | { "tires": { "pressureFront": 2.555 } }                                                                        |
      | { "tires": { "dotFront": "23/24" } }                                                                           |
      | { "chain": { "wearLinks": 1 } }                                                                                |

  Scenario: The latest measurement of every template, with its log
    Given a maintenance log "older" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-05-01", "mileageAtPerformed": 15000, "measurements": {
          "brakes": { "padFront": 4.0 },
          "valveClearance": { "valves": [ { "cylinder": 1, "valve": "INTAKE", "index": 1, "shimNew": 1.800 } ] } } }
      """
    And a maintenance log "newer" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-10-01", "mileageAtPerformed": 19000, "measurements": { "brakes": { "padFront": 3.0 } } }
      """
    When a GET request is sent to "/api/v1/vehicles/{bike}/measurements/latest"
    Then the response status code is 200
    And the JSON response value at "/measurements/brakes/padFront" equals "3.0"
    And the JSON response value at "/sources/brakes/logId" equals "{newer}"
    And the JSON response value at "/measurements/valveClearance/valves/0/shimNew" equals "1.8"
    And the JSON response value at "/sources/valveClearance/performedAt" equals "2026-05-01"
    And the JSON response has no value at "/measurements/chain"
    And the JSON response has no value at "/sources/chain"

  Scenario: The shim calculator suggests new shims from the target values
    Given a PUT request is sent to "/api/v1/vehicles/{bike}/specifications" with body:
      """
      { "valveAdjustment": "SHIM", "intakeClearanceMin": 0.10, "intakeClearanceMax": 0.15, "shimStep": 0.025,
        "shimMin": 1.2, "shimMax": 3.5 }
      """
    When a POST request is sent to "/api/v1/vehicles/{bike}/shim-calculation" with body:
      """
      { "valves": [ { "valve": "INTAKE", "measured": 0.08, "shimOld": 1.800 },
                    { "valve": "INTAKE", "measured": 0.12, "shimOld": 1.800 },
                    { "valve": "EXHAUST", "measured": 0.20, "shimOld": 2.000 } ] }
      """
    Then the response status code is 200
    And the JSON response value at "/results/0/suggestedShim" equals "1.750"
    And the JSON response value at "/results/0/resultingClearance" equals "0.130"
    And the JSON response has no value at "/results/0/hint"
    And the JSON response value at "/results/1/hint" equals "NO_CHANGE_NEEDED"
    And the JSON response value at "/results/2/hint" equals "MISSING_INPUT"

  Scenario: The shim calculator rejects invalid input
    When a POST request is sent to "/api/v1/vehicles/{bike}/shim-calculation" with body:
      """
      { "valves": [ { "valve": "INTAKE", "measured": 0.0805 } ] }
      """
    Then the response status code is 400
    When a POST request is sent to "/api/v1/vehicles/{bike}/shim-calculation" with body:
      """
      { "valves": [ ] }
      """
    Then the response status code is 400

  Scenario: Deleting the log and the vehicle removes the measurements and target values
    Given a PUT request is sent to "/api/v1/vehicles/{bike}/specifications" with body:
      """
      { "cylinders": 2 }
      """
    And a maintenance log "check" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-11-01", "mileageAtPerformed": 20000, "measurements": {
          "brakes": { "padFront": 3.0 }, "chain": { "slack": 30 }, "tires": { "treadFront": 4.0 },
          "valveClearance": { "valves": [ { "cylinder": 1, "valve": "INTAKE", "index": 1, "measured": 0.1 } ] } } }
      """
    And a maintenance log "second" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-11-02", "mileageAtPerformed": 20001, "measurements": { "brakes": { "padFront": 2.9 } } }
      """
    When a DELETE request is sent to "/api/v1/vehicles/{bike}/maintenance-logs/{check}"
    Then the response status code is 204
    When a GET request is sent to "/api/v1/vehicles/{bike}/measurements/latest"
    Then the JSON response value at "/sources/brakes/logId" equals "{second}"
    And the JSON response has no value at "/measurements/valveClearance"
    When a DELETE request is sent to "/api/v1/vehicles/{bike}"
    Then the response status code is 204
    And the database holds 0 vehicles, 0 maintenance tasks and 0 maintenance logs

  Scenario: Another user's vehicle answers 404
    Given I am logged in as "bob@example.org"
    When a GET request is sent to "/api/v1/vehicles/{bike}/specifications"
    Then the response status code is 404
    When a PUT request is sent to "/api/v1/vehicles/{bike}/specifications" with body:
      """
      { "cylinders": 2 }
      """
    Then the response status code is 404
    When a GET request is sent to "/api/v1/vehicles/{bike}/measurements/latest"
    Then the response status code is 404
    When a POST request is sent to "/api/v1/vehicles/{bike}/shim-calculation" with body:
      """
      { "valves": [ { "valve": "INTAKE" } ] }
      """
    Then the response status code is 404
