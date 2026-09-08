package de.chriswohlbrecht.maintenance.cucumber;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthSteps {

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${spring.application.name}")
    private String applicationName;

    @Given("the Spring application context is running")
    public void theSpringApplicationContextIsRunning() {
        assertThat(applicationContext).isNotNull();
    }

    @Then("the application name should be {string}")
    public void theApplicationNameShouldBe(String expectedName) {
        assertThat(applicationName).isEqualTo(expectedName);
    }
}
