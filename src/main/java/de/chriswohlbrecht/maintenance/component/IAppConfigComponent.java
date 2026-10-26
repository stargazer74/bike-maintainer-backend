package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AppConfigResponse;

public interface IAppConfigComponent {

    /** Public configuration the client needs at startup, e.g. the operating mode. */
    AppConfigResponse getAppConfig();
}
