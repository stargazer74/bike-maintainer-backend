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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserTokenComponentImplTest {

    private static final Duration VALIDITY = Duration.ofHours(24);

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MutableClock clock;
    private UserTokenComponentImpl component;
    private AppUser user;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-29T10:00:00Z"), ZoneId.of("Europe/Berlin"));
        component = new UserTokenComponentImpl(userTokenRepository, clock);
        user = appUserRepository.save(AppUser.builder().email("rider@example.org").role(UserRole.USER).build());
    }

    @Test
    void issuedTokenIsUrlSafeAndOnlyHashIsStored() throws Exception {
        String token = component.issue(user, UserTokenType.EMAIL_VERIFICATION, VALIDITY);

        assertThat(token).matches("^[A-Za-z0-9_-]{43}$");
        List<String> hashes = jdbcTemplate.queryForList("SELECT token_hash FROM user_token", String.class);
        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        assertThat(hashes).containsExactly(expectedHash);
    }

    @Test
    void consumeReturnsUserOnlyOnce() {
        String token = component.issue(user, UserTokenType.EMAIL_VERIFICATION, VALIDITY);

        Optional<AppUser> first = component.consume(token, UserTokenType.EMAIL_VERIFICATION);
        Optional<AppUser> second = component.consume(token, UserTokenType.EMAIL_VERIFICATION);

        assertThat(first).map(AppUser::getId).contains(user.getId());
        assertThat(second).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = component.issue(user, UserTokenType.EMAIL_VERIFICATION, VALIDITY);
        clock.advance(VALIDITY.plusSeconds(1));

        assertThat(component.consume(token, UserTokenType.EMAIL_VERIFICATION)).isEmpty();
    }

    @Test
    void tokenIsValidUntilShortlyBeforeExpiry() {
        String token = component.issue(user, UserTokenType.EMAIL_VERIFICATION, VALIDITY);
        clock.advance(VALIDITY.minusSeconds(1));

        assertThat(component.consume(token, UserTokenType.EMAIL_VERIFICATION)).isPresent();
    }

    @Test
    void wrongTypeIsRejected() {
        String token = component.issue(user, UserTokenType.EMAIL_VERIFICATION, VALIDITY);

        assertThat(component.consume(token, UserTokenType.PASSWORD_RESET)).isEmpty();
    }

    @Test
    void unknownTokenIsRejected() {
        assertThat(component.consume("does-not-exist", UserTokenType.EMAIL_VERIFICATION)).isEmpty();
    }

    @Test
    void newTokenInvalidatesOlderOne() {
        String older = component.issue(user, UserTokenType.EMAIL_VERIFICATION, VALIDITY);
        String newer = component.issue(user, UserTokenType.EMAIL_VERIFICATION, VALIDITY);

        assertThat(component.consume(older, UserTokenType.EMAIL_VERIFICATION)).isEmpty();
        assertThat(component.consume(newer, UserTokenType.EMAIL_VERIFICATION)).isPresent();
    }

    @Test
    void newTokenKeepsTokensOfOtherType() {
        String reset = component.issue(user, UserTokenType.PASSWORD_RESET, VALIDITY);
        component.issue(user, UserTokenType.EMAIL_VERIFICATION, VALIDITY);

        assertThat(component.consume(reset, UserTokenType.PASSWORD_RESET)).isPresent();
    }

    @Test
    void deleteExpiredRemovesUsedAndExpired() {
        String used = component.issue(user, UserTokenType.PASSWORD_RESET, VALIDITY);
        component.consume(used, UserTokenType.PASSWORD_RESET);
        component.issue(user, UserTokenType.EMAIL_VERIFICATION, Duration.ofHours(1));
        clock.advance(Duration.ofHours(2));
        String open = component.issue(user, UserTokenType.PASSWORD_RESET, VALIDITY);

        int deleted = component.deleteExpired();

        assertThat(deleted).isEqualTo(2);
        assertThat(component.consume(open, UserTokenType.PASSWORD_RESET)).isPresent();
    }
}
