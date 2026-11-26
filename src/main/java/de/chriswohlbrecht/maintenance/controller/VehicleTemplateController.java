package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.VehicleTemplatesApi;
import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateRequest;
import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSummaryResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleType;
import de.chriswohlbrecht.maintenance.component.IVehicleTemplateComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VehicleTemplateController implements VehicleTemplatesApi {

    private final IVehicleTemplateComponent vehicleTemplateComponent;

    @Override
    public ResponseEntity<List<VehicleTemplateSummaryResponse>> searchVehicleTemplates(String make, VehicleType type,
                                                                                       String model, Integer year) {
        return ResponseEntity.ok(vehicleTemplateComponent.search(make, type, model, year));
    }

    @Override
    public ResponseEntity<VehicleTemplateResponse> getVehicleTemplate(String templateId) {
        return vehicleTemplateComponent.get(templateId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<ApplyTemplateResponse> applyVehicleTemplate(Long vehicleId,
                                                                      ApplyTemplateRequest applyTemplateRequest) {
        return vehicleTemplateComponent.apply(vehicleId, applyTemplateRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
