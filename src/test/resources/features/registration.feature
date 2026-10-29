Feature: Registration and email verification

  Scenario: Challenge for the ALTCHA widget
    When a GET request is sent to "/api/v1/auth/challenge"
    Then the response status code is 200
    And the response header "Cache-Control" contains "no-store"
    And the response body contains "\"signature\""
    And the response body contains "\"parameters\""

  Scenario: Register, verify the email address and log in
    When I register as "new@example.org" with password "correct-horse-battery"
    Then the response status code is 202
    And 1 email has been sent to "new@example.org"
    And the last email to "new@example.org" has subject "Bitte bestätige deine E-Mail-Adresse"
    When I log in as "new@example.org" with password "correct-horse-battery"
    Then the response status code is 403
    And the JSON response field "code" equals "EMAIL_NOT_VERIFIED"
    When I open the verification link from the last email to "new@example.org"
    Then the response status code is 204
    When I log in as "new@example.org" with password "correct-horse-battery"
    Then the response status code is 200
    And the JSON response field "email" equals "new@example.org"
    And the JSON response field "role" equals "USER"

  Scenario: The account gets the language sent by the client
    When I register as "new@example.org" with password "correct-horse-battery" and language "en"
    Then the response status code is 202
    And the last email to "new@example.org" has subject "Please confirm your email address"

  Scenario: Without a language the browser language is used
    Given the browser language is "en-US,en;q=0.9"
    When I register as "new@example.org" with password "correct-horse-battery"
    Then the last email to "new@example.org" has subject "Please confirm your email address"

  Scenario: Registering an existing address changes nothing and notifies the owner
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I register as "Rider@Example.org" with password "another-password-123"
    Then the response status code is 202
    And 1 email has been sent to "rider@example.org"
    And the last email to "rider@example.org" has subject "Registrierungsversuch mit deiner E-Mail-Adresse"
    When I log in as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 200

  Scenario: Responses for new and existing addresses are identical
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    When I register as "rider@example.org" with password "correct-horse-battery"
    And I remember the response
    And I register as "new@example.org" with password "correct-horse-battery"
    Then the response equals the remembered response

  Scenario: A verification link works only once
    When I register as "new@example.org" with password "correct-horse-battery"
    And I open the verification link from the last email to "new@example.org"
    Then the response status code is 204
    When I open the verification link from the last email to "new@example.org"
    Then the response status code is 400

  Scenario: An expired verification link is rejected
    When I register as "new@example.org" with password "correct-horse-battery"
    And the verification link of "new@example.org" has expired
    And I open the verification link from the last email to "new@example.org"
    Then the response status code is 400

  Scenario: An unknown token is rejected
    When a POST request is sent to "/api/v1/auth/verify-email" with body:
      """
      { "token": "this-token-does-not-exist" }
      """
    Then the response status code is 400

  Scenario: Resending the verification email
    When I register as "new@example.org" with password "correct-horse-battery"
    And I remember the verification link from the last email to "new@example.org"
    And I request a new verification email for "new@example.org"
    Then the response status code is 204
    And 2 emails have been sent to "new@example.org"
    When I open the remembered verification link
    Then the response status code is 400
    When I open the verification link from the last email to "new@example.org"
    Then the response status code is 204

  Scenario: Resending answers the same for unknown, verified and unverified addresses
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And an unverified user "new@example.org" with password "correct-horse-battery" exists
    When I request a new verification email for "nobody@example.org"
    Then the response status code is 204
    And I remember the response
    When I request a new verification email for "rider@example.org"
    Then the response equals the remembered response
    When I request a new verification email for "new@example.org"
    Then the response equals the remembered response
    And 0 emails have been sent to "nobody@example.org"
    And 0 emails have been sent to "rider@example.org"
    And 1 email has been sent to "new@example.org"

  Scenario Outline: Registration without a valid ALTCHA solution is rejected
    When a POST request is sent to "/api/v1/auth/register" with body:
      """
      { "email": "new@example.org", "password": "correct-horse-battery" <altcha> }
      """
    Then the response status code is 400
    And the JSON response field "code" equals "CAPTCHA_INVALID"
    And 0 emails have been sent to "new@example.org"

    Examples:
      | altcha                     |
      |                            |
      | , "altcha": "not-a-payload" |

  Scenario: Resending without a valid ALTCHA solution is rejected
    When a POST request is sent to "/api/v1/auth/verify-email/resend" with body:
      """
      { "email": "new@example.org", "altcha": "not-a-payload" }
      """
    Then the response status code is 400
    And the JSON response field "code" equals "CAPTCHA_INVALID"

  Scenario: An ALTCHA solution can be used only once
    When I register as "new@example.org" with password "correct-horse-battery"
    And I register as "other@example.org" with password "correct-horse-battery" reusing the last ALTCHA solution
    Then the response status code is 400
    And the JSON response field "code" equals "CAPTCHA_INVALID"
    And 0 emails have been sent to "other@example.org"

  Scenario: A password longer than 72 bytes is rejected before the ALTCHA solution is used
    When I register as "new@example.org" with password "ääääääääääääääääääääääääääääääääääääääää"
    Then the response status code is 400
    And 0 emails have been sent to "new@example.org"
    And no account exists for "new@example.org"

  Scenario: A filled honeypot is accepted but ignored
    When I register as "bot@example.org" with password "correct-horse-battery" and the honeypot filled
    Then the response status code is 202
    And 0 emails have been sent to "bot@example.org"
    And no account exists for "bot@example.org"

  Scenario: When the daily email limit is reached, every address gets 429
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists
    And the daily email limit is reached
    When I register as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 429
    And the JSON response field "code" equals "MAIL_QUOTA_EXCEEDED"
    When I register as "new@example.org" with password "correct-horse-battery"
    Then the response status code is 429
    And the JSON response field "code" equals "MAIL_QUOTA_EXCEEDED"
    And no account exists for "new@example.org"
    When I request a new verification email for "new@example.org"
    Then the response status code is 429
    And the JSON response field "code" equals "MAIL_QUOTA_EXCEEDED"

  Scenario: Unverified accounts are deleted after 7 days
    Given an unverified user "old@example.org" registered 8 days ago
    And an unverified user "recent@example.org" registered 6 days ago
    When the account cleanup runs
    Then no account exists for "old@example.org"
    And an account exists for "recent@example.org"
