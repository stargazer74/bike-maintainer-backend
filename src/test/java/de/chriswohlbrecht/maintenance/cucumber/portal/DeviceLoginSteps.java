package de.chriswohlbrecht.maintenance.cucumber.portal;

import de.chriswohlbrecht.maintenance.cucumber.support.ApiClient;
import de.chriswohlbrecht.maintenance.cucumber.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/** Portal only: apps that stay logged in with a device token. */
public class DeviceLoginSteps {

    private static final String DEVICE_LOGIN = "/api/v1/auth/device-login";
    private static final String DEVICE_SESSION = "/api/v1/auth/device-session";
    private static final String DEVICES = "/api/v1/auth/devices";

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private String deviceToken;
    private String previousDeviceToken;
    private String chosenDeviceToken;
    private List<Integer> parallelStatuses;
    private ApiClient webApp;
    private ApiClient thief;
    private EntityExchangeResult<byte[]> webAppResponse;

    @Given("I log in on a device {string} as {string} with password {string}")
    public void iLogInOnADevice(String deviceName, String email, String password) {
        EntityExchangeResult<byte[]> response = apiClient.post(DEVICE_LOGIN, new JSONObject()
                .put("email", email).put("password", password).put("deviceName", deviceName).toString());
        scenarioContext.setLastResponse(response);
        if (response.getStatus().value() == 200) {
            deviceToken = body(response).getString("deviceToken");
        }
    }

    @When("I fail to log in on a device as {string} {int} times")
    public void iFailToLogInOnADevice(String email, int times) {
        for (int i = 0; i < times; i++) {
            iLogInOnADevice("Pixel 8", email, "definitely-wrong-password");
        }
    }

