package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.DeviceLogin;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Real SQL against H2 (MariaDB mode) with only the JPA slice. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DeviceLoginRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);

    @Autowired
    private DeviceLoginRepository deviceLoginRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EntityManager entityManager;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = appUserRepository.save(AppUser.builder().email("rider@example.org").role(UserRole.USER).build());
    }

    @Test
    void rotateSwapsHashesOnlyForTheCurrentHash() {
        DeviceLogin device = deviceLoginRepository.save(device("Pixel 8", HASH_A, NOW.minusDays(1)));

        assertThat(deviceLoginRepository.rotate(device.getId(), HASH_A, HASH_B, NOW)).isEqualTo(1);
        assertThat(deviceLoginRepository.rotate(device.getId(), HASH_A, HASH_C, NOW)).isZero();

        DeviceLogin reloaded = deviceLoginRepository.findByTokenHash(HASH_B).orElseThrow();
        assertThat(reloaded.getPreviousTokenHash()).isEqualTo(HASH_A);
        assertThat(reloaded.getLastUsedAt()).isEqualTo(NOW);
        assertThat(deviceLoginRepository.findByPreviousTokenHash(HASH_A)).isPresent();
        assertThat(deviceLoginRepository.findByTokenHash(HASH_C)).isEmpty();
    }

    @Test
    void devicesGoWithTheirUser() {
        deviceLoginRepository.save(device("Pixel 8", HASH_A, NOW));
        entityManager.flush();
        entityManager.clear();

        // The database removes the devices (ON DELETE CASCADE); the entity has no relation back to them.
        appUserRepository.deleteById(user.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(deviceLoginRepository.count()).isZero();
    }

    @Test
    void deleteUnusedSinceRemovesOnlyOlderDevices() {
        deviceLoginRepository.save(device("old", HASH_A, NOW.minusDays(91)));
        deviceLoginRepository.save(device("recent", HASH_B, NOW.minusDays(89)));

        assertThat(deviceLoginRepository.deleteUnusedSince(NOW.minusDays(90))).isEqualTo(1);

        assertThat(deviceLoginRepository.findAll()).extracting(DeviceLogin::getName).containsExactly("recent");
    }

    @Test
    void devicesOfAUserAreListedMostRecentlyUsedFirst() {
        AppUser other = appUserRepository.save(AppUser.builder().email("other@example.org").role(UserRole.USER).build());
        deviceLoginRepository.save(device("tablet", HASH_A, NOW.minusDays(3)));
        deviceLoginRepository.save(device("phone", HASH_B, NOW));
        DeviceLogin foreign = device("foreign", HASH_C, NOW);
        foreign.setUser(other);
        deviceLoginRepository.save(foreign);

        List<DeviceLogin> devices = deviceLoginRepository.findAllByUser_IdOrderByLastUsedAtDesc(user.getId());

        assertThat(devices).extracting(DeviceLogin::getName).containsExactly("phone", "tablet");
        assertThat(deviceLoginRepository.findByIdAndUser_Id(foreign.getId(), user.getId())).isEmpty();
    }

    @Test
    void deleteAllByUserLeavesOtherUsersAlone() {
        AppUser other = appUserRepository.save(AppUser.builder().email("other@example.org").role(UserRole.USER).build());
        deviceLoginRepository.save(device("phone", HASH_A, NOW));
        DeviceLogin foreign = device("foreign", HASH_B, NOW);
        foreign.setUser(other);
        deviceLoginRepository.save(foreign);

        assertThat(deviceLoginRepository.deleteAllByUser_Id(user.getId())).isEqualTo(1);

        assertThat(deviceLoginRepository.findAll()).extracting(DeviceLogin::getName).containsExactly("foreign");
    }

    @Test
    void nameWithEmojiRoundTrips() {
        DeviceLogin saved = deviceLoginRepository.save(device("Pixel 8 🏍", HASH_A, NOW));
        entityManager.flush();
        entityManager.clear();

        assertThat(deviceLoginRepository.findById(saved.getId()).orElseThrow().getName()).isEqualTo("Pixel 8 🏍");
    }

    private DeviceLogin device(String name, String hash, LocalDateTime lastUsedAt) {
        DeviceLogin device = new DeviceLogin();
        device.setUser(user);
        device.setName(name);
        device.setTokenHash(hash);
        device.setCreatedAt(lastUsedAt);
        device.setLastUsedAt(lastUsedAt);
        return device;
    }
}
