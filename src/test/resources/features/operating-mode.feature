Feature: Portal mode (default)

  Scenario: Config reports portal mode without login
    When a GET request is sent to "/api/v1/config"
    Then the response status code is 200
    And the JSON response field "mode" equals "portal"
    And the client has a "XSRF-TOKEN" cookie

  Scenario: Profile endpoints do not exist
    When a GET request is sent to "/api/v1/profiles"
    Then the response status code is 404
    When a POST request without CSRF token is sent to "/api/v1/profiles" with body:
      """
      { "displayName": "Anna" }
      """
    Then the response status code is 404

  Scenario: Profile endpoints do not exist for logged-in users either
    Given I am logged in as "rider@example.org"
    When a GET request is sent to "/api/v1/profiles"
    Then the response status code is 404
    When a POST request is sent to "/api/v1/profiles/1/select" with body:
      """
      {}
      """
    Then the response status code is 404
