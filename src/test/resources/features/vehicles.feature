Feature: Vehicle management

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
