Feature: Application health

  Scenario: Spring application context is available
    Given the Spring application context is running
    Then the application name should be "maintenance"
