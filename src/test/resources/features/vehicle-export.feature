Feature: Export a vehicle to a file and import it again (Epic #82)

  Background:
    Given I am logged in as "rider@example.org"
    And a vehicle "bike" exists with body:
      """
      { "name": "Die Rote", "type": "MOTORCYCLE", "make": "Honda", "model": "CB 500", "modelYear": 2019,
        "firstRegistrationDate": "2019-05-01", "currentMileage": 21000 }
      """
    And a PUT request is sent to "/api/v1/vehicles/{bike}/specifications" with body:
      """
      { "cylinders": 2, "valvesPerCylinder": 4, "valveAdjustment": "SHIM", "intakeClearanceMin": 0.10,
        "intakeClearanceMax": 0.15, "tirePressureFront": 2.5 }
      """
    And a maintenance task "oilChange" exists for vehicle "bike" with body:
      """
      { "name": "Oelwechsel", "description": "10W-40", "intervalKm": 6000, "intervalMonths": 12 }
      """
    And a maintenance task "chain" exists for vehicle "bike" with body:
      """
      { "name": "Kette pruefen", "intervalKm": 1000 }
      """
    And a maintenance log "spring" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-04-01", "mileageAtPerformed": 18000, "notes": "Fruehjahrscheck",
        "performedTaskIds": [{oilChange}, {chain}], "laborCost": 30,
        "materials": [
          { "name": "Oelfilter", "partNumber": "HF-204", "quantity": 1, "unit": "PIECE", "price": 12.50, "taskId": {oilChange} },
          { "name": "Motoroel", "quantity": 3.5, "unit": "LITER", "price": 7.25 } ],
        "measurements": { "valveClearance": { "valves": [
          { "cylinder": 1, "valve": "INTAKE", "index": 1, "measured": 0.08, "shimOld": 1.800, "shimNew": 1.750 } ] },
          "tires": { "pressureFront": 2.4 } } }
      """
    And a maintenance log "summer" exists for vehicle "bike" with body:
      """
      { "performedAt": "2026-07-15", "mileageAtPerformed": 20000, "performedTaskIds": [{chain}] }
      """

  Scenario: The export contains the vehicle with its tasks, logs and target values, without database ids
    When a GET request is sent to "/api/v1/vehicles/{bike}/export"
    Then the response status code is 200
    And the response content type is "application/json"
    And the response header "Content-Disposition" contains "attachment"
    And the response header "Content-Disposition" contains "vehicle-die-rote-"
    And the JSON response value at "/formatVersion" equals "1"
    And the JSON response value at "/vehicle/name" equals "Die Rote"
    And the JSON response value at "/vehicle/specifications/intakeClearanceMax" equals "0.15"
    And the JSON response value at "/vehicle/maintenanceTasks/0/ref" equals "t1"
    And the JSON response value at "/vehicle/maintenanceTasks/0/name" equals "Oelwechsel"
    And the JSON response value at "/vehicle/maintenanceLogs/0/performedTaskRefs/1" equals "t2"
    And the JSON response value at "/vehicle/maintenanceLogs/0/currency" equals "EUR"
    And the JSON response value at "/vehicle/maintenanceLogs/0/materials/0/taskRef" equals "t1"
    And the JSON response value at "/vehicle/maintenanceLogs/0/measurements/valveClearance/valves/0/shimNew" equals "1.75"
    And the JSON response has no value at "/vehicle/maintenanceLogs/1/notes"
    And the JSON response has no value at "/vehicle/id"
    And the JSON response has no value at "/vehicle/maintenanceTasks/0/id"

  Scenario: An imported vehicle is exported exactly as the original
    Given a GET request is sent to "/api/v1/vehicles/{bike}/export"
    And the response body is remembered as "original"
    When the remembered file "original" is imported as vehicle "copy"
    Then the JSON response field "name" equals "Die Rote"
    And the vehicle "copy" has 2 maintenance tasks
    And the vehicle "copy" has 2 maintenance logs
    When a GET request is sent to "/api/v1/vehicles/{copy}/export"
    Then the JSON response value at "/vehicle" equals the one of the remembered file "original"

  Scenario: The currency of each log is taken from the file, not from the importing user
    When a POST request is sent to "/api/v1/vehicles/import" with body:
      """
      { "formatVersion": 1, "exportedAt": "2026-11-11T10:00:00Z", "vehicle": {
          "name": "Schweizer", "type": "CAR", "modelYear": 2015, "firstRegistrationDate": "2015-03-01", "currentMileage": 90000,
          "maintenanceLogs": [ { "performedAt": "2025-06-01", "mileageAtPerformed": 85000, "currency": "CHF", "laborCost": 120 } ] } }
      """
    Then the response status code is 201
    And the created vehicle is stored as "swiss"
    When a GET request is sent to "/api/v1/vehicles/{swiss}/maintenance-logs"
    Then the JSON response value at "/0/currency" equals "CHF"

  Scenario: An invalid file creates nothing and lists every problem
    When a POST request is sent to "/api/v1/vehicles/import" with body:
      """
      { "formatVersion": 1, "exportedAt": "2026-11-11T10:00:00Z", "vehicle": {
          "name": "Kaputt", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 100,
          "maintenanceTasks": [ { "ref": "a", "name": "Kette", "intervalKm": 1000 }, { "ref": "a", "name": "Oel", "intervalKm": 5000 } ],
          "maintenanceLogs": [ { "performedAt": "2026-01-01", "mileageAtPerformed": 50, "currency": "EUR",
            "performedTaskRefs": ["b"], "materials": [ { "name": "Oel", "quantity": 1, "unit": "LITER", "taskRef": "a" } ] } ] } }
      """
    Then the response status code is 400
    And the JSON response field "code" equals "IMPORT_INVALID"
    And the JSON response value at "/details/0" equals "vehicle.maintenanceTasks[1].ref: duplicate ref \"a\""
    And the JSON response value at "/details/1" equals "vehicle.maintenanceLogs[0].performedTaskRefs: unknown task ref \"b\""
    And the JSON response value at "/details/2" equals "vehicle.maintenanceLogs[0].materials[0].taskRef: task \"a\" is not performed by this log"
    When a GET request is sent to "/api/v1/vehicles"
    Then the response body does not contain "Kaputt"

  Scenario: A rule broken late in the import rolls back the whole vehicle
    When a POST request is sent to "/api/v1/vehicles/import" with body:
      """
      { "formatVersion": 1, "exportedAt": "2026-11-11T10:00:00Z", "vehicle": {
          "name": "Halb", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 100,
          "maintenanceTasks": [ { "ref": "a", "name": "Kette", "intervalKm": 1000 }, { "ref": "b", "name": "Ohne Intervall" } ] } }
      """
    Then the response status code is 400
    And the JSON response field "code" equals "IMPORT_INVALID"
    And the JSON response value at "/details/0" equals "vehicle.maintenanceTasks[1]: At least one of intervalKm or intervalMonths must be provided unless oneTime is true"
    When a GET request is sent to "/api/v1/vehicles"
    Then the response body does not contain "Halb"

  Scenario: Missing fields are reported with their location
    When a POST request is sent to "/api/v1/vehicles/import" with body:
      """
      { "formatVersion": 1, "exportedAt": "2026-11-11T10:00:00Z", "vehicle": {
          "name": "", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 100,
          "maintenanceTasks": [ { "ref": "a" } ] } }
      """
    Then the response status code is 400
    And the JSON response field "code" equals "IMPORT_INVALID"
    And the response body contains "vehicle.maintenanceTasks[0].name"
    And the response body contains "vehicle.name"

  Scenario: A file that is no export file at all
    When a POST request is sent to "/api/v1/vehicles/import" with body:
      """
      [ "not", "an", "export" ]
      """
    Then the response status code is 400
    And the JSON response field "code" equals "IMPORT_INVALID"

  Scenario: A file of a newer format version is not read
    When a POST request is sent to "/api/v1/vehicles/import" with body:
      """
      { "formatVersion": 2, "exportedAt": "2026-11-11T10:00:00Z", "vehicle": { "somethingNew": true } }
      """
    Then the response status code is 400
    And the JSON response field "code" equals "IMPORT_FORMAT_UNSUPPORTED"

  Scenario: More tasks than allowed
    When an import file with 201 maintenance tasks is sent
    Then the response status code is 400
    And the JSON response field "code" equals "IMPORT_INVALID"

  Scenario: The size limit of 5 MB
    When an import file of 5242880 bytes is sent
    Then the response status code is 201
    When an import file of 5242881 bytes is sent
    Then the response status code is 413

  Scenario: Other users cannot export the vehicle
    Given I am logged in as "other@example.org"
    When a GET request is sent to "/api/v1/vehicles/{bike}/export"
    Then the response status code is 404
