package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.ConfigApi;
import de.chriswohlbrecht.maintenance.api.model.AppConfigResponse;
import de.chriswohlbrecht.maintenance.component.IAppConfigComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConfigController implements ConfigApi {

    private final IAppConfigComponent appConfigComponent;

    @Override
    public ResponseEntity<AppConfigResponse> getAppConfig() {
        return ResponseEntity.ok(appConfigComponent.getAppConfig());
    }
}
