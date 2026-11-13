package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AppConfigResponse;
import de.chriswohlbrecht.maintenance.api.model.OperatingMode;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeProperties;
import de.chriswohlbrecht.maintenance.configuration.PaddleProperties;
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

    /** The client-side token and the environment are public values for Paddle.js on the payment page. */
    @Test
    void portal_returnsPaymentConfig() {
        AppConfigResponse response = component(de.chriswohlbrecht.maintenance.configuration.OperatingMode.PORTAL,
                null).getAppConfig();

        assertThat(response.getPayment().getClientToken()).isEqualTo("test_token");
        assertThat(response.getPayment().getSandbox()).isTrue();
    }

    @Test
    void portalWithoutClientToken_omitsPaymentConfig() {
        assertThat(component(de.chriswohlbrecht.maintenance.configuration.OperatingMode.PORTAL, null,
                new PaddleProperties("https://api.paddle.com", null, null, null, " ")).getAppConfig().getPayment())
                .isNull();
        assertThat(component(de.chriswohlbrecht.maintenance.configuration.OperatingMode.PORTAL, null,
                new PaddleProperties("https://api.paddle.com", null, null, null, null)).getAppConfig().getPayment())
                .isNull();
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
        assertThat(response.getPayment()).isNull();
    }

    private static AppConfigComponentImpl component(de.chriswohlbrecht.maintenance.configuration.OperatingMode mode,
                                                    WebsiteProperties website) {
        return component(mode, website, new PaddleProperties("https://sandbox-api.paddle.com", null, null, null,
                "test_token"));
    }

    private static AppConfigComponentImpl component(de.chriswohlbrecht.maintenance.configuration.OperatingMode mode,
                                                    WebsiteProperties website, PaddleProperties paddle) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (website != null) {
            beans.addBean("websiteProperties", website);
        }
        ObjectProvider<WebsiteProperties> provider = beans.getBeanProvider(WebsiteProperties.class);
        return new AppConfigComponentImpl(new OperatingModeProperties(mode), provider, paddle);
    }
}
