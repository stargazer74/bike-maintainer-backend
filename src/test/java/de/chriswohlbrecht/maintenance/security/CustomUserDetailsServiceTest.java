package de.chriswohlbrecht.maintenance.security;

import de.chriswohlbrecht.maintenance.user.AppUser;
import de.chriswohlbrecht.maintenance.user.AppUserRepository;
import de.chriswohlbrecht.maintenance.user.AppUserService;
import de.chriswohlbrecht.maintenance.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class CustomUserDetailsServiceTest {

    @Autowired private AppUserRepository userRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        AppUser admin = new AppUser();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv"); // dummy bcrypt
        admin.setEnabled(true);
        admin.setRoles(Set.of(Role.ADMIN, Role.USER));
        userRepository.save(admin);
        service = new CustomUserDetailsService(new AppUserService(userRepository));
    }

    @Test
    void loadUserByUsername_findsExistingUser() {
        UserDetails details = service.loadUserByUsername("admin@example.com");
        assertEquals("admin@example.com", details.getUsername());
        assertTrue(details.isEnabled());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsername_throwsIfNotFound() {
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("notfound@example.com"));
    }
}
