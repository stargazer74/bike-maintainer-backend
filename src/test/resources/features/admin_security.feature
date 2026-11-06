Feature: Admin User Management Security

  In order to protect admin endpoints
  As a user
  I want to be able to access /api/v1/admin/users only if I have the ADMIN role

  Scenario: Admin successfully lists users
    Given an admin user with email "admin@example.com" and password "secret"
    And a regular user with email "user@example.com" and password "secret"
    When the admin performs a GET request to "/api/v1/admin/users"
    Then the response status should be 200 OK
    And the response should contain "user@example.com"

  Scenario: Non-admin access is forbidden
    Given a regular user with email "user@example.com" and password "secret"
    When the user performs a GET request to "/api/v1/admin/users"
    Then the response status should be 403 Forbidden
