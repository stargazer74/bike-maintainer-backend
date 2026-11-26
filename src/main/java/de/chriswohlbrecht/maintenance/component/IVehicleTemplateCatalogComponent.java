package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.template.CatalogDictionary;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTemplate;

import java.util.List;
import java.util.Optional;

/** The vehicle template catalog, loaded and checked once at startup. */
public interface IVehicleTemplateCatalogComponent {

    /** All templates, sorted by id. */
    List<CatalogTemplate> templates();

    Optional<CatalogTemplate> find(String id);

    CatalogDictionary dictionary();
}
