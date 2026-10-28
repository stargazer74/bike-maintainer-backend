package de.chriswohlbrecht.maintenance.i18n;

import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** German (base file) and English translations must offer the same keys with the same arguments. */
class MessagesConsistencyTest {

    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)}");

    private final Properties german = load("i18n/messages.properties");
    private final Properties english = load("i18n/messages_en.properties");

    @Test
    void bothLanguagesHaveTheSameKeys() {
        assertThat(english.stringPropertyNames()).isEqualTo(german.stringPropertyNames());
    }

    @Test
    void translationsUseTheSameArguments() {
        for (String key : german.stringPropertyNames()) {
            assertThat(arguments(english.getProperty(key)))
                    .as("arguments of %s", key)
                    .isEqualTo(arguments(german.getProperty(key)));
        }
    }

    @Test
    void everyVehicleTypeHasALabel() {
        Arrays.stream(VehicleType.values()).forEach(type ->
                assertThat(german).containsKey("vehicleType." + type.name()));
    }

    private static Set<String> arguments(String text) {
        Set<String> arguments = new TreeSet<>();
        if (text == null) {
            return arguments;
        }
        Matcher matcher = ARGUMENT.matcher(text);
        while (matcher.find()) {
            arguments.add(matcher.group(1));
        }
        return arguments;
    }

    private static Properties load(String resource) {
        Properties properties = new Properties();
        try (InputStream input = MessagesConsistencyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return properties;
    }
}
