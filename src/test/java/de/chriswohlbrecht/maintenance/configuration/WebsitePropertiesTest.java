package de.chriswohlbrecht.maintenance.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code BM_WEBSITE_URL}: portal mode only, a trailing slash is dropped, a typo stops the startup. */
class WebsitePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(WebsiteConfiguration.class);

    @Test
    void portalWithUrl_dropsTrailingSlash() {
        runner.withPropertyValues("bm.mode=portal", "bm.website-url=https://www.example.com/")
                .run(context -> assertThat(context.getBean(WebsiteProperties.class).url())
                        .contains("https://www.example.com"));
    }

    @Test
    void portalWithPath_keepsPath() {
        runner.withPropertyValues("bm.mode=portal", "bm.website-url=http://example.com/bike//")
                .run(context -> assertThat(context.getBean(WebsiteProperties.class).url())
                        .contains("http://example.com/bike"));
    }

    @Test
    void portalWithoutUrl_isEmpty() {
        runner.withPropertyValues("bm.mode=portal", "bm.website-url=")
                .run(context -> assertThat(context.getBean(WebsiteProperties.class).url()).isEmpty());
    }

    @Test
    void portalWithoutProperty_isEmpty() {
        runner.withPropertyValues("bm.mode=portal")
                .run(context -> assertThat(context.getBean(WebsiteProperties.class).url()).isEmpty());
    }

    @Test
    void portalWithInvalidUrl_failsTheStartup() {
        runner.withPropertyValues("bm.mode=portal", "bm.website-url=www.example.com")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("websiteUrl");
                });
    }

    @Test
    void portalWithOtherScheme_failsTheStartup() {
        runner.withPropertyValues("bm.mode=portal", "bm.website-url=javascript:alert(1)")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void household_ignoresTheProperty() {
        runner.withPropertyValues("bm.mode=household", "bm.website-url=not a url")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WebsiteProperties.class);
                });
    }
}
