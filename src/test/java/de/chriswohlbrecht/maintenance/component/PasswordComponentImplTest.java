package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ChangePasswordRequest;
import de.chriswohlbrecht.maintenance.api.model.PasswordResetConfirmRequest;
import de.chriswohlbrecht.maintenance.exception.InvalidPasswordException;
import de.chriswohlbrecht.maintenance.exception.InvalidTokenException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import de.chriswohlbrecht.maintenance.persistence.type.UserTokenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordComponentImplTest {

    private static final String TOO_LONG = "ä".repeat(40);

    @Mock
    private IAltchaComponent altchaComponent;
    @Mock
    private IMailQuotaComponent mailQuotaComponent;
    @Mock
    private IAccountMailComponent accountMailComponent;
    @Mock
    private IUserTokenComponent userTokenComponent;
    @Mock
    private ISessionComponent sessionComponent;
    @Mock
    private ICurrentUserComponent currentUserComponent;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordComponentImpl component;

    @Test
    void tooLongPasswordDoesNotUseUpTheToken() {
        assertThatThrownBy(() -> component.confirmReset(
                new PasswordResetConfirmRequest().token("token").newPassword(TOO_LONG)))
                .isInstanceOf(InvalidPasswordException.class);

        verifyNoInteractions(userTokenComponent);
    }

    @Test
    void resetSetsPasswordVerifiesUnlocksAndEndsAllSessions() {
        AppUser user = user(true);
        user.setEmailVerified(false);
        user.setFailedLoginAttempts(10);
        user.setLockedUntil(LocalDateTime.of(2026, 10, 29, 12, 0));
        when(userTokenComponent.consume("token", UserTokenType.PASSWORD_RESET)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("brand-new-password")).thenReturn("{bcrypt}new");

        component.confirmReset(new PasswordResetConfirmRequest().token("token").newPassword("brand-new-password"));

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(appUserRepository).save(user);
        verify(sessionComponent).invalidateSessionsOf(7L);
    }

    @Test
    void resetOfDeactivatedAccountIsRejected() {
        AppUser user = user(false);
        when(userTokenComponent.consume("token", UserTokenType.PASSWORD_RESET)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> component.confirmReset(
                new PasswordResetConfirmRequest().token("token").newPassword("brand-new-password")))
                .isInstanceOf(InvalidTokenException.class);

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}old");
        verify(appUserRepository, never()).save(any());
        verify(sessionComponent, never()).invalidateSessionsOf(anyLong());
    }

    @Test
    void wrongCurrentPasswordChangesNothing() {
        AppUser user = user(true);
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("wrong-password", "{bcrypt}old")).thenReturn(false);

        assertThatThrownBy(() -> component.changePassword(new ChangePasswordRequest()
                .currentPassword("wrong-password").newPassword("brand-new-password"), "session-1"))
                .isInstanceOf(InvalidPasswordException.class);

        verify(appUserRepository, never()).save(any());
        verify(sessionComponent, never()).invalidateOtherSessionsOf(anyLong(), anyString());
    }

    @Test
    void changeKeepsCurrentSessionAndRevokesResetLinks() {
        AppUser user = user(true);
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("correct-horse-battery", "{bcrypt}old")).thenReturn(true);
        when(passwordEncoder.encode("brand-new-password")).thenReturn("{bcrypt}new");

        component.changePassword(new ChangePasswordRequest()
                .currentPassword("correct-horse-battery").newPassword("brand-new-password"), "session-1");

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
        verify(userTokenComponent).revokeOpen(user, UserTokenType.PASSWORD_RESET);
        verify(sessionComponent).invalidateOtherSessionsOf(7L, "session-1");
    }

    private static AppUser user(boolean active) {
        return AppUser.builder().id(7L).email("rider@example.org").passwordHash("{bcrypt}old")
                .role(UserRole.USER).active(active).emailVerified(true).build();
    }
}
