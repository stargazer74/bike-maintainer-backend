package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.AccessStatus;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.configuration.RetentionProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Only portal mode has expiring accounts; household profiles and administrators are never touched. Each account is
 * handled on its own, so one that fails (e.g. the payment provider is down) does not stop the others; it is tried
 * again on the next run.
 */
@Slf4j
@Component
@Conditional(OperatingModeCondition.Portal.class)
public class AccountRetentionComponentImpl implements IAccountRetentionComponent {

    private static final String METRIC = "bm.accounts.retention";
    private static final String NOTICE_SENT = "notice_sent";
    private static final String DELETED = "deleted";
    private static final String FAILED = "failed";
    private static final List<String> ACTIONS = List.of(NOTICE_SENT, "notice_deferred", DELETED, "kept", FAILED);
    /** Slack for the pre-selection: adding and subtracting months is not exactly reversible at month ends. */
    private static final int PRESELECTION_SLACK_DAYS = 7;

    private final AppUserRepository appUserRepository;
    private final IAccessComponent accessComponent;
    private final IAccountMailComponent accountMailComponent;
    private final IAccountDataDeletionComponent accountDataDeletionComponent;
    private final ISessionComponent sessionComponent;
    private final RetentionProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injected Spring beans are meant to be shared")
    public AccountRetentionComponentImpl(AppUserRepository appUserRepository, IAccessComponent accessComponent,
                                         IAccountMailComponent accountMailComponent,
                                         IAccountDataDeletionComponent accountDataDeletionComponent,
                                         ISessionComponent sessionComponent, RetentionProperties properties,
                                         Clock clock, MeterRegistry meterRegistry) {
        this.appUserRepository = appUserRepository;
        this.accessComponent = accessComponent;
        this.accountMailComponent = accountMailComponent;
        this.accountDataDeletionComponent = accountDataDeletionComponent;
        this.sessionComponent = sessionComponent;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        // Registered with 0 up front: a counter that only appears with its first event hides it from increase().
        ACTIONS.forEach(action -> meterRegistry.counter(METRIC, "action", action));
    }

    /** No surrounding transaction: every saved notice and every deletion stands on its own. */
    @Override
    @Scheduled(cron = "${bm.cleanup.cron}")
    public void applyRetention() {
        LocalDate today = LocalDate.now(clock);
        // Access ends with the trial at the earliest, so an account can only be due if its trial started long enough
        // ago; accounts with a pending notice are checked anyway (to withdraw it, e.g. after a new subscription).
        LocalDate trialStartedBefore = today.minus(properties.period()).plus(properties.noticePeriod())
                .plusDays(PRESELECTION_SLACK_DAYS);
        int noticesSent = 0;
        int deleted = 0;
        for (AppUser candidate : appUserRepository.findRetentionCandidates(UserRole.USER, trialStartedBefore)) {
            try {
                // Read again: the list is a snapshot, the person may have logged in or been deleted since.
                Optional<AppUser> user = appUserRepository.findById(candidate.getId());
                if (user.isEmpty()) {
                    continue;
                }
                String action = handle(user.get(), today, noticesSent < properties.noticeDailyLimit());
                noticesSent += NOTICE_SENT.equals(action) ? 1 : 0;
                deleted += DELETED.equals(action) ? 1 : 0;
            } catch (RuntimeException e) {
                // Most likely the payment provider; logged without personal data, retried on the next run.
                count(FAILED);
                log.warn("Retention of user {} failed: {}", candidate.getId(), e.getClass().getSimpleName());
            }
        }
        log.info("Retention: sent {} deletion notices, deleted {} expired accounts", noticesSent, deleted);
    }

    @Override
    public void keepAfterLogin(AppUser user) {
        if (user.getDeletionNoticeSentAt() == null) {
            return;
        }
        user.setDeletionNoticeSentAt(null);
        user.setKeepUntil(LocalDate.now(clock).plus(properties.period()));
        appUserRepository.save(user);
        count("kept");
        log.info("User {} logged in after the deletion notice, account kept", user.getId());
    }

    /** @return the action taken ({@link #NOTICE_SENT}, {@link #DELETED}, …), or null if there was nothing to do */
    private String handle(AppUser user, LocalDate today, boolean mayNotify) {
        LocalDate deletionDay = deletionDayOf(user);
        if (deletionDay == null) {
            withdrawNotice(user);
            return null;
        }
        LocalDateTime noticeSentAt = user.getDeletionNoticeSentAt();
        if (noticeSentAt == null) {
            if (today.isBefore(deletionDay.minus(properties.noticePeriod()))) {
                return null;
            }
            if (!mayNotify) {
                count("notice_deferred");
                return null;
            }
            return sendNotice(user, today, deletionDay) ? NOTICE_SENT : FAILED;
        }
        // Never earlier than a full notice period after the notice, even if the notice went out late.
        if (today.isBefore(deletionDay)
                || today.isBefore(noticeSentAt.toLocalDate().plus(properties.noticePeriod()))) {
            return null;
        }
        return delete(user, today, noticeSentAt) ? DELETED : null;
    }

    /**
     * The day the account is due for deletion, or null if it is not expired (trial, subscription, administrator) or
     * the end of its access is unknown.
     */
    private LocalDate deletionDayOf(AppUser user) {
        Access access = accessComponent.accessOf(user);
        if (access.status() != AccessStatus.EXPIRED || access.until() == null) {
            return null;
        }
        LocalDate due = access.until().plus(properties.period());
        LocalDate keepUntil = user.getKeepUntil();
        return keepUntil != null && keepUntil.isAfter(due) ? keepUntil : due;
    }

    /**
     * The mail names the real day: never earlier than a full notice period from now. It only counts as sent once the
     * mail server accepted it, otherwise it is tried again on the next run – an account is never deleted without it.
     */
    private boolean sendNotice(AppUser user, LocalDate today, LocalDate deletionDay) {
        LocalDate earliest = today.plus(properties.noticePeriod());
        if (!accountMailComponent.sendDeletionNotice(user, deletionDay.isBefore(earliest) ? earliest : deletionDay)) {
            count(FAILED);
            log.warn("Deletion notice to user {} not accepted by the mail server, tried again next run", user.getId());
            return false;
        }
        // Only this column: the copy of the account may be outdated by now.
        appUserRepository.setDeletionNoticeSentAt(user.getId(), LocalDateTime.now(clock));
        count(NOTICE_SENT);
        return true;
    }

    private void withdrawNotice(AppUser user) {
        if (user.getDeletionNoticeSentAt() != null) {
            appUserRepository.setDeletionNoticeSentAt(user.getId(), null);
        }
    }

    /**
     * Checked once more on the locked account right before deleting: a login since then (notice withdrawn, account
     * kept) or a new subscription keeps it.
     */
    private boolean delete(AppUser user, LocalDate today, LocalDateTime noticeSentAt) {
        boolean deleted = accountDataDeletionComponent.deleteWithAllData(user, current ->
                noticeSentAt.equals(current.getDeletionNoticeSentAt()) && isDue(current, today));
        if (!deleted) {
            log.info("User {} not deleted: the account changed during the retention run", user.getId());
            return false;
        }
        sessionComponent.invalidateSessionsOf(user.getId());
        count(DELETED);
        log.info("User {} deleted: access expired and no login after the deletion notice", user.getId());
        return true;
    }

    private boolean isDue(AppUser user, LocalDate today) {
        LocalDate deletionDay = deletionDayOf(user);
        return deletionDay != null && !today.isBefore(deletionDay);
    }

    private void count(String action) {
        meterRegistry.counter(METRIC, "action", action).increment();
    }
}
