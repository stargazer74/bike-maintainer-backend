package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What a worksheet is prefilled with (Epic #113): the target values of the vehicle and the shims installed at the last
 * valve clearance measurement.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP",
        justification = "Target values are only read while the worksheet is rendered, within one request")
public record WorksheetPrefill(VehicleSpecification specification, Map<Valve, BigDecimal> installedShims) {

    /** One valve of the engine: cylinder 1–8, kind, index 1–2. */
    public record Valve(int cylinder, ValveKind kind, int index) {
    }

    public WorksheetPrefill {
        specification = specification == null ? new VehicleSpecification() : specification;
        installedShims = Map.copyOf(installedShims);
    }

    /** Nothing stored: all fields stay empty. */
    public static WorksheetPrefill none() {
        return new WorksheetPrefill(null, Map.of());
    }
}
