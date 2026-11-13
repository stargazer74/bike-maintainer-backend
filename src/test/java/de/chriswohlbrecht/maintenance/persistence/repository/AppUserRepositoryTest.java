package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Real SQL against H2 (MariaDB mode) with only the JPA slice. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AppUserRepositoryTest {

    private static final LocalDate CUTOFF = LocalDate.of(2025, 12, 19);

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void retentionCandidatesAreVerifiedUsersWithAnOldTrialOrAPendingNotice() {
        AppUser oldTrial = save("old@example.org", UserRole.USER, true, CUTOFF.minusDays(1), null);
        AppUser pendingNotice = save("notice@example.org", UserRole.USER, true, CUTOFF.plusDays(30),
                LocalDateTime.of(2026, 11, 1, 3, 30));
        save("recent@example.org", UserRole.USER, true, CUTOFF, null);
        save("admin@example.org", UserRole.ADMIN, true, CUTOFF.minusYears(2), null);
        save("unverified@example.org", UserRole.USER, false, CUTOFF.minusDays(1), null);
        save("no-trial@example.org", UserRole.USER, true, null, null);
        appUserRepository.save(AppUser.builder().displayName("Max").role(UserRole.USER).emailVerified(true)
                .trialStartedOn(CUTOFF.minusDays(1)).build());

        assertThat(appUserRepository.findRetentionCandidates(UserRole.USER, CUTOFF))
                .extracting(AppUser::getId)
                .containsExactly(oldTrial.getId(), pendingNotice.getId());
    }

    /** Of two checkouts at the same time, the reference stored first stays (backend review, finding 7). */
    @Test
    void billingCustomerRefIsOnlySetWhileMissing() {
        AppUser user = save("checkout@example.org", UserRole.USER, true, CUTOFF, null);

        assertThat(appUserRepository.setBillingCustomerRefIfMissing(user.getId(), "ref-first")).isEqualTo(1);
        assertThat(appUserRepository.setBillingCustomerRefIfMissing(user.getId(), "ref-second")).isZero();

        assertThat(appUserRepository.findBillingCustomerRefById(user.getId())).contains("ref-first");
    }

    @Test
    void deletionNoticeIsSetAndClearedWithoutTouchingOtherColumns() {
        AppUser user = save("notice-column@example.org", UserRole.USER, true, CUTOFF, null);
        LocalDateTime sentAt = LocalDateTime.of(2026, 11, 13, 3, 30);

        appUserRepository.setDeletionNoticeSentAt(user.getId(), sentAt);
        assertThat(appUserRepository.findById(user.getId())).get()
                .satisfies(current -> {
                    assertThat(current.getDeletionNoticeSentAt()).isEqualTo(sentAt);
                    assertThat(current.getTrialStartedOn()).isEqualTo(CUTOFF);
                });

        appUserRepository.setDeletionNoticeSentAt(user.getId(), null);
        assertThat(appUserRepository.findById(user.getId())).get()
                .extracting(AppUser::getDeletionNoticeSentAt).isNull();
    }

    @Test
    void lockedReadFindsTheAccount() {
        AppUser user = save("locked@example.org", UserRole.USER, true, CUTOFF, null);

        assertThat(appUserRepository.findLockedById(user.getId())).get()
                .extracting(AppUser::getEmail).isEqualTo("locked@example.org");
        assertThat(appUserRepository.findLockedById(-1L)).isEmpty();
    }

    private AppUser save(String email, UserRole role, boolean verified, LocalDate trialStartedOn,
                         LocalDateTime deletionNoticeSentAt) {
        return appUserRepository.save(AppUser.builder()
                .email(email)
                .role(role)
                .emailVerified(verified)
                .trialStartedOn(trialStartedOn)
                .deletionNoticeSentAt(deletionNoticeSentAt)
                .build());
    }
}
