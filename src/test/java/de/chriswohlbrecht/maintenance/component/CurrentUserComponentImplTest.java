package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.InitialAdminProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        currentUserComponent = new CurrentUserComponentImpl(appUserRepository,
                new InitialAdminProperties("admin@localhost", null));
    }

    @Test
    void getCurrentUser_returnsInitialAdmin() {
        AppUser admin = AppUser.builder().id(1L).email("admin@localhost").build();
        when(appUserRepository.findByEmailIgnoreCase("admin@localhost")).thenReturn(Optional.of(admin));

        assertThat(currentUserComponent.getCurrentUser()).isSameAs(admin);
    }

    @Test
    void getCurrentUser_initialAdminMissing_fails() {
        when(appUserRepository.findByEmailIgnoreCase("admin@localhost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserComponent.getCurrentUser())
                .isInstanceOf(IllegalStateException.class);
    }
}
