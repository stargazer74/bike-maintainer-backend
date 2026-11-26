package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSourceKind;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSourceQuality;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSummaryResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleType;
import de.chriswohlbrecht.maintenance.configuration.VehicleTemplateProperties;
import de.chriswohlbrecht.maintenance.mapper.VehicleTemplateMapperImpl;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class VehicleTemplateComponentImplTest {

    @Mock
    private ICurrentUserComponent currentUserComponent;
    @Mock
    private IUserLocaleComponent userLocaleComponent;

    private VehicleTemplateComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new VehicleTemplateComponentImpl(
                new VehicleTemplateCatalogComponentImpl(
                        new VehicleTemplateProperties("classpath*:template-catalog/valid/")),
                currentUserComponent, userLocaleComponent, new VehicleTemplateMapperImpl());
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
        assertThat(ids(component.search("Honda", null, "CB 650 R", null))).containsExactly("honda-cb650r-2019");
    }

    @Test
    void filtersByYearAndType() {
        assertThat(ids(component.search("Honda", null, "CB650", 2020))).containsExactly("honda-cb650r-2019");
        assertThat(ids(component.search("Honda", null, "CB650", 2018))).isEmpty();
        assertThat(ids(component.search("Honda", null, "CB650", 2021))).isEmpty();
        assertThat(ids(component.search("Moto Guzzi", null, null, 2030))).containsExactly("moto-guzzi-v7-2012");
        assertThat(ids(component.search("Honda", VehicleType.CAR, null, null))).isEmpty();
        assertThat(ids(component.search("Honda", VehicleType.MOTORCYCLE, null, null)))
                .containsExactly("honda-cb650r-2019");
    }

    @Test
    void unknownOrBlankMakeFindsNothing() {
        assertThat(component.search("Yamaha", null, null, null)).isEmpty();
        assertThat(component.search(null, null, null, null)).isEmpty();
        assertThat(component.search(" ", null, "cb", null)).isEmpty();
    }

    @Test
    void summaryCarriesQualityAndTaskCount() {
        VehicleTemplateSummaryResponse honda = component.search("Honda", null, null, null).getFirst();
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
}
