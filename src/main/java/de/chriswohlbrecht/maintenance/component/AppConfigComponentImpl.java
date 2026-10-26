package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AppConfigResponse;
import de.chriswohlbrecht.maintenance.api.model.OperatingMode;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AppConfigComponentImpl implements IAppConfigComponent {

    private final OperatingModeProperties operatingModeProperties;

    @Override
    public AppConfigResponse getAppConfig() {
        return new AppConfigResponse()
                .mode(OperatingMode.fromValue(operatingModeProperties.mode().name().toLowerCase(Locale.ROOT)));
    }
}
