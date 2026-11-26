package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateRequest;
import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSourceKind;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSourceQuality;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSummaryResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleType;
import de.chriswohlbrecht.maintenance.configuration.VehicleTemplateProperties;
import de.chriswohlbrecht.maintenance.mapper.VehicleTemplateMapperImpl;
import de.chriswohlbrecht.maintenance.exception.InvalidTemplateRequestException;
import de.chriswohlbrecht.maintenance.exception.SubscriptionRequiredException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleTemplateComponentImplTest {

    @Mock
    private ICurrentUserComponent currentUserComponent;
    @Mock
    private IUserLocaleComponent userLocaleComponent;
    @Mock
    private IAccessComponent accessComponent;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private MaintenanceTaskRepository maintenanceTaskRepository;
    @Mock
    private IMaintenanceTaskComponent maintenanceTaskComponent;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneId.of("Europe/Berlin"));
    private static final Long USER_ID = 7L;
    private static final Long VEHICLE_ID = 5L;

    private SimpleMeterRegistry meterRegistry;
    private VehicleTemplateComponentImpl component;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        component = new VehicleTemplateComponentImpl(
                new VehicleTemplateCatalogComponentImpl(
                        new VehicleTemplateProperties("classpath*:template-catalog/valid/")),
                currentUserComponent, userLocaleComponent, new VehicleTemplateMapperImpl(), accessComponent,
                vehicleRepository, maintenanceTaskRepository, maintenanceTaskComponent, 
                CLOCK, meterRegistry);
    }

    private void userLocale(Locale locale) {
        AppUser user = new AppUser();
        lenient().when(currentUserComponent.getCurrentUser()).thenReturn(user);
        lenient().when(userLocaleComponent.localeOf(user)).thenReturn(locale);
    }

    private static List<String> ids(List<VehicleTemplateSummaryResponse> result) {
        return result.stream().map(VehicleTemplateSummaryResponse::getId).toList();
    }

    @Test
    void findsMakeAndModelRegardlessOfSpelling() {
        assertThat(ids(component.search("moto guzzi", null, "v7", null))).containsExactly("moto-guzzi-v7-2012");
        assertThat(ids(component.search("MOTO-GUZZI", null, "V 7", null))).containsExactly("moto-guzzi-v7-2012");
        assertThat(ids(component.search("Honda", null, "CB 650 R", null)))
                .containsExactly("honda-cb650r-2019", "honda-a-cb650r-2021");
    }

    @Test
    void filtersByYearAndType() {
        assertThat(ids(component.search("Honda", null, "CB650", 2020))).containsExactly("honda-cb650r-2019");
        assertThat(ids(component.search("Honda", null, "CB650", 2018))).isEmpty();
        assertThat(ids(component.search("Honda", null, "CB650", 2021))).containsExactly("honda-a-cb650r-2021");
        assertThat(ids(component.search("Moto Guzzi", null, null, 2030))).containsExactly("moto-guzzi-v7-2012");
        assertThat(ids(component.search("Honda", VehicleType.CAR, null, null))).isEmpty();
        assertThat(ids(component.search("Honda", VehicleType.MOTORCYCLE, "CB650", null)))
                .containsExactly("honda-cb650r-2019", "honda-a-cb650r-2021");
    }

    @Test
    void sortsByModelThenFirstYear() {
        assertThat(ids(component.search("Honda", null, null, null)))
                .containsExactly("honda-cb500f-2019", "honda-cb650r-2019", "honda-a-cb650r-2021");
    }

    @Test
    void findsByAliasOnly() {
        assertThat(ids(component.search("Honda", null, "hornet", null))).containsExactly("honda-cb500f-2019");
    }

    @Test
    void modelThatMatchesNothingFindsNothing() {
        assertThat(component.search("Honda", null, "gold wing", null)).isEmpty();
    }

    @Test
    void unknownOrBlankMakeFindsNothing() {
        assertThat(component.search("Yamaha", null, null, null)).isEmpty();
        assertThat(component.search(null, null, null, null)).isEmpty();
        assertThat(component.search(" ", null, "cb", null)).isEmpty();
    }

    @Test
    void summaryCarriesQualityAndTaskCount() {
        VehicleTemplateSummaryResponse honda = component.search("Honda", null, "CB650", 2019).getFirst();
        assertThat(honda.getSourceQuality()).isEqualTo(VehicleTemplateSourceQuality.MANUFACTURER);
        assertThat(honda.getTaskCount()).isEqualTo(2);
        assertThat(honda.getYearFrom()).isEqualTo(2019);
        assertThat(honda.getYearTo()).isEqualTo(2020);

        VehicleTemplateSummaryResponse kawasaki = component.search("Kawasaki", null, null, null).getFirst();
        assertThat(kawasaki.getSourceQuality()).isEqualTo(VehicleTemplateSourceQuality.SECONDARY);
        assertThat(kawasaki.getTaskCount()).isEqualTo(1);
    }

    @Test
    void getReportsSecondaryQualityForRepairPortalSource() {
        userLocale(Locale.GERMANY);

        VehicleTemplateResponse kawasaki = component.get("kawasaki-z900-2017").orElseThrow();
        assertThat(kawasaki.getSourceQuality()).isEqualTo(VehicleTemplateSourceQuality.SECONDARY);
        assertThat(kawasaki.getSources().getFirst().getKind()).isEqualTo(VehicleTemplateSourceKind.REPAIR_PORTAL);
    }

    @Test
    void getShowsEnglishTextsForEnglishAccounts() {
        userLocale(Locale.UK);

        VehicleTemplateResponse honda = component.get("honda-cb650r-2019").orElseThrow();
        assertThat(honda.getNotes()).isEqualTo("Test note");
        assertThat(honda.getSources().getFirst().getKind()).isEqualTo(VehicleTemplateSourceKind.MANUFACTURER);
        assertThat(honda.getTasks().getFirst().getName()).isEqualTo("Change test oil");
        assertThat(honda.getTasks().getFirst().getSourceIds()).containsExactly("manual");
        assertThat(honda.getTasks().getFirst().getDetails().getFirst().getLabel()).isEqualTo("Oil");
        assertThat(honda.getTasks().getFirst().getDetails().getFirst().getSourceIds()).containsExactly("manual");

        VehicleTemplateResponse guzzi = component.get("moto-guzzi-v7-2012").orElseThrow();
        assertThat(guzzi.getTasks().getFirst().getName()).isEqualTo("Own brake fluid");
        assertThat(guzzi.getTasks().getFirst().getDescription()).isEqualTo("Own description");
        assertThat(guzzi.getNotes()).isNull();
    }

    @Test
    void getShowsGermanTextsAndDictionaryFallback() {
        userLocale(Locale.GERMANY);

        VehicleTemplateResponse honda = component.get("honda-cb650r-2019").orElseThrow();
        assertThat(honda.getTasks().getFirst().getName()).isEqualTo("Testöl wechseln");
        assertThat(honda.getTasks().getFirst().getDescription()).isEqualTo("Das Öl erneuern.");
        assertThat(honda.getTasks().getFirst().getDetails().getFirst().getLabel()).isEqualTo("Öl");
        assertThat(honda.getTasks().get(1).getOneTime()).isTrue();
        assertThat(component.get("moto-guzzi-v7-2012").orElseThrow().getTasks().getFirst().getName())
                .isEqualTo("Eigene Bremsflüssigkeit");
    }

    @Test
    void getUnknownIsEmpty() {
        assertThat(component.get("unknown")).isEmpty();
    }

    private Vehicle ownVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(VEHICLE_ID);
        vehicle.setCurrentMileage(40000);
        vehicle.setFirstRegistrationDate(LocalDate.of(2012, 4, 1));
        lenient().when(currentUserComponent.getCurrentUserId()).thenReturn(USER_ID);
        lenient().when(vehicleRepository.findByIdAndUser_Id(VEHICLE_ID, USER_ID)).thenReturn(Optional.of(vehicle));
        lenient().when(maintenanceTaskRepository.findAllByVehicle_Id(VEHICLE_ID)).thenReturn(List.of());
        lenient().when(maintenanceTaskComponent.createMaintenanceTask(anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        userLocale(Locale.GERMANY);
        return vehicle;
    }

    private static ApplyTemplateTaskRequest selected(String key, Integer km, LocalDate date) {
        ApplyTemplateTaskRequest task = new ApplyTemplateTaskRequest(key);
        task.setLastDoneKm(km);
        task.setLastDoneDate(date);
        return task;
    }

    private static ApplyTemplateRequest applyRequest(ApplyTemplateTaskRequest... tasks) {
        return new ApplyTemplateRequest("honda-cb650r-2019", List.of(tasks));
    }

    private static MaintenanceTask existing(String templateKey, boolean active) {
        MaintenanceTask task = new MaintenanceTask();
        task.setTemplateKey(templateKey);
        task.setActive(active);
        return task;
    }

    @Test
    void applyCreatesSelectedTasksWithTemplateKeyAndCountsOnce() {
        ownVehicle();

        ApplyTemplateResponse response = component.apply(VEHICLE_ID, applyRequest(
                selected("engine-oil", 38000, null), selected("break-in-service", null, null))).orElseThrow();

        assertThat(response.getCreated()).isEqualTo(2);
        assertThat(response.getSkipped()).isZero();
        org.mockito.ArgumentCaptor<MaintenanceTaskRequest> captor =
                org.mockito.ArgumentCaptor.forClass(MaintenanceTaskRequest.class);
        verify(maintenanceTaskComponent).createMaintenanceTask(eq(VEHICLE_ID), captor.capture(), eq("engine-oil"));
        verify(maintenanceTaskComponent).createMaintenanceTask(eq(VEHICLE_ID), any(), eq("break-in-service"));
        assertThat(captor.getValue().getName()).isEqualTo("Testöl wechseln");
        assertThat(captor.getValue().getFirstDueKm()).isEqualTo(44000);
        assertThat(meterRegistry.counter("bm.vehicle_templates.applied").count()).isEqualTo(1.0);
    }

    @Test
    void applyingTwiceCreatesNothingTheSecondTime() {
        ownVehicle();
        when(maintenanceTaskRepository.findAllByVehicle_Id(VEHICLE_ID)).thenReturn(List.of(
                existing("engine-oil", false), existing(null, true)));

        ApplyTemplateResponse response = component.apply(VEHICLE_ID, applyRequest(
                selected("engine-oil", null, null))).orElseThrow();

        assertThat(response.getCreated()).isZero();
        assertThat(response.getSkipped()).isEqualTo(1);
        verify(maintenanceTaskComponent, never()).createMaintenanceTask(anyLong(), any(), any());
        assertThat(meterRegistry.counter("bm.vehicle_templates.applied").count()).isZero();
    }

    @Test
    void applySkipsOnlyTasksTheVehicleAlreadyHas() {
        ownVehicle();
        when(maintenanceTaskRepository.findAllByVehicle_Id(VEHICLE_ID))
                .thenReturn(List.of(existing("engine-oil", true)));

        ApplyTemplateResponse response = component.apply(VEHICLE_ID, applyRequest(
                selected("engine-oil", null, null), selected("break-in-service", null, null))).orElseThrow();

        assertThat(response.getCreated()).isEqualTo(1);
        assertThat(response.getSkipped()).isEqualTo(1);
        verify(maintenanceTaskComponent).createMaintenanceTask(eq(VEHICLE_ID), any(), eq("break-in-service"));
        assertThat(meterRegistry.counter("bm.vehicle_templates.applied").count()).isEqualTo(1.0);
    }

    @Test
    void applyToForeignVehicleIsEmpty() {
        when(currentUserComponent.getCurrentUserId()).thenReturn(USER_ID);
        when(vehicleRepository.findByIdAndUser_Id(VEHICLE_ID, USER_ID)).thenReturn(Optional.empty());

        assertThat(component.apply(VEHICLE_ID, applyRequest(selected("engine-oil", null, null)))).isEmpty();
        verifyNoInteractions(maintenanceTaskComponent);
    }

    @Test
    void applyUnknownTemplateIsEmpty() {
        ownVehicle();

        assertThat(component.apply(VEHICLE_ID, new ApplyTemplateRequest("unknown",
                List.of(selected("engine-oil", null, null))))).isEmpty();
        verifyNoInteractions(maintenanceTaskComponent);
    }

    @Test
    void applyWithoutWriteAccessIsRejectedBeforeAnythingElse() {
        doThrow(new SubscriptionRequiredException()).when(accessComponent).requireWriteAccess();

        assertThatThrownBy(() -> component.apply(VEHICLE_ID, applyRequest(selected("engine-oil", null, null))))
                .isInstanceOf(SubscriptionRequiredException.class);
        verifyNoInteractions(vehicleRepository, maintenanceTaskComponent);
    }

    @Test
    void applyRejectsInvalidSelections() {
        ownVehicle();

        assertInvalid(selected("unknown-key", null, null));
        assertInvalid(selected("engine-oil", null, null), selected("engine-oil", null, null));
        assertInvalid(selected("engine-oil", 40001, null));
        assertInvalid(selected("engine-oil", null, LocalDate.of(2026, 10, 30)));
        assertInvalid(selected("engine-oil", null, LocalDate.of(2012, 3, 31)));
        verifyNoInteractions(maintenanceTaskComponent);
    }

    @Test
    void applyAcceptsBoundaryValues() {
        ownVehicle();

        ApplyTemplateResponse response = component.apply(VEHICLE_ID, applyRequest(
                selected("engine-oil", 40000, LocalDate.of(2026, 10, 29)),
                selected("break-in-service", null, LocalDate.of(2012, 4, 1)))).orElseThrow();

        assertThat(response.getCreated()).isEqualTo(2);
    }

    @Test
    void applyValidatesBeforeCreatingAnything() {
        ownVehicle();

        assertInvalid(selected("engine-oil", null, null), selected("unknown-key", null, null));
        verify(maintenanceTaskComponent, never()).createMaintenanceTask(anyLong(), any(), any());
    }

    private void assertInvalid(ApplyTemplateTaskRequest... tasks) {
        assertThatThrownBy(() -> component.apply(VEHICLE_ID, applyRequest(tasks)))
                .isInstanceOf(InvalidTemplateRequestException.class);
    }
}
