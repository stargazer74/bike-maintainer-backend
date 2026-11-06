package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import io.cucumber.java.en.Given;
import org.springframework.beans.factory.annotation.Autowired;

/** Portal only: user administration by admins. */
public class AdminSteps {

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private AppUserRepository appUserRepository;

    @Given("the id of the user {string} is remembered as {string}")
    public void theIdOfTheUserIsRememberedAs(String email, String alias) {
        scenarioContext.storeId(alias, appUserRepository.findByEmailIgnoreCase(email).orElseThrow().getId());
    }
}
