package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AppConfigResponse;
import de.chriswohlbrecht.maintenance.api.model.OperatingMode;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeProperties;
import de.chriswohlbrecht.maintenance.configuration.WebsiteProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigComponentImplTest {

    @Test
    void portalWithWebsite_returnsModeAndUrl() {
        AppConfigResponse response = component(de.chriswohlbrecht.maintenance.configuration.OperatingMode.PORTAL,
                new WebsiteProperties("https://www.example.com/")).getAppConfig();

        assertThat(response.getMode()).isEqualTo(OperatingMode.PORTAL);
        assertThat(response.getWebsiteUrl()).isEqualTo(URI.create("https://www.example.com"));
    }

    @Test
    void portalWithoutWebsite_omitsUrl() {
        AppConfigResponse response = component(de.chriswohlbrecht.maintenance.configuration.OperatingMode.PORTAL,
                new WebsiteProperties("")).getAppConfig();

        assertThat(response.getWebsiteUrl()).isNull();
    }

    @Test
    void household_omitsUrl() {
        AppConfigResponse response = component(de.chriswohlbrecht.maintenance.configuration.OperatingMode.HOUSEHOLD,
                null).getAppConfig();

        assertThat(response.getMode()).isEqualTo(OperatingMode.HOUSEHOLD);
        assertThat(response.getWebsiteUrl()).isNull();
    }

    private static AppConfigComponentImpl component(de.chriswohlbrecht.maintenance.configuration.OperatingMode mode,
                                                    WebsiteProperties website) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (website != null) {
            beans.addBean("websiteProperties", website);
        }
        ObjectProvider<WebsiteProperties> provider = beans.getBeanProvider(WebsiteProperties.class);
        return new AppConfigComponentImpl(new OperatingModeProperties(mode), provider);
    }
}
