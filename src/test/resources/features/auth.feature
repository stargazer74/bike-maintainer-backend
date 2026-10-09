Feature: Authentication

  Scenario: API requires login
    When a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 401
    And the JSON response field "status" equals "401"
    And the client has a "XSRF-TOKEN" cookie

  Scenario: Successful login creates a session
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I log in as "Rider@Example.org" with password "correct-horse-battery"
    Then the response status code is 200
    And the JSON response field "email" equals "rider@example.org"
    And the JSON response field "role" equals "USER"
    And the client has a "JSESSIONID" cookie
    And the client has a "XSRF-TOKEN" cookie
    When a GET request is sent to "/api/v1/auth/me"
    Then the response status code is 200
    And the JSON response field "email" equals "rider@example.org"

  Scenario: Wrong password and unknown email are indistinguishable
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I log in as "rider@example.org" with password "wrong-password"
    Then the response status code is 401
    And the JSON response field "message" equals "Invalid email or password"
    When I log in as "nobody@example.org" with password "wrong-password"
    Then the response status code is 401
    And the JSON response field "message" equals "Invalid email or password"

  Scenario: Unverified account with correct password
    Given an unverified user "new@example.org" with password "correct-horse-battery" exists
    When I log in as "new@example.org" with password "correct-horse-battery"
    Then the response status code is 403
    And the JSON response field "code" equals "EMAIL_NOT_VERIFIED"

  Scenario: Unverified account with wrong password reveals nothing
    Given an unverified user "new@example.org" with password "correct-horse-battery" exists
    When I log in as "new@example.org" with password "wrong-password"
    Then the response status code is 401

  Scenario: Deactivated account with correct password
    Given a deactivated user "gone@example.org" with password "correct-horse-battery" exists
    When I log in as "gone@example.org" with password "correct-horse-battery"
    Then the response status code is 403
    And the JSON response field "code" equals "ACCOUNT_DISABLED"

  Scenario: Logout ends the session
    Given I am logged in as "rider@example.org"
    When I log out
    Then the response status code is 204
    When a GET request is sent to "/api/v1/auth/me"
    Then the response status code is 401

  Scenario: Logout requires login
    When I log out
    Then the response status code is 401

  Scenario: State-changing requests require the CSRF token
    Given I am logged in as "rider@example.org"
    When a POST request without CSRF token is sent to "/api/v1/vehicles" with body:
      """
      { "name": "Bike", "type": "MOTORCYCLE", "modelYear": 2020, "firstRegistrationDate": "2020-01-01", "currentMileage": 0 }
      """
    Then the response status code is 403

  Scenario: Login also requires the CSRF token
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When a POST request without CSRF token is sent to "/api/v1/auth/login" with body:
      """
      { "email": "rider@example.org", "password": "correct-horse-battery" }
      """
    Then the response status code is 403

  Scenario: Too many attempts from one IP are rate limited
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And the client IP is "198.51.100.200"
    When I fail to log in as "someone-else@example.org" 10 times
    And I log in as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 429
    And the response header "Retry-After" contains "9"

  Scenario: Account is locked after too many failed logins, even from another IP
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And the client IP is "198.51.100.201"
    When I fail to log in as "rider@example.org" 10 times
    And the client IP is "198.51.100.202"
    And I log in as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 429

  Scenario: Not yet implemented auth endpoints answer 501
    When a POST request is sent to "/api/v1/auth/register" with body:
      """
      { "email": "new@example.org", "password": "long-enough-password" }
      """
    Then the response status code is 501
