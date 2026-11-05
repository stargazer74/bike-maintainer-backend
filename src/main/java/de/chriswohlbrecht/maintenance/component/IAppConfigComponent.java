package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AppConfigResponse;

public interface IAppConfigComponent {

    /** Public configuration the client needs at startup: operating mode and, in portal mode, the website address. */
    AppConfigResponse getAppConfig();
}
