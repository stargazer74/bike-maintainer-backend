package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** Only portal mode has registrations; household profiles have no email and are never touched. */
@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
@RequiredArgsConstructor
public class AccountCleanupComponentImpl implements IAccountCleanupComponent {

    static final Duration UNVERIFIED_ACCOUNT_LIFETIME = Duration.ofDays(7);

    private final AppUserRepository appUserRepository;
    private final IUserTokenComponent userTokenComponent;
    private final Clock clock;

    /** Tokens of deleted accounts go with them (ON DELETE CASCADE). */
    @Override
    @Transactional
    @Scheduled(cron = "${bm.cleanup.cron}")
    public void cleanUp() {
        int accounts = appUserRepository.deleteUnverifiedCreatedBefore(UserRole.USER,
                LocalDateTime.now(clock).minus(UNVERIFIED_ACCOUNT_LIFETIME));
        int tokens = userTokenComponent.deleteExpired();
        log.info("Cleanup: deleted {} unverified accounts and {} used or expired tokens", accounts, tokens);
    }
}
