package de.chriswohlbrecht.maintenance.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminUserControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private TestRestTemplate restTemplate = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/admin/users";
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        AppUser admin = new AppUser();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("secret"));
        admin.setEnabled(true);
        admin.setRoles(Set.of(Role.ADMIN, Role.USER));
        userRepository.save(admin);

        AppUser user = new AppUser();
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user.setEnabled(true);
        user.setRoles(Set.of(Role.USER));
        userRepository.save(user);
    }

    @Test
    void adminCanRetrieveUserList() {
        ResponseEntity<String> response = restTemplate.withBasicAuth("admin@example.com", "secret").getForEntity(baseUrl(), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("user@example.com"));
    }

    @Test
    void nonAdminGetsForbidden() {
        ResponseEntity<String> response = restTemplate.withBasicAuth("user@example.com", "secret").getForEntity(baseUrl(), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
