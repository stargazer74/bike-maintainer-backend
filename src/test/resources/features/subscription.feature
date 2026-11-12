Feature: Subscription via Polar – checkout, webhooks and customer portal (portal mode)

  Background:
    Given I am logged in as "rider@example.org"

  Scenario: The checkout leads to the hosted page of Polar
    When a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    Then the response status code is 200
    And the response body contains "https://polar.test/checkout/"
    And the response body does not contain "rider@example.org"

  Scenario: A signed webhook activates the subscription
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And the trial of "rider@example.org" has ended
    And Polar reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    When Polar sends a signed "subscription.active" event for subscription "sub_1"
    Then the response status code is 202
    When a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "ACTIVE"
    And the JSON response field "hasSubscription" equals "true"

  Scenario: The same webhook twice does no harm
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And Polar reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    When Polar sends a signed "subscription.created" event for subscription "sub_1"
    And Polar sends a signed "subscription.updated" event for subscription "sub_1"
    Then the response status code is 202
    When a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "ACTIVE"

  Scenario: A revoked subscription leaves the account read-only
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And the trial of "rider@example.org" has ended
    And Polar reports subscription "sub_1" of "rider@example.org" as revoked
    When Polar sends a signed "subscription.revoked" event for subscription "sub_1"
    And a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "EXPIRED"

  Scenario: An unsigned webhook is rejected and changes nothing
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And the trial of "rider@example.org" has ended
    And Polar reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    When someone sends an unsigned "subscription.active" event for subscription "sub_1"
    Then the response status code is 403
    When a GET request is sent to "/api/v1/auth/me"
    Then the JSON response field "accessStatus" equals "EXPIRED"

  Scenario: No second checkout while a subscription is active
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And Polar reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    And Polar sends a signed "subscription.active" event for subscription "sub_1"
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
    And Polar reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    And Polar sends a signed "subscription.active" event for subscription "sub_1"
    When a POST request is sent to "/api/v1/subscription/customer-portal" with body:
      """
      {}
      """
    Then the response status code is 200
    And the response body contains "https://polar.test/portal/"

  Scenario: Deleting the account ends the subscription at Polar
    Given a POST request is sent to "/api/v1/subscription/checkout" with body:
      """
      {}
      """
    And Polar reports subscription "sub_1" of "rider@example.org" as "active" until in 365 days
    And Polar sends a signed "subscription.active" event for subscription "sub_1"
    When I delete my account with password "test-password-123"
    Then the response status code is 204
    And Polar revoked subscription "sub_1"

  Scenario: An oversized webhook is rejected before it is read
    When an oversized webhook is sent
    Then the response status code is 413
