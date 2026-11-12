package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Household mode has neither trial nor subscription: everyone may do everything, without any database access. */
@Component
@Conditional(OperatingModeCondition.Household.class)
public class HouseholdAccessComponentImpl implements IAccessComponent {

    private static final Access FULL = Access.full(false);

    @Override
    public Access accessOf(AppUser user) {
        return FULL;
    }

    @Override
    public void requireWriteAccess() {
        // Nothing is restricted in household mode.
    }
}
