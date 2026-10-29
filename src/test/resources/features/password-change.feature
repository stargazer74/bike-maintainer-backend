Feature: Change the password of the logged-in user

  Scenario: Change the password and log in with the new one
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And I log in as "rider@example.org" with password "correct-horse-battery"
    When I change my password from "correct-horse-battery" to "brand-new-password"
    Then the response status code is 204
    And I am still logged in
    When I log out
    And I log in as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 401
    When I log in as "rider@example.org" with password "brand-new-password"
    Then the response status code is 200

  Scenario: A wrong current password is rejected
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And I log in as "rider@example.org" with password "correct-horse-battery"
    When I change my password from "wrong-password-123" to "brand-new-password"
    Then the response status code is 400
    When I log out
    And I log in as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 200

  Scenario: Other sessions end, the current one stays
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And I am logged in as "rider@example.org" with password "correct-horse-battery" in a second browser
    And I log in as "rider@example.org" with password "correct-horse-battery"
    When I change my password from "correct-horse-battery" to "brand-new-password"
    Then the response status code is 204
    And the second browser is no longer logged in
    And I am still logged in
    When a POST request is sent to "/api/v1/vehicles" with body:
      """
      { "name": "Africa Twin", "type": "MOTORCYCLE", "make": "Honda", "model": "CRF1100L", "modelYear": 2022, "firstRegistrationDate": "2022-03-01", "currentMileage": 1000 }
      """
    Then the response status code is 201

  Scenario: Changing the password invalidates an open reset link
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I request a password reset for "rider@example.org"
    And I log in as "rider@example.org" with password "correct-horse-battery"
    And I change my password from "correct-horse-battery" to "brand-new-password"
    And I reset the password with the link from the last email to "rider@example.org" to "attacker-password"
    Then the response status code is 400

  Scenario: A new password longer than 72 bytes is rejected
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And I log in as "rider@example.org" with password "correct-horse-battery"
    When I change my password from "correct-horse-battery" to "ääääääääääääääääääääääääääääääääääääääää"
    Then the response status code is 400

  Scenario: Without login the password cannot be changed
    When I change my password from "correct-horse-battery" to "brand-new-password"
    Then the response status code is 401

  Scenario: Too many attempts are rate limited
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And I log in as "rider@example.org" with password "correct-horse-battery"
    When I change my password from "wrong-password-123" to "brand-new-password" 10 times
    And I change my password from "correct-horse-battery" to "brand-new-password"
    Then the response status code is 429
