Feature: Password reset by email

  Scenario: Reset the password with the emailed link and log in
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I request a password reset for "Rider@Example.org"
    Then the response status code is 204
    And 1 email has been sent to "rider@example.org"
    And the last email to "rider@example.org" has subject "Passwort zurücksetzen"
    When I reset the password with the link from the last email to "rider@example.org" to "brand-new-password"
    Then the response status code is 204
    When I log in as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 401
    When I log in as "rider@example.org" with password "brand-new-password"
    Then the response status code is 200

  Scenario: The email is sent in the language of the account
    Given a verified user "rider@example.org" with password "correct-horse-battery" and language "en" exists
    When I request a password reset for "rider@example.org"
    Then the last email to "rider@example.org" has subject "Reset your password"

  Scenario: A reset link works only once
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I request a password reset for "rider@example.org"
    And I reset the password with the link from the last email to "rider@example.org" to "brand-new-password"
    Then the response status code is 204
    When I reset the password with the link from the last email to "rider@example.org" to "another-new-password"
    Then the response status code is 400
    When I log in as "rider@example.org" with password "brand-new-password"
    Then the response status code is 200

  Scenario: An expired reset link is rejected
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I request a password reset for "rider@example.org"
    And the password reset link of "rider@example.org" has expired
    And I reset the password with the link from the last email to "rider@example.org" to "brand-new-password"
    Then the response status code is 400

  Scenario: A new request invalidates the older link
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I request a password reset for "rider@example.org"
    And I remember the password reset link from the last email to "rider@example.org"
    And I request a password reset for "rider@example.org"
    And I reset the password with the remembered link to "brand-new-password"
    Then the response status code is 400
    When I reset the password with the link from the last email to "rider@example.org" to "brand-new-password"
    Then the response status code is 204

  Scenario: A verification link cannot be used to reset the password
    When I register as "new@example.org" with password "correct-horse-battery"
    And I reset the password with the token from the last email to "new@example.org" to "brand-new-password"
    Then the response status code is 400

  Scenario: Unknown and deactivated addresses get the same answer but no email
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And a deactivated user "gone@example.org" with password "correct-horse-battery" exists
    When I request a password reset for "rider@example.org"
    Then the response status code is 204
    And I remember the response
    When I request a password reset for "nobody@example.org"
    Then the response equals the remembered response
    When I request a password reset for "gone@example.org"
    Then the response equals the remembered response
    And 0 emails have been sent to "nobody@example.org"
    And 0 emails have been sent to "gone@example.org"

  Scenario: The reset verifies an unverified account
    Given an unverified user "new@example.org" with password "correct-horse-battery" exists
    When I request a password reset for "new@example.org"
    And I reset the password with the link from the last email to "new@example.org" to "brand-new-password"
    Then the response status code is 204
    When I log in as "new@example.org" with password "brand-new-password"
    Then the response status code is 200

  Scenario: The reset lifts the account lock
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And the client IP is "198.51.100.210"
    And I fail to log in as "rider@example.org" 10 times
    And the client IP is "198.51.100.211"
    When I request a password reset for "rider@example.org"
    And I reset the password with the link from the last email to "rider@example.org" to "brand-new-password"
    And I log in as "rider@example.org" with password "brand-new-password"
    Then the response status code is 200

  Scenario: The reset ends all sessions of the user
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And I am logged in as "rider@example.org" with password "correct-horse-battery" in a second browser
    When I request a password reset for "rider@example.org"
    And I reset the password with the link from the last email to "rider@example.org" to "brand-new-password"
    Then the second browser is no longer logged in

  Scenario: A password longer than 72 bytes is rejected without using up the link
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I request a password reset for "rider@example.org"
    And I reset the password with the link from the last email to "rider@example.org" to "ääääääääääääääääääääääääääääääääääääääää"
    Then the response status code is 400
    When I reset the password with the link from the last email to "rider@example.org" to "brand-new-password"
    Then the response status code is 204

  Scenario: Requesting without a valid ALTCHA solution is rejected
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When a POST request is sent to "/api/v1/auth/password-reset/request" with body:
      """
      { "email": "rider@example.org", "altcha": "not-a-payload" }
      """
    Then the response status code is 400
    And the JSON response field "code" equals "CAPTCHA_INVALID"
    And 0 emails have been sent to "rider@example.org"

  Scenario: When the daily email limit is reached, the request gets 429
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And the daily email limit is reached
    When I request a password reset for "rider@example.org"
    Then the response status code is 429
    And the JSON response field "code" equals "MAIL_QUOTA_EXCEEDED"
    When I request a password reset for "nobody@example.org"
    Then the response status code is 429
