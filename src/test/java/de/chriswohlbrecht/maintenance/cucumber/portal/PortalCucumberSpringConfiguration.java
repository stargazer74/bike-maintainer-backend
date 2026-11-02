package de.chriswohlbrecht.maintenance.cucumber.portal;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

@CucumberContextConfiguration
@ActiveProfiles("test")
/*
 * Initial admin as configured in the .env (BM_INITIAL_ADMIN_EMAIL/PASSWORD): created by migration V4, password set on
 * startup. Fixed here so that values from the developer's environment do not change the tests (access-control.feature).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bm.initial-admin.email=admin@localhost",
        "bm.initial-admin.password=initial-admin-password"
})
@Import(RecordingMailService.class)
public class PortalCucumberSpringConfiguration {
}
