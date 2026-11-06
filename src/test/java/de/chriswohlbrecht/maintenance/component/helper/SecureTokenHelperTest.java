package de.chriswohlbrecht.maintenance.component.helper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenHelperTest {

    @Test
    void generatedTokensAre256BitBase64UrlWithoutPadding() {
        String first = SecureTokenHelper.generate();
        String second = SecureTokenHelper.generate();

        assertThat(first).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(second).matches("^[A-Za-z0-9_-]{43}$").isNotEqualTo(first);
    }

    @Test
    void hashIsLowerCaseHexSha256() throws Exception {
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("token".getBytes(StandardCharsets.UTF_8)));

        assertThat(SecureTokenHelper.hash("token")).isEqualTo(expected).hasSize(64).matches("^[0-9a-f]{64}$");
    }
}
