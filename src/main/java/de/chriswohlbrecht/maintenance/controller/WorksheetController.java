package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.WorksheetsApi;
import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplateResponse;
import de.chriswohlbrecht.maintenance.component.IAccessComponent;
import de.chriswohlbrecht.maintenance.component.IWorksheetComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WorksheetController implements WorksheetsApi {

    private final IWorksheetComponent worksheetComponent;
    private final IAccessComponent accessComponent;

    @Override
    public ResponseEntity<List<WorksheetTemplateResponse>> listWorksheetTemplates(Long vehicleId) {
        return worksheetComponent.listWorksheetTemplates(vehicleId)
                .map(templates -> templates.stream().map(WorksheetTemplateResponse::new).toList())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Resource> downloadWorksheet(Long vehicleId, WorksheetTemplate template, Integer cylinders,
                                                      Integer valvesPerCylinder) {
        accessComponent.requireWriteAccess();
        return worksheetComponent.generateWorksheet(vehicleId, template, cylinders, valvesPerCylinder)
                .map(worksheet -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename(worksheet.fileName())
                                .build()
                                .toString())
                        .body((Resource) new ByteArrayResource(worksheet.content())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
