package de.chriswohlbrecht.maintenance.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

public class ExcludeConfig implements ImportOption {
    @Override
    public boolean includes(Location location) {
        return !location.contains("de/chriswohlbrecht/maintenance/configuration");
    }
}
