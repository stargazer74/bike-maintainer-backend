package de.chriswohlbrecht.maintenance.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PortalStartupCheckTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PortalStartupCheck.class);

    @Test
    void failsWithoutAltchaKey() {
        runner.withPropertyValues("bm.mode=portal", "spring.mail.host=smtp.example.org", "bm.altcha.hmac-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("BM_ALTCHA_HMAC_KEY");
                });
    }

    @Test
    void failsWithoutMailHostOutsideLocalProfile() {
        runner.withPropertyValues("bm.mode=portal", "spring.mail.host=", "bm.altcha.hmac-key=secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("SPRING_MAIL_HOST");
                });
    }

    @Test
    void startsWithoutMailHostInLocalProfile() {
        runner.withPropertyValues("bm.mode=portal", "spring.profiles.active=local", "bm.altcha.hmac-key=secret")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startsWithMailHostAndAltchaKey() {
        runner.withPropertyValues("bm.mode=portal", "spring.mail.host=smtp.example.org", "bm.altcha.hmac-key=secret")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void notLoadedInHouseholdMode() {
        runner.withPropertyValues("bm.mode=household")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PortalStartupCheck.class);
                });
    }
}
