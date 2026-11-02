package de.chriswohlbrecht.maintenance.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Every Spring test must activate the profile {@code test}. Without it the test would start with the production
 * configuration and connect to the MariaDB on localhost (possibly the developer's database) instead of H2.
 */
@AnalyzeClasses(packages = "de.chriswohlbrecht.maintenance", importOptions = ImportOption.OnlyIncludeTests.class)
public class SpringTestProfileTest {

    private static final String TEST_PROFILE = "test";

    @ArchTest
    ArchRule springTestsActivateTestProfile = classes()
            .that().areAnnotatedWith(SpringBootTest.class)
            .should(activateTestProfile())
            .allowEmptyShould(false)
            .because("Spring tests must run against H2 with application-test.properties, never against a real database");

    private static ArchCondition<JavaClass> activateTestProfile() {
        return new ArchCondition<>("be annotated with @ActiveProfiles including \"" + TEST_PROFILE + "\"") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Optional<ActiveProfiles> activeProfiles = javaClass.tryGetAnnotationOfType(ActiveProfiles.class);
                boolean satisfied = activeProfiles
                        .map(annotation -> Arrays.asList(annotation.profiles()).contains(TEST_PROFILE)
                                || Arrays.asList(annotation.value()).contains(TEST_PROFILE))
                        .orElse(false);
                events.add(new SimpleConditionEvent(javaClass, satisfied,
                        javaClass.getName() + (satisfied ? " activates" : " does not activate")
                                + " the profile " + TEST_PROFILE));
            }
        };
    }
}
