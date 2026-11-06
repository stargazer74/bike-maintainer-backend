package de.chriswohlbrecht.maintenance.cucumber;

import de.chriswohlbrecht.maintenance.user.AppUser;
import de.chriswohlbrecht.maintenance.user.AppUserRepository;
import de.chriswohlbrecht.maintenance.user.Role;
import io.cucumber.java.en.*;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminSecuritySteps {

    @LocalServerPort
    int port;

    @Autowired
    AppUserRepository repo;

    @Autowired
    PasswordEncoder encoder;

    private TestRestTemplate template = new TestRestTemplate();

    private ResponseEntity<String> response;

    @Given("an admin user with email \"(?<email>.+?)\" and password \"(?<pw>.+?)\"")
    public void createAdminUser(String email, String pw) {
        repo.deleteAll();
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(pw));
        u.setEnabled(true);
        u.setRoles(Set.of(Role.ADMIN, Role.USER));
        repo.save(u);
    }

    @Given("a regular user with email \"(?<email>.+?)\" and password \"(?<pw>.+?)\"")
    public void createRegularUser(String email, String pw) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(pw));
        u.setEnabled(true);
        u.setRoles(Set.of(Role.USER));
        repo.save(u);
    }

    @When("the admin performs a GET request to \"(?<path>.+?)\"")
    public void adminGets(String path) {
        response = template.withBasicAuth("admin@example.com", "secret").getForEntity("http://localhost:" + port + path, String.class);
    }

    @When("the user performs a GET request to \"(?<path>.+?)\"")
    public void userGets(String path) {
        response = template.withBasicAuth("user@example.com", "secret").getForEntity("http://localhost:" + port + path, String.class);
    }

    @Then("the response status should be 200 OK")
    public void status200() {
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Then("the response status should be 403 Forbidden")
    public void status403() {
        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Then("the response should contain \"(?<text>.+?)\"")
    public void containsText(String text) {
        Assertions.assertTrue(response.getBody().contains(text));
    }
}
