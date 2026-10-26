package de.chriswohlbrecht.maintenance.cucumber.household;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/** Own in-memory database, so the household context does not share data with the portal context. */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bm.mode=household",
        "spring.datasource.url=jdbc:h2:mem:household;MODE=MariaDB;DB_CLOSE_DELAY=-1"
})
public class HouseholdCucumberSpringConfiguration {
}
