Feature: Metrics for monitoring

  Scenario: The Prometheus endpoint is served on the management port
    Given a GET request is sent to "/api/v1/config"
    When a GET request is sent to the management path "/actuator/prometheus"
    Then the response status code is 200
    And the response body contains "application=\"bike-maintainer\""
    And the response body contains "mode=\"portal\""
    And the response body contains "http_server_requests_seconds_bucket"

  Scenario: The Prometheus endpoint is not served on the application port
    When a GET request is sent to "/actuator/prometheus"
    Then the response status code is not 200
