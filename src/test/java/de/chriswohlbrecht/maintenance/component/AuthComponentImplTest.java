package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.Currency;
import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.api.model.UpdateCurrentUserRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.exception.InvalidUserUpdateException;
import de.chriswohlbrecht.maintenance.mapper.UserMapperImpl;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthComponentImplTest {

    @Mock
    private ICurrentUserComponent currentUserComponent;
    @Mock
    private AppUserRepository appUserRepository;

    private AuthComponentImpl component;
    private AppUser user;

    @BeforeEach
    void setUp() {
        component = new AuthComponentImpl(null, null, null, currentUserComponent, new UserMapperImpl(),
                appUserRepository);
        user = new AppUser();
        user.setLanguage("de");
    }

    @Test
    void updateCurrentUser_currencyOnly_keepsLanguage() {
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(appUserRepository.save(user)).thenReturn(user);

        UserResponse response = component.updateCurrentUser(new UpdateCurrentUserRequest().currency(Currency.CHF));

        assertThat(response.getCurrency()).isEqualTo(Currency.CHF);
        assertThat(response.getLanguage()).isEqualTo(Language.DE);
        assertThat(user.getCurrency()).isEqualTo("CHF");
    }

    @Test
    void updateCurrentUser_languageOnly_keepsCurrency() {
        user.setCurrency("GBP");
        when(currentUserComponent.getCurrentUser()).thenReturn(user);
        when(appUserRepository.save(user)).thenReturn(user);

        UserResponse response = component.updateCurrentUser(new UpdateCurrentUserRequest().language(Language.EN));

        assertThat(response.getLanguage()).isEqualTo(Language.EN);
        assertThat(user.getCurrency()).isEqualTo("GBP");
    }

    @Test
    void updateCurrentUser_neitherField_throwsAndSavesNothing() {
        assertThatThrownBy(() -> component.updateCurrentUser(new UpdateCurrentUserRequest()))
                .isInstanceOf(InvalidUserUpdateException.class);

        verify(appUserRepository, never()).save(any());
    }
}
