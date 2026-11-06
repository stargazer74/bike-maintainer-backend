Feature: Delete the own account

  Background:
    Given I am logged in as "other@example.org"
    And a vehicle "otherBike" exists with body:
      """
      { "name": "Other Bike", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 5000 }
      """
    And a maintenance task "otherTask" exists for vehicle "otherBike" with body:
      """
      { "name": "Oil change", "intervalKm": 5000 }
      """
    And a verified user "rider@example.org" with password "correct-horse-battery" exists
    And I log in as "rider@example.org" with password "correct-horse-battery"
    And a vehicle "bike" exists with body:
      """
      { "name": "Adventure Bike", "type": "MOTORCYCLE", "modelYear": 2022, "firstRegistrationDate": "2022-01-01", "currentMileage": 8000 }
      """
    And a maintenance task "chain" exists for vehicle "bike" with body:
      """
      { "name": "Kette schmieren", "intervalKm": 1000 }
      """
    And a maintenance log "service" exists for vehicle "bike" with body:
      """
      { "performedAt": "2024-05-01", "mileageAtPerformed": 8200, "laborCost": 60,
        "performedTaskIds": [ {chain} ],
        "materials": [ { "name": "Chain spray", "quantity": 1, "unit": "PIECE", "price": 12.90, "taskId": {chain} } ] }
      """

  Scenario: The account and all of its data are deleted, other accounts stay
    Given I am logged in as "rider@example.org" with password "correct-horse-battery" in a second browser
    When I delete my account with password "correct-horse-battery"
    Then the response status code is 204
    And the user "rider@example.org" no longer exists
    And the database holds 1 vehicle, 1 maintenance task and 0 maintenance logs
    And I am no longer logged in
    And the second browser is no longer logged in
    When I log in as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 401
    When I log in as "other@example.org" with password "test-password-123"
    And a GET request is sent to "/api/v1/vehicles"
    Then the JSON response array has 1 element

  Scenario: A wrong password is rejected and nothing is deleted
    When I delete my account with password "wrong-password-123"
    Then the response status code is 403
    And the JSON response field "code" equals "WRONG_PASSWORD"
    And I am still logged in
    And the database holds 2 vehicles, 2 maintenance tasks and 1 maintenance log

  Scenario: Wrong passwords lock the account like failed logins
    Given the client IP is "203.0.113.50"
    When I delete my account with password "wrong-password-123" 9 times
    And the client IP is "203.0.113.51"
    And I delete my account with password "wrong-password-123"
    And I delete my account with password "correct-horse-battery"
    Then the response status code is 429
    And the database holds 2 vehicles, 2 maintenance tasks and 1 maintenance log

  Scenario: Too many attempts from one IP are rate limited
    When I delete my account with password "wrong-password-123" 10 times
    And a verified user "third@example.org" with password "correct-horse-battery" exists
    And I log in as "third@example.org" with password "correct-horse-battery"
    And I delete my account with password "correct-horse-battery"
    Then the response status code is 429

  Scenario: The password is required
    When I delete my account with the body:
      """
      {}
      """
    Then the response status code is 400

  Scenario: The last active admin cannot delete their account
    When I log in as "admin@localhost" with password "initial-admin-password"
    And I delete my account with password "initial-admin-password"
    Then the response status code is 409
    And the JSON response field "code" equals "LAST_ADMIN"
    And I am still logged in

  Scenario: Without login no account can be deleted
    When I log out
    And I delete my account with password "correct-horse-battery"
    Then the response status code is 401
