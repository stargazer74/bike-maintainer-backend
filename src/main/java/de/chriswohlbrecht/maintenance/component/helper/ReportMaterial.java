package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.type.MaterialUnit;

import java.math.BigDecimal;

/** Material line of a report entry; independent of the JPA entity. {@code partNumber} and {@code price} may be null. */
public record ReportMaterial(
        String name,
        String partNumber,
        BigDecimal quantity,
        MaterialUnit unit,
        BigDecimal price) {
}
