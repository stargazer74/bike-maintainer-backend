package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.VehicleType;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogSourceKind;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTask;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTemplate;
import de.chriswohlbrecht.maintenance.component.model.template.LocalizedText;
import de.chriswohlbrecht.maintenance.configuration.VehicleTemplateProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleTemplateCatalogComponentImplTest {

    private static VehicleTemplateCatalogComponentImpl load(String directory) {
        return new VehicleTemplateCatalogComponentImpl(
                new VehicleTemplateProperties("classpath*:template-catalog/" + directory + "/"));
    }

    @Test
    void loadsValidCatalog() {
        VehicleTemplateCatalogComponentImpl catalog = load("valid");

        assertThat(catalog.templates()).extracting(CatalogTemplate::id)
                .containsExactly("honda-cb650r-2019", "kawasaki-z900-2017", "moto-guzzi-v7-2012");
        assertThat(catalog.find("honda-cb650r-2019")).isPresent();
        assertThat(catalog.find("nope")).isEmpty();
        assertThat(catalog.dictionary().details().get("oil").de()).isEqualTo("Öl");
        assertThat(catalog.dictionary().tasks().get("engine-oil").plausible())
                .containsEntry(VehicleType.MOTORCYCLE, Map.of(
                        "intervalKm", List.of(1000, 30000), "intervalMonths", List.of(6, 36)));
    }

    @Test
    void mapsTemplateFields() {
        VehicleTemplateCatalogComponentImpl catalog = load("valid");

        CatalogTemplate honda = catalog.find("honda-cb650r-2019").orElseThrow();
        assertThat(honda.type()).isEqualTo(VehicleType.MOTORCYCLE);
        assertThat(honda.aliases()).containsExactly("CB 650 R");
        assertThat(honda.years().from()).isEqualTo(2019);
        assertThat(honda.years().to()).isEqualTo(2020);
        assertThat(honda.notes()).isEqualTo(new LocalizedText("Testhinweis", "Test note"));
        CatalogTask oil = honda.tasks().getFirst();
        assertThat(oil.intervalKm()).isEqualTo(6000);
        assertThat(oil.firstDueKm()).isEqualTo(1000);
        assertThat(oil.details().getFirst().quantity()).isEqualTo("3 l");
        assertThat(honda.tasks().get(1).oneTime()).isTrue();

        CatalogTemplate guzzi = catalog.find("moto-guzzi-v7-2012").orElseThrow();
        assertThat(guzzi.aliases()).containsExactly("V 7");
        assertThat(guzzi.years().to()).isNull();
        assertThat(guzzi.tasks().getFirst().name().en()).isEqualTo("Own brake fluid");

        CatalogTemplate kawasaki = catalog.find("kawasaki-z900-2017").orElseThrow();
        assertThat(kawasaki.sources().getFirst().kind()).isEqualTo(CatalogSourceKind.REPAIR_PORTAL);
        assertThat(kawasaki.tasks().getFirst().plausibilityException()).isNotBlank();
        assertThat(kawasaki.aliases()).isEmpty();
    }

    @Test
    void failsOnUnknownField() {
        assertThatThrownBy(() -> load("invalid-unknown-field"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roadster-2001.yaml")
                .hasMessageContaining("bogus");
    }

    @Test
    void failsOnUnknownTaskKey() {
        assertThatThrownBy(() -> load("invalid-unknown-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roadster-2001.yaml")
                .hasMessageContaining("no-such-task");
    }

    @Test
    void failsOnDuplicateId() {
        assertThatThrownBy(() -> load("invalid-duplicate-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roadster-2001")
                .hasMessageContaining("duplicate id acme-roadster-2001");
    }

    @Test
    void failsOnUnknownSourceRef() {
        assertThatThrownBy(() -> load("invalid-source-ref"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roadster-2001.yaml")
                .hasMessageContaining("ghost");
    }

    @Test
    void failsOnMissingRequiredField() {
        assertThatThrownBy(() -> load("invalid-missing-field"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roadster-2001.yaml")
                .hasMessageContaining("model is missing");
    }

    @Test
    void failsOnMalformedYaml() {
        assertThatThrownBy(() -> load("invalid-bad-yaml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roadster-2001.yaml");
    }

    @Test
    void failsOnEmptyCatalog() {
        assertThatThrownBy(() -> load("empty"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no templates");
    }

    @Test
    void failsWithoutDictionary() {
        assertThatThrownBy(() -> load("does-not-exist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tasks.yaml")
                .hasMessageContaining("no templates");
    }

    @Test
    void localizedTextPicksEnglishOnlyForEnglish() {
        LocalizedText text = new LocalizedText("Hallo", "Hello");

        assertThat(text.in(Locale.ENGLISH)).isEqualTo("Hello");
        assertThat(text.in(Locale.UK)).isEqualTo("Hello");
        assertThat(text.in(Locale.GERMAN)).isEqualTo("Hallo");
        assertThat(text.in(Locale.FRENCH)).isEqualTo("Hallo");
        assertThat(text.in(null)).isEqualTo("Hallo");
    }

    @Test
    void propertiesFallBackToTheCatalogOfTheArtifact() {
        assertThat(new VehicleTemplateProperties(null).location()).isEqualTo("classpath*:vehicle-templates/");
        assertThat(new VehicleTemplateProperties(" ").location()).isEqualTo("classpath*:vehicle-templates/");
    }

    @Test
    void loadsTheRealCatalogOfTheArtifact() {
        VehicleTemplateCatalogComponentImpl catalog =
                new VehicleTemplateCatalogComponentImpl(new VehicleTemplateProperties(null));

        assertThat(catalog.templates()).isNotEmpty();
        assertThat(catalog.dictionary().tasks()).containsKey("engine-oil");
    }
}
