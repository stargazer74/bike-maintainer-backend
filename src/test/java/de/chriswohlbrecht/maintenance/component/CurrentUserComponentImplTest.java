package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.SessionUser;
import de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserComponentImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    private CurrentUserComponentImpl currentUserComponent;

    @BeforeEach
    void setUp() {
        currentUserComponent = new CurrentUserComponentImpl(appUserRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_loggedIn_returnsUserFromDatabase() {
        AppUser user = AppUser.builder().id(7L).email("rider@example.org").build();
        login(new SessionUser(7L, "rider@example.org"));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThat(currentUserComponent.getCurrentUser()).isSameAs(user);
    }

    @Test
    void getCurrentUser_notLoggedIn_fails() {
        assertThatThrownBy(() -> currentUserComponent.getCurrentUser())
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void getCurrentUser_userDeletedMeanwhile_fails() {
        login(new SessionUser(7L, "rider@example.org"));
        when(appUserRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserComponent.getCurrentUser())
                .isInstanceOf(AuthenticationFailedException.class);
    }

    private static void login(SessionUser principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
