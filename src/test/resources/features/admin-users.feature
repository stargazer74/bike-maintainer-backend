Feature: Admins manage users

  Background:
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And the id of the user "rider@example.org" is remembered as "rider"
    And the id of the user "admin@localhost" is remembered as "admin"
    And I log in as "admin@localhost" with password "initial-admin-password"

  Scenario: An admin lists all users without password hashes
    When a GET request is sent to "/api/v1/admin/users"
    Then the response status code is 200
    And the response body contains "rider@example.org"
    And the response body contains "admin@localhost"
    And the response body does not contain "password"

  Scenario: A deactivated user is logged out everywhere and cannot log in
    Given I am logged in as "rider@example.org" with password "correct-horse-battery" in a second browser
    When a PATCH request is sent to "/api/v1/admin/users/{rider}" with body:
      """
      { "active": false }
      """
    Then the response status code is 200
    And the JSON response field "active" equals "false"
    And the second browser is no longer logged in
    When I log out
    And I log in as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 403
    And the JSON response field "code" equals "ACCOUNT_DISABLED"

  Scenario: A reactivated user can log in again
    Given a PATCH request is sent to "/api/v1/admin/users/{rider}" with body:
      """
      { "active": false }
      """
    When a PATCH request is sent to "/api/v1/admin/users/{rider}" with body:
      """
      { "active": true }
      """
    Then the response status code is 200
    When I log out
    And I log in as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 200

  Scenario: A promoted user gets admin rights with the next login
    When a PATCH request is sent to "/api/v1/admin/users/{rider}" with body:
      """
      { "role": "ADMIN" }
      """
    Then the response status code is 200
    And the JSON response field "role" equals "ADMIN"
    When I log out
    And I log in as "rider@example.org" with password "correct-horse-battery"
    And a GET request is sent to "/api/v1/admin/users"
    Then the response status code is 200

  Scenario: A demoted admin loses admin rights at once
    Given a PATCH request is sent to "/api/v1/admin/users/{rider}" with body:
      """
      { "role": "ADMIN" }
      """
    And I am logged in as "rider@example.org" with password "correct-horse-battery" in a second browser
    When a PATCH request is sent to "/api/v1/admin/users/{rider}" with body:
      """
      { "role": "USER" }
      """
    Then the response status code is 200
    And the second browser is no longer logged in

  Scenario: The last active admin can neither be demoted nor deactivate themselves
    When a PATCH request is sent to "/api/v1/admin/users/{admin}" with body:
      """
      { "role": "USER" }
      """
    Then the response status code is 400
    When a PATCH request is sent to "/api/v1/admin/users/{admin}" with body:
      """
      { "active": false }
      """
    Then the response status code is 400
    When a GET request is sent to "/api/v1/admin/users"
    Then the response status code is 200

  Scenario: An empty update is rejected
    When a PATCH request is sent to "/api/v1/admin/users/{rider}" with body:
      """
      {}
      """
    Then the response status code is 400

  Scenario: An unknown user answers 404
    When a PATCH request is sent to "/api/v1/admin/users/999999" with body:
      """
      { "active": false }
      """
    Then the response status code is 404

  Scenario: Admins do not see the vehicles of other users
    Given I am logged in as "owner@example.org"
    And a vehicle "bike" exists with body:
      """
      { "name": "Adventure Bike", "type": "MOTORCYCLE", "modelYear": 2022, "firstRegistrationDate": "2022-01-01", "currentMileage": 8000 }
      """
    When I log in as "admin@localhost" with password "initial-admin-password"
    And a GET request is sent to "/api/v1/vehicles/{bike}"
    Then the response status code is 404
