package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public Map<Long, Access> accessOf(List<AppUser> users) {
        return users.stream().collect(Collectors.toMap(AppUser::getId, user -> FULL));
    }

    @Override
    public Access requireWriteAccess() {
        // Nothing is restricted in household mode.
        return FULL;
    }
}
