package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AppConfigResponse;
import de.chriswohlbrecht.maintenance.api.model.OperatingMode;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeProperties;
import de.chriswohlbrecht.maintenance.configuration.WebsiteProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AppConfigComponentImpl implements IAppConfigComponent {

    private final OperatingModeProperties operatingModeProperties;

    /** Only present in portal mode (WebsiteConfiguration). */
    private final ObjectProvider<WebsiteProperties> websiteProperties;

    @Override
    public AppConfigResponse getAppConfig() {
        AppConfigResponse response = new AppConfigResponse()
                .mode(OperatingMode.fromValue(operatingModeProperties.mode().name().toLowerCase(Locale.ROOT)));
        WebsiteProperties website = websiteProperties.getIfAvailable();
        if (website != null) {
            website.url().map(URI::create).ifPresent(response::websiteUrl);
        }
        return response;
    }
}
