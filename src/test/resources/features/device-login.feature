Feature: Apps stay logged in with a device token

  Background:
    Given a verified user "rider@example.org" with password "correct-horse-battery" exists

  Scenario: The app gets a new session with its device token after the session expired
    When I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 200
    And the response body contains "\"email\":\"rider@example.org\""
    When my session expires
    And a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 401
    When the app exchanges its device token
    Then the response status code is 200
    And the app received a new device token
    When a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 200

  Scenario: A token that was already exchanged removes the device
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    And the app exchanges its device token
    And the device "Pixel 8" was last used 2 minutes ago
    When the app exchanges its previous device token
    Then the response status code is 401
    When the app exchanges its device token
    Then the response status code is 401

  Scenario: The previous token right after an exchange fails but keeps the device
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    And the app exchanges its device token
    When the app exchanges its previous device token
    Then the response status code is 401
    And the user "rider@example.org" has 1 device
    When the app exchanges its device token
    Then the response status code is 200

  Scenario: A lost answer of the exchange does not cost the login when the app chose the next token
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    And the app exchanges its device token choosing the next one, but the answer is lost
    And the device "Pixel 8" was last used 2 minutes ago
    When the app exchanges the token it chose
    Then the response status code is 200
    And the user "rider@example.org" has 1 device
    When a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 200

  Scenario: A chosen next token that is not new is rejected and changes nothing
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    And the app exchanges its device token
    When the app exchanges its device token choosing the same token as next
    Then the response status code is 400
    When the app exchanges its device token choosing its previous token as next
    Then the response status code is 400
    When the app exchanges its device token
    Then the response status code is 200

  Scenario: A chosen next token in the wrong format is rejected
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    When a POST request is sent to "/api/v1/auth/device-session" with body:
      """
      { "deviceToken": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "nextDeviceToken": "too short" }
      """
    Then the response status code is 400

  Scenario: A detected copy also ends the session the thief opened with it
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    And a thief exchanges a copy of the device token
    And the device "Pixel 8" was last used 2 minutes ago
    When the app exchanges its device token
    Then the response status code is 401
    And the thief is no longer logged in

  Scenario: Two exchanges with the same token at once keep the device
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    When the app exchanges its device token twice at the same time
    Then exactly one of the exchanges succeeded
    And the user "rider@example.org" has 1 device

  Scenario: The device list marks the device of the current session
    Given I log in on a device "Tablet" as "rider@example.org" with password "correct-horse-battery"
    And I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    When a GET request is sent to "/api/v1/auth/devices"
    Then the response status code is 200
    And the JSON response array has 2 elements
    And the device "Pixel 8" is marked as current
    And the device "Tablet" is not marked as current

  Scenario: Logging out in the app removes the device
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    When I log out
    And the app exchanges its device token
    Then the response status code is 401

  Scenario: Removing the device in the web app logs the app out at once
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    And the web app is logged in as "rider@example.org" with password "correct-horse-battery"
    When the web app removes the device "Pixel 8"
    Then the web app got status 204
    When a GET request is sent to "/api/v1/vehicles"
    Then the response status code is 401
    When the app exchanges its device token
    Then the response status code is 401

  Scenario: Changing the password removes all devices
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    When I change my password from "correct-horse-battery" to "brand-new-password"
    Then the response status code is 204
    And the user "rider@example.org" has 0 devices
    When the app exchanges its device token
    Then the response status code is 401

  Scenario: Resetting the password removes all devices
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    When I request a password reset for "rider@example.org"
    And I reset the password with the link from the last email to "rider@example.org" to "brand-new-password"
    Then the response status code is 204
    When the app exchanges its device token
    Then the response status code is 401

  Scenario: Deleting the account removes the devices
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    When I delete my account with password "correct-horse-battery"
    Then the response status code is 204
    And the database holds no devices

  Scenario: A device unused for 90 days has to log in again
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    And the device "Pixel 8" was last used 91 days ago
    When the app exchanges its device token
    Then the response status code is 401
    And the user "rider@example.org" has 0 devices

  Scenario: Devices of other users cannot be removed
    Given I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    And I am logged in as "other@example.org"
    When I remove the device "Pixel 8"
    Then the response status code is 404
    And the user "rider@example.org" has 1 device

  Scenario: Wrong passwords on device login lock the account like on login
    Given the client IP is "203.0.113.60"
    When I fail to log in on a device as "rider@example.org" 9 times
    And the client IP is "203.0.113.61"
    And I fail to log in on a device as "rider@example.org" 1 times
    And I log in on a device "Pixel 8" as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 429

  Scenario: Too many device logins from one IP are rate limited
    When I fail to log in on a device as "rider@example.org" 10 times
    And a verified user "third@example.org" with password "correct-horse-battery" exists
    And I log in on a device "Pixel 8" as "third@example.org" with password "correct-horse-battery"
    Then the response status code is 429

  Scenario: A blank device name is rejected
    When I log in on a device "   " as "rider@example.org" with password "correct-horse-battery"
    Then the response status code is 400
