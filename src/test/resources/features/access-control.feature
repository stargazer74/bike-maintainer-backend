Feature: Access control – login and roles

  Scenario Outline: Protected endpoints require login
    When a <method> request without login is sent to "<path>"
    Then the response status code is 401

    Examples:
      | method | path                                          |
      | GET    | /api/v1/vehicles                              |
      | POST   | /api/v1/vehicles                              |
      | GET    | /api/v1/vehicles/1                            |
      | PUT    | /api/v1/vehicles/1                            |
      | DELETE | /api/v1/vehicles/1                            |
      | GET    | /api/v1/vehicles/1/maintenance-tasks          |
      | POST   | /api/v1/vehicles/1/maintenance-tasks          |
      | GET    | /api/v1/vehicles/1/maintenance-tasks/1        |
      | PUT    | /api/v1/vehicles/1/maintenance-tasks/1        |
      | DELETE | /api/v1/vehicles/1/maintenance-tasks/1        |
      | GET    | /api/v1/vehicles/1/maintenance-logs           |
      | POST   | /api/v1/vehicles/1/maintenance-logs           |
      | GET    | /api/v1/vehicles/1/maintenance-logs/1         |
      | PUT    | /api/v1/vehicles/1/maintenance-logs/1         |
      | DELETE | /api/v1/vehicles/1/maintenance-logs/1         |
      | GET    | /api/v1/vehicles/1/cost-summary               |
      | GET    | /api/v1/vehicles/1/maintenance-report         |
      | GET    | /api/v1/vehicles/1/maintenance-checklist      |
      | GET    | /api/v1/vehicles/1/worksheets                 |
      | GET    | /api/v1/vehicles/1/worksheets/BRAKES          |
      | GET    | /api/v1/auth/me                               |
      | PATCH  | /api/v1/auth/me                               |
      | PUT    | /api/v1/auth/password                         |
      | POST   | /api/v1/auth/logout                           |
      | GET    | /api/v1/admin/users                           |
      | PATCH  | /api/v1/admin/users/1                         |

  Scenario: Admin endpoints are forbidden for normal users
    Given I am logged in as "rider@example.org"
    When a GET request is sent to "/api/v1/admin/users"
    Then the response status code is 403
    When a PATCH request is sent to "/api/v1/admin/users/1" with body:
      """
      { "active": false }
      """
    Then the response status code is 403

  Scenario: The initial admin logs in with the password from the configuration
    When I log in as "admin@localhost" with password "initial-admin-password"
    Then the response status code is 200
    And the JSON response field "email" equals "admin@localhost"
    And the JSON response field "role" equals "ADMIN"
