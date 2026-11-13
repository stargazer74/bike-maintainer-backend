Feature: Subscription via Paddle – checkout, webhooks and customer portal (portal mode)

  Background:
    Given I am logged in as "rider@example.org"

  Scenario: The checkout leads to the payment page with the Paddle transaction
    When a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    Then the response status code is 200
    And the response body contains "https://app.example.test/abo/bezahlen?_ptxn="
    And the response body does not contain "rider@example.org"

  Scenario: A signed webhook activates the subscription
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And the trial of "rider@example.org" has ended
    And Paddle reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    When Paddle sends a signed "subscription.activated" event for subscription "sub_1"
    Then the response status code is 202
    When a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "ACTIVE"
    And the JSON response field "hasSubscription" equals "true"

  Scenario: The same webhook twice does no harm
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And Paddle reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    When Paddle sends a signed "subscription.created" event for subscription "sub_1"
    And Paddle sends a signed "subscription.updated" event for subscription "sub_1"
    Then the response status code is 202
    When a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "ACTIVE"

  Scenario: A revoked subscription leaves the account read-only
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And the trial of "rider@example.org" has ended
    And Paddle reports subscription "sub_1" of "rider@example.org" as canceled
    When Paddle sends a signed "subscription.canceled" event for subscription "sub_1"
    And a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "EXPIRED"

  Scenario: An unsigned webhook is rejected and changes nothing
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And the trial of "rider@example.org" has ended
    And Paddle reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    When someone sends an unsigned "subscription.activated" event for subscription "sub_1"
    Then the response status code is 403
    When a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "EXPIRED"

  Scenario: No second checkout while a subscription is active
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And Paddle reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    And Paddle sends a signed "subscription.activated" event for subscription "sub_1"
    When a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    Then the response status code is 409
    And the JSON response field "code" equals "ALREADY_SUBSCRIBED"

  Scenario: The customer portal exists only with a subscription
    When a POST request is sent to "/api/v1/subscription/customer-portal" with body:
      """
      {}
      """
    Then the response status code is 404
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And Paddle reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    And Paddle sends a signed "subscription.activated" event for subscription "sub_1"
    When a POST request is sent to "/api/v1/subscription/customer-portal" with body:
      """
      {}
      """
    Then the response status code is 200
    And the response body contains "https://customer-portal.paddle.test/"

  Scenario: Deleting the account ends the subscription at Paddle
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And Paddle reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    And Paddle sends a signed "subscription.activated" event for subscription "sub_1"
    When I delete my account with password "test-password-123"
    Then the response status code is 204
    And Paddle revoked subscription "sub_1"

  Scenario: An oversized webhook is rejected before it is read
    When an oversized webhook is sent
    Then the response status code is 413
