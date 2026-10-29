package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.UserTokenRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.persistence.type.UserTokenType;
import de.chriswohlbrecht.maintenance.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AccountCleanupComponentImplTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MutableClock clock;
    private UserTokenComponentImpl userTokenComponent;
    private AccountCleanupComponentImpl component;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-29T03:30:00Z"), ZoneId.of("Europe/Berlin"));
        userTokenComponent = new UserTokenComponentImpl(userTokenRepository, clock);
        component = new AccountCleanupComponentImpl(appUserRepository, userTokenComponent, clock);
    }

    @Test
    void deletesUnverifiedAccountsOlderThanSevenDays() {
        AppUser old = user("old@example.org", false, 8);
        AppUser recent = user("recent@example.org", false, 6);
        AppUser verified = user("verified@example.org", true, 30);

        component.cleanUp();

        assertThat(appUserRepository.findById(old.getId())).isEmpty();
        assertThat(appUserRepository.findById(recent.getId())).isPresent();
        assertThat(appUserRepository.findById(verified.getId())).isPresent();
        assertThat(appUserRepository.findAll()).anyMatch(user -> user.getRole() == UserRole.ADMIN);
    }

    @Test
    void keepsHouseholdProfilesWithoutEmail() {
        AppUser profile = appUserRepository.saveAndFlush(
                AppUser.builder().displayName("Anna").role(UserRole.USER).emailVerified(false).build());
        jdbcTemplate.update("UPDATE app_user SET created_at = ? WHERE id = ?",
                LocalDateTime.now(clock).minusDays(30), profile.getId());

        component.cleanUp();

        assertThat(appUserRepository.findById(profile.getId())).isPresent();
    }

    @Test
    void deletesTokensOfDeletedAccounts() {
        AppUser old = user("old@example.org", false, 8);
        userTokenComponent.issue(old, UserTokenType.EMAIL_VERIFICATION, Duration.ofDays(30));

        component.cleanUp();

        assertThat(userTokenRepository.count()).isZero();
    }

    @Test
    void deletesExpiredTokens() {
        AppUser verified = user("verified@example.org", true, 30);
        userTokenComponent.issue(verified, UserTokenType.PASSWORD_RESET, Duration.ofHours(1));
        clock.advance(Duration.ofHours(2));

        component.cleanUp();

        assertThat(userTokenRepository.count()).isZero();
    }

    private AppUser user(String email, boolean verified, int ageInDays) {
        AppUser user = appUserRepository.saveAndFlush(
                AppUser.builder().email(email).role(UserRole.USER).emailVerified(verified).build());
        jdbcTemplate.update("UPDATE app_user SET created_at = ? WHERE id = ?",
                LocalDateTime.now(clock).minusDays(ageInDays), user.getId());
        return user;
    }
}
