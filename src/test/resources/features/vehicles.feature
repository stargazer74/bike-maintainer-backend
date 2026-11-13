Feature: Vehicle management

  Background:
    Given I am logged in as "rider@example.org"

  Scenario: Create a new vehicle
    When a POST request is sent to "/api/v1/vehicles" with body:
      """
      {
        "name": "Enduro 450",
        "type": "MOTORCYCLE",
        "make": "KTM",
        "model": "450 EXC-F",
        "modelYear": 2023, "firstRegistrationDate": "2023-01-01",
        "currentMileage": 1500
      }
      """
    Then the response status code is 201
    And the JSON response field "name" equals "Enduro 450"
    And the JSON response field "type" equals "MOTORCYCLE"
    And the JSON response field "currentMileage" equals "1500"

  Scenario Outline: Create and retrieve a vehicle of every type
    Given a vehicle "typed" exists with body:
      """
      { "name": "Typed", "type": "<type>", "modelYear": 2022, "firstRegistrationDate": "2022-01-01", "currentMileage": 100 }
      """
    When a GET request is sent to "/api/v1/vehicles/{typed}"
    Then the response status code is 200
    And the JSON response field "type" equals "<type>"

    Examples:
      | type       |
      | MOTORCYCLE |
      | CAR        |
      | BICYCLE    |
      | E_BIKE     |
      | QUAD       |
      | SCOOTER    |

  Scenario: Reject creating a vehicle without a name
    When a POST request is sent to "/api/v1/vehicles" with body:
      """
      {
        "type": "CAR"
      }
      """
    Then the response status code is 400

  Scenario: Retrieve an existing vehicle
    Given a vehicle "car" exists with body:
      """
      {
        "name": "Family Van",
        "type": "CAR",
        "make": "VW",
        "model": "Caddy",
        "modelYear": 2020, "firstRegistrationDate": "2020-01-01",
        "currentMileage": 45000
      }
      """
    When a GET request is sent to "/api/v1/vehicles/{car}"
    Then the response status code is 200
    And the JSON response field "name" equals "Family Van"
    And the JSON response field "currentMileage" equals "45000"

  Scenario: Retrieve a vehicle that does not exist
    When a GET request is sent to "/api/v1/vehicles/999999"
    Then the response status code is 404

  Scenario: Reject a non-positive vehicle id
    When a GET request is sent to "/api/v1/vehicles/0"
    Then the response status code is 400

  Scenario: List all vehicles includes previously created vehicles
    Given a vehicle "listed" exists with body:
      """
      { "name": "Listed Bike", "type": "MOTORCYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-01-01", "currentMileage": 3000 }
      """
    When a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 200
    And the response body contains "Listed Bike"

  Scenario: Update an existing vehicle
    Given a vehicle "toUpdate" exists with body:
      """
      { "name": "Old Name", "type": "CAR", "modelYear": 2015, "firstRegistrationDate": "2015-01-01", "currentMileage": 1000 }
      """
    When a PUT request is sent to "/api/v1/vehicles/{toUpdate}" with body:
      """
      { "name": "New Name", "type": "CAR", "modelYear": 2015, "firstRegistrationDate": "2015-01-01", "currentMileage": 2000 }
      """
    Then the response status code is 200
    And the JSON response field "name" equals "New Name"
    And the JSON response field "currentMileage" equals "2000"

  Scenario: Update a vehicle that does not exist
    When a PUT request is sent to "/api/v1/vehicles/999999" with body:
      """
      { "name": "Ghost", "type": "CAR", "modelYear": 2015, "firstRegistrationDate": "2015-01-01", "currentMileage": 1000 }
      """
    Then the response status code is 404

  Scenario: Delete an existing vehicle
    Given a vehicle "toDelete" exists with body:
      """
      { "name": "Disposable", "type": "MOTORCYCLE", "modelYear": 2019, "firstRegistrationDate": "2019-01-01", "currentMileage": 500 }
      """
    When a DELETE request is sent to "/api/v1/vehicles/{toDelete}"
    Then the response status code is 204
    When a GET request is sent to "/api/v1/vehicles/{toDelete}"
    Then the response status code is 404

  Scenario: Delete a vehicle that does not exist
    When a DELETE request is sent to "/api/v1/vehicles/999999"
    Then the response status code is 404

  Scenario: Delete a vehicle with maintenance tasks and logs
    Given a vehicle "withHistory" exists with body:
      """
      { "name": "Well Kept", "type": "MOTORCYCLE", "modelYear": 2018, "firstRegistrationDate": "2018-01-01", "currentMileage": 12000 }
      """
    And a maintenance task "historyTask" exists for vehicle "withHistory" with body:
      """
      { "name": "Oil change", "intervalKm": 5000 }
      """
    And a maintenance log "historyLog" exists for vehicle "withHistory" with body:
      """
      { "performedAt": "2026-01-15", "mileageAtPerformed": 11000, "performedTaskIds": [{historyTask}],
        "materials": [ { "name": "Oelfilter", "quantity": 1, "unit": "PIECE", "price": 12.50, "taskId": {historyTask} } ] }
      """
    When a DELETE request is sent to "/api/v1/vehicles/{withHistory}"
    Then the response status code is 204
    When a GET request is sent to "/api/v1/vehicles/{withHistory}"
    Then the response status code is 404

  Scenario Outline: Reject creating or updating a vehicle with invalid values
    Given a vehicle "bike" exists with body:
      """
      { "name": "Trail Bike", "type": "MOTORCYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-01-01", "currentMileage": 5000 }
      """
    When a POST request is sent to "/api/v1/vehicles" with body:
      """
      { "name": "Invalid", "type": <type>, "modelYear": <modelYear>, "firstRegistrationDate": <date>, "currentMileage": <mileage> }
      """
    Then the response status code is 400
    When a PUT request is sent to "/api/v1/vehicles/{bike}" with body:
      """
      { "name": "Invalid", "type": <type>, "modelYear": <modelYear>, "firstRegistrationDate": <date>, "currentMileage": <mileage> }
      """
    Then the response status code is 400
    When a GET request is sent to "/api/v1/vehicles/{bike}"
    Then the JSON response field "name" equals "Trail Bike"

    Examples:
      | case                  | type         | modelYear | date         | mileage |
      | unknown type          | "TRIKE"      | 2021      | "2021-01-01" | 5000    |
      | missing type          | null         | 2021      | "2021-01-01" | 5000    |
      | model year too early  | "MOTORCYCLE" | 1899      | "2021-01-01" | 5000    |
      | model year too late   | "MOTORCYCLE" | 2101      | "2021-01-01" | 5000    |
      | negative mileage      | "MOTORCYCLE" | 2021      | "2021-01-01" | -1      |
      | invalid date          | "MOTORCYCLE" | 2021      | "2021-13-45" | 5000    |
      | missing date          | "MOTORCYCLE" | 2021      | null         | 5000    |

  Scenario: Raising the mileage changes only the mileage
    Given a vehicle "mileage" exists with body:
      """
      { "name": "Tenere", "type": "MOTORCYCLE", "make": "Yamaha", "modelYear": 2021, "firstRegistrationDate": "2021-05-01", "currentMileage": 24000 }
      """
    When a PUT request is sent to "/api/v1/vehicles/{mileage}/mileage" with body:
      """
      { "mileage": 24500 }
      """
    Then the response status code is 200
    And the JSON response field "currentMileage" equals "24500"
    And the JSON response field "name" equals "Tenere"
    And the JSON response field "make" equals "Yamaha"

  Scenario: Sending the same mileage again changes nothing
    Given a vehicle "same" exists with body:
      """
      { "name": "Same", "type": "CAR", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 5000 }
      """
    When a PUT request is sent to "/api/v1/vehicles/{same}/mileage" with body:
      """
      { "mileage": 5000 }
      """
    Then the response status code is 200
    And the JSON response field "currentMileage" equals "5000"

  Scenario: A mileage below the current one is rejected
    Given a vehicle "lower" exists with body:
      """
      { "name": "Lower", "type": "CAR", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 5000 }
      """
    When a PUT request is sent to "/api/v1/vehicles/{lower}/mileage" with body:
      """
      { "mileage": 4999 }
      """
    Then the response status code is 409
    And the JSON response field "code" equals "MILEAGE_BELOW_CURRENT"
    When a GET request is sent to "/api/v1/vehicles/{lower}"
    Then the JSON response field "currentMileage" equals "5000"

  Scenario: A negative mileage is rejected
    Given a vehicle "negative" exists with body:
      """
      { "name": "Negative", "type": "CAR", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 5000 }
      """
    When a PUT request is sent to "/api/v1/vehicles/{negative}/mileage" with body:
      """
      { "mileage": -1 }
      """
    Then the response status code is 400
