package de.chriswohlbrecht.maintenance.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code BM_MODE} decides between portal and household security; a typo must stop the startup. */
class OperatingModePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsPortal() {
        runner.withPropertyValues("bm.mode=portal")
                .run(context -> assertThat(context.getBean(OperatingModeProperties.class).mode())
                        .isEqualTo(OperatingMode.PORTAL));
    }

    @Test
    void bindsHousehold() {
        runner.withPropertyValues("bm.mode=household")
                .run(context -> assertThat(context.getBean(OperatingModeProperties.class).mode())
                        .isEqualTo(OperatingMode.HOUSEHOLD));
    }

    @Test
    void unknownModeFailsTheStartup() {
        runner.withPropertyValues("bm.mode=housefold")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("housefold");
                });
    }

    @Test
    void missingModeFailsTheStartup() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("bm.mode");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OperatingModeProperties.class)
    static class PropertiesConfiguration {
    }
}
