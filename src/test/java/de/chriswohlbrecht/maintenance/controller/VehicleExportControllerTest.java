package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.VehicleExportApi;
import de.chriswohlbrecht.maintenance.api.model.ExportedVehicle;
import de.chriswohlbrecht.maintenance.api.model.VehicleExport;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleType;
import de.chriswohlbrecht.maintenance.component.IVehicleExportComponent;
import de.chriswohlbrecht.maintenance.component.model.VehicleExportFile;
import de.chriswohlbrecht.maintenance.exception.ImportFormatUnsupportedException;
import de.chriswohlbrecht.maintenance.exception.ImportInvalidException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VehicleExportController.class)
@AutoConfigureMockMvc(addFilters = false)
class VehicleExportControllerTest {

    private static final String VALID_FILE = """
            { "formatVersion": 1, "exportedAt": "2026-11-11T10:00:00Z", "vehicle": {
                "name": "Die Rote", "type": "MOTORCYCLE", "modelYear": 2019, "firstRegistrationDate": "2019-05-01",
                "currentMileage": 21000 } }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IVehicleExportComponent vehicleExportComponent;

    @Test
    void exportVehicle_isDownloadedAsAttachment() throws Exception {
        VehicleExport export = new VehicleExport().formatVersion(1).exportedAt(OffsetDateTime.parse("2026-11-11T10:00:00Z"))
                .vehicle(new ExportedVehicle().name("Die Rote").type(VehicleType.MOTORCYCLE).modelYear(2019)
                        .firstRegistrationDate(LocalDate.of(2019, 5, 1)).currentMileage(21000));
        when(vehicleExportComponent.exportVehicle(7L))
                .thenReturn(Optional.of(new VehicleExportFile("vehicle-die-rote-2026-11-11.json", export)));

        mockMvc.perform(get(VehicleExportApi.PATH_EXPORT_VEHICLE, 7L))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"vehicle-die-rote-2026-11-11.json\""))
                .andExpect(jsonPath("$.formatVersion").value(1))
                .andExpect(jsonPath("$.vehicle.name").value("Die Rote"));
    }

    @Test
    void exportVehicle_ofAnUnknownVehicle_isNotFound() throws Exception {
        when(vehicleExportComponent.exportVehicle(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get(VehicleExportApi.PATH_EXPORT_VEHICLE, 7L))
                .andExpect(status().isNotFound());
    }

    @Test
    void importVehicle_answersWithTheCreatedVehicle() throws Exception {
        when(vehicleExportComponent.importVehicle(any())).thenReturn(new VehicleResponse().id(9L).name("Die Rote"));

        mockMvc.perform(post(VehicleExportApi.PATH_IMPORT_VEHICLE).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_FILE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void importVehicle_withInvalidFields_listsThemSorted() throws Exception {
        mockMvc.perform(post(VehicleExportApi.PATH_IMPORT_VEHICLE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "formatVersion": 1, "exportedAt": "2026-11-11T10:00:00Z", "vehicle": {
                                    "name": "", "type": "MOTORCYCLE", "modelYear": 1800,
                                    "firstRegistrationDate": "2019-05-01", "currentMileage": 0 } }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImportInvalidException.CODE))
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0]").value(startsWith("vehicle.modelYear: ")))
                .andExpect(jsonPath("$.details[1]").value(startsWith("vehicle.name: ")));
        verifyNoInteractions(vehicleExportComponent);
    }

    @Test
    void importVehicle_ofANewerFormat_isNotCheckedAgainstTheCurrentRules() throws Exception {
        mockMvc.perform(post(VehicleExportApi.PATH_IMPORT_VEHICLE).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"formatVersion\": 2, \"vehicle\": {} }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImportFormatUnsupportedException.CODE));
    }

    @Test
    void importVehicle_withoutFormatVersion_isInvalid() throws Exception {
        mockMvc.perform(post(VehicleExportApi.PATH_IMPORT_VEHICLE).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"vehicle\": {} }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImportInvalidException.CODE));
    }

    @Test
    void importVehicle_thatIsNoJsonObject_isInvalid() throws Exception {
        mockMvc.perform(post(VehicleExportApi.PATH_IMPORT_VEHICLE).contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImportInvalidException.CODE))
                .andExpect(jsonPath("$.details[0]").value("file: not a valid export file"));
    }

    @Test
    void importVehicle_rejectedByTheComponent_reportsTheDetails() throws Exception {
        when(vehicleExportComponent.importVehicle(any()))
                .thenThrow(new ImportInvalidException(List.of("vehicle.maintenanceTasks[0]: no interval")));

        mockMvc.perform(post(VehicleExportApi.PATH_IMPORT_VEHICLE).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_FILE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImportInvalidException.CODE))
                .andExpect(jsonPath("$.details[0]").value("vehicle.maintenanceTasks[0]: no interval"));
    }

    @Test
    void importVehicle_ofAnUnsupportedVersionFoundByTheComponent_isReported() throws Exception {
        when(vehicleExportComponent.importVehicle(any())).thenThrow(new ImportFormatUnsupportedException(3));

        mockMvc.perform(post(VehicleExportApi.PATH_IMPORT_VEHICLE).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_FILE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImportFormatUnsupportedException.CODE));
    }
}
