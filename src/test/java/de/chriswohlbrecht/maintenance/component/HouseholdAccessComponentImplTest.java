package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HouseholdAccessComponentImplTest {

    private final HouseholdAccessComponentImpl component = new HouseholdAccessComponentImpl();

    /** Even a profile that never had a trial (household profiles have no email to verify) may do everything. */
    @Test
    void everyProfileHasFullAccess() {
        AppUser profile = AppUser.builder().id(3L).displayName("Anna").role(UserRole.USER).build();

        Access access = component.accessOf(profile);

        assertThat(access).isEqualTo(new Access(AccessStatus.FULL, null, false));
        assertThat(access.canWrite()).isTrue();
    }

    @Test
    void writingIsNeverRestricted() {
        assertThatCode(component::requireWriteAccess).doesNotThrowAnyException();
    }
}
