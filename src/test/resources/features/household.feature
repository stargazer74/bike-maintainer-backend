@household
Feature: Household mode – profile selection instead of login

  Scenario: Config reports household mode
    When a GET request is sent to "/api/v1/config"
    Then the response status code is 200
    And the JSON response field "mode" equals "household"

  Scenario: The initial admin is offered as a profile
    When a GET request is sent to "/api/v1/profiles"
    Then the response status code is 200
    And the response body contains "\"displayName\":\"Admin\""

  Scenario: The API requires a selected profile
    When a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 401

  Scenario: Creating a profile stores the trimmed name
    When I create a profile "  Anna  "
    Then the response status code is 201
    And the JSON response field "displayName" equals "Anna"
    When a GET request is sent to "/api/v1/profiles"
    Then the response body contains "\"displayName\":\"Anna\""

  Scenario: Profile names are unique, ignoring case
    Given a profile "Anna" exists
    When I create a profile "anna"
    Then the response status code is 409

  Scenario: A blank profile name is rejected
    When I create a profile "   "
    Then the response status code is 400

  Scenario: Creating a profile requires the CSRF token
    When a POST request without CSRF token is sent to "/api/v1/profiles" with body:
      """
      { "displayName": "Anna" }
      """
    Then the response status code is 403

  Scenario: Selecting a profile starts a session
    Given a profile "Anna" exists
    When I select the profile "Anna"
    Then the response status code is 200
    And the JSON response field "displayName" equals "Anna"
    And the client has a "JSESSIONID" cookie
    When a GET request is sent to "/api/v1/auth/me"
    Then the response status code is 200
    And the JSON response field "displayName" equals "Anna"
    And the JSON response field "role" equals "USER"

  Scenario: A selected profile can change its language
    Given a profile "Anna" exists
    When I select the profile "Anna"
    And a PATCH request is sent to "/api/v1/auth/me" with body:
      """
      { "language": "en" }
      """
    Then the response status code is 200
    And the JSON response field "language" equals "en"

  Scenario: A selected profile can change its currency
    Given a profile "Anna" exists
    When I select the profile "Anna"
    And a PATCH request is sent to "/api/v1/auth/me" with body:
      """
      { "currency": "CHF" }
      """
    Then the response status code is 200
    And the JSON response field "currency" equals "CHF"
    And the JSON response field "language" equals "de"

  Scenario: Selecting an unknown profile answers 404
    When a POST request is sent to "/api/v1/profiles/999999/select" with body:
      """
      {}
      """
    Then the response status code is 404

  Scenario: Switching to another profile
    Given a profile "Ben" exists
    And I have selected a new profile "Anna"
    When I select the profile "Ben"
    And a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "displayName" equals "Ben"

  Scenario: Each profile only sees its own vehicles
    Given I have selected a new profile "Anna"
    And a vehicle "annasBike" exists with body:
      """
      { "name": "Annas Enduro", "type": "MOTORCYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-03-01", "currentMileage": 12000 }
      """
    And I have selected a new profile "Ben"
    When a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 200
    And the response body does not contain "Annas Enduro"
    When a GET request is sent to "/api/v1/vehicles/{annasBike}"
    Then the response status code is 404

  Scenario: Renaming a profile
    Given a profile "Anna" exists
    When a PUT request is sent to "/api/v1/profiles/{Anna}" with body:
      """
      { "displayName": "Anna-Lena" }
      """
    Then the response status code is 200
    And the JSON response field "displayName" equals "Anna-Lena"

  Scenario: Renaming to a name that is taken answers 409
    Given a profile "Anna" exists
    And a profile "Ben" exists
    When a PUT request is sent to "/api/v1/profiles/{Ben}" with body:
      """
      { "displayName": "ANNA" }
      """
    Then the response status code is 409

  Scenario: A profile with vehicles cannot be deleted
    Given I have selected a new profile "Anna"
    And a vehicle "annasBike" exists with body:
      """
      { "name": "Annas Enduro", "type": "MOTORCYCLE", "modelYear": 2021, "firstRegistrationDate": "2021-03-01", "currentMileage": 12000 }
      """
    When a DELETE request is sent to "/api/v1/profiles/{Anna}"
    Then the response status code is 409

  Scenario: Deleting a profile removes it and ends its sessions
    Given I have selected a new profile "Anna"
    When a DELETE request is sent to "/api/v1/profiles/{Anna}"
    Then the response status code is 204
    When a GET request is sent to "/api/v1/auth/me"
    Then the response status code is 401
    When a GET request is sent to "/api/v1/profiles"
    Then the response body does not contain "Anna"

  Scenario: Logout ends the profile session
    Given I have selected a new profile "Anna"
    When I log out
    Then the response status code is 204
    When a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 401

  Scenario: The registration captcha of portal mode does not exist
    When a GET request is sent to "/api/v1/auth/challenge"
    Then the response status code is 404

  Scenario Outline: Account endpoints of portal mode do not exist
    When a POST request without CSRF token is sent to "<path>" with body:
      """
      {}
      """
    Then the response status code is 404

    Examples:
      | path                                 |
      | /api/v1/auth/login                   |
      | /api/v1/auth/register                |
      | /api/v1/auth/verify-email            |
      | /api/v1/auth/verify-email/resend     |
      | /api/v1/auth/password-reset/request  |
      | /api/v1/auth/password-reset/confirm  |

  Scenario: Password change and admin endpoints do not exist
    Given I have selected a new profile "Anna"
    When a PUT request is sent to "/api/v1/auth/password" with body:
      """
      { "currentPassword": "x", "newPassword": "long-enough-password" }
      """
    Then the response status code is 404
    When a GET request is sent to "/api/v1/admin/users"
    Then the response status code is 404