    /** Deletes the sessions on the server; the client keeps its (now useless) cookie, like after 8 idle hours. */
    @When("my session expires")
    public void mySessionExpires() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }

    @When("the app exchanges its device token")
    public void theAppExchangesItsDeviceToken() {
        EntityExchangeResult<byte[]> response = exchange(apiClient, deviceToken);
        scenarioContext.setLastResponse(response);
        if (response.getStatus().value() == 200) {
            previousDeviceToken = deviceToken;
            deviceToken = body(response).getString("deviceToken");
        }
    }

    /** The exchange reaches the server, but its answer never reaches the app: it only knows the token it chose. */
    @When("the app exchanges its device token choosing the next one, but the answer is lost")
    public void theAppExchangesChoosingTheNextOneButTheAnswerIsLost() {
        chosenDeviceToken = newToken();
        exchange(apiClient, deviceToken, chosenDeviceToken);
    }

    @When("the app exchanges the token it chose")
    public void theAppExchangesTheTokenItChose() {
        String next = newToken();
        EntityExchangeResult<byte[]> response = exchange(apiClient, chosenDeviceToken, next);
        scenarioContext.setLastResponse(response);
        if (response.getStatus().value() == 200) {
            assertThat(body(response).getString("deviceToken")).isEqualTo(next);
            previousDeviceToken = chosenDeviceToken;
            deviceToken = next;
        }
    }

    @When("the app exchanges its device token choosing the same token as next")
    public void theAppExchangesChoosingTheSameToken() {
        scenarioContext.setLastResponse(exchange(apiClient, deviceToken, deviceToken));
    }

    @When("the app exchanges its device token choosing its previous token as next")
    public void theAppExchangesChoosingThePreviousToken() {
        scenarioContext.setLastResponse(exchange(apiClient, deviceToken, previousDeviceToken));
    }

    @When("the app exchanges its previous device token")
    public void theAppExchangesItsPreviousDeviceToken() {
        assertThat(previousDeviceToken).as("previous device token").isNotNull();
        scenarioContext.setLastResponse(exchange(apiClient, previousDeviceToken));
    }

    @When("the app exchanges its device token twice at the same time")
    public void theAppExchangesItsDeviceTokenTwiceAtTheSameTime() {
        ApiClient first = new ApiClient(port);
        ApiClient second = new ApiClient(port);
        // Fetch the CSRF cookies up front so that both exchanges start as close together as possible.
        first.get("/api/v1/config");
        second.get("/api/v1/config");
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> awaitThenExchange(start, first));
        CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> awaitThenExchange(start, second));
        start.countDown();
        parallelStatuses = List.of(a.join(), b.join());
    }

    @Then("exactly one of the exchanges succeeded")
    public void exactlyOneOfTheExchangesSucceeded() {
        assertThat(parallelStatuses).containsExactlyInAnyOrder(200, 401);
    }

    @Then("the app received a new device token")
    public void theAppReceivedANewDeviceToken() {
        assertThat(deviceToken).isNotEqualTo(previousDeviceToken).matches("^[A-Za-z0-9_-]{43}$");
    }

    @Then("the user {string} has {int} device(s)")
    public void theUserHasDevices(String email, int count) {
        Integer devices = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_login d JOIN app_user u "
                + "ON u.id = d.user_id WHERE u.email = ?", Integer.class, email);
        assertThat(devices).as("devices of %s", email).isEqualTo(count);
    }

    @Then("the database holds no devices")
    public void theDatabaseHoldsNoDevices() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_login", Integer.class)).isZero();
    }

    @Then("the device {string} is marked as current")
    public void theDeviceIsMarkedAsCurrent(String name) {
        assertThat(deviceInLastResponse(name).getBoolean("current")).isTrue();
    }

    @Then("the device {string} is not marked as current")
    public void theDeviceIsNotMarkedAsCurrent(String name) {
        assertThat(deviceInLastResponse(name).getBoolean("current")).isFalse();
    }

    @Given("the device {string} was last used {int} minutes ago")
    public void theDeviceWasLastUsedMinutesAgo(String name, int minutes) {
        jdbcTemplate.update("UPDATE device_login SET last_used_at = ? WHERE name = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(minutes)), name);
    }

    @Given("a thief exchanges a copy of the device token")
    public void aThiefExchangesACopyOfTheDeviceToken() {
        thief = new ApiClient(port);
        assertThat(exchange(thief, deviceToken).getStatus().value()).as("thief's exchange").isEqualTo(200);
        assertThat(thief.get("/api/v1/vehicles").getStatus().value()).isEqualTo(200);
    }

    @Then("the thief is no longer logged in")
    public void theThiefIsNoLongerLoggedIn() {
        assertThat(thief.get("/api/v1/vehicles").getStatus().value()).isEqualTo(401);
    }

    @Given("the device {string} was last used {int} days ago")
    public void theDeviceWasLastUsedDaysAgo(String name, int days) {
        jdbcTemplate.update("UPDATE device_login SET last_used_at = ? WHERE name = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(days)), name);
    }

    @When("I remove the device {string}")
    public void iRemoveTheDevice(String name) {
        scenarioContext.setLastResponse(apiClient.delete(DEVICES + "/" + deviceId(name)));
    }

    @Given("the web app is logged in as {string} with password {string}")
    public void theWebAppIsLoggedIn(String email, String password) {
        webApp = new ApiClient(port);
        EntityExchangeResult<byte[]> response = webApp.post("/api/v1/auth/login",
                new JSONObject().put("email", email).put("password", password).toString());
        assertThat(response.getStatus().value()).as("login of the web app").isEqualTo(200);
    }

    @When("the web app removes the device {string}")
    public void theWebAppRemovesTheDevice(String name) {
        webAppResponse = webApp.delete(DEVICES + "/" + deviceId(name));
    }

    @Then("the web app got status {int}")
    public void theWebAppGotStatus(int status) {
        assertThat(webAppResponse.getStatus().value()).isEqualTo(status);
    }

    private Integer awaitThenExchange(CountDownLatch start, ApiClient client) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return exchange(client, deviceToken).getStatus().value();
    }

    private EntityExchangeResult<byte[]> exchange(ApiClient client, String token) {
        return client.post(DEVICE_SESSION, new JSONObject().put("deviceToken", token).toString());
    }

    private EntityExchangeResult<byte[]> exchange(ApiClient client, String token, String next) {
        return client.post(DEVICE_SESSION, new JSONObject().put("deviceToken", token).put("nextDeviceToken", next)
                .toString());
    }

    /** Like the app: 256 random bits as Base64url without padding. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Long deviceId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM device_login WHERE name = ?", Long.class, name);
    }

    private JSONObject deviceInLastResponse(String name) {
        JSONArray devices = new JSONArray(scenarioContext.getLastResponseBodyAsString());
        for (int i = 0; i < devices.length(); i++) {
            if (name.equals(devices.getJSONObject(i).getString("name"))) {
                return devices.getJSONObject(i);
            }
        }
        throw new AssertionError("Device " + name + " not in " + devices);
    }

    private static JSONObject body(EntityExchangeResult<byte[]> response) {
        return new JSONObject(new String(response.getResponseBody(), StandardCharsets.UTF_8));
    }
}
