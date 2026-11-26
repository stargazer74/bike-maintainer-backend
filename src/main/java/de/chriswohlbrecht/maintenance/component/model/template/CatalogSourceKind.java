package de.chriswohlbrecht.maintenance.component.model.template;

/** Kind of a source; {@link #MANUFACTURER} is preferred, all others count as secondary. */
public enum CatalogSourceKind {
    MANUFACTURER, DEALER, PRESS, REPAIR_PORTAL, FORUM
}
