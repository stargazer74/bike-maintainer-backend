package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertThat(component.requireWriteAccess().status()).isEqualTo(AccessStatus.FULL);
    }

    @Test
    void manyProfilesAllHaveFullAccess() {
        AppUser anna = AppUser.builder().id(3L).displayName("Anna").role(UserRole.USER).build();
        AppUser ben = AppUser.builder().id(4L).displayName("Ben").role(UserRole.USER).build();

        assertThat(component.accessOf(List.of(anna, ben)))
                .containsOnlyKeys(3L, 4L)
                .allSatisfy((id, access) -> assertThat(access.status()).isEqualTo(AccessStatus.FULL));
    }
}
