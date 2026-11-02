package de.chriswohlbrecht.maintenance.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Rules for tests that start Spring.
 *
 * <ul>
 *   <li>The whole application context ({@code @SpringBootTest}) is reserved for the Cucumber suites and the few
 *   tests that need everything together; unit tests use plain JUnit/Mockito, database tests the JPA slice
 *   ({@code @DataJpaTest}), configuration tests an {@code ApplicationContextRunner}.</li>
 *   <li>Every Spring test activates the profile {@code test}. Without it the test would start with the production
 *   configuration and connect to the MariaDB on localhost (possibly the developer's database) instead of H2.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "de.chriswohlbrecht.maintenance", importOptions = ImportOption.OnlyIncludeTests.class)
public class SpringTestRulesTest {

    private static final String TEST_PROFILE = "test";

    /** Swagger UI needs MVC, security, springdoc and the profile together; see the class comment there. */
    private static final String SWAGGER_CONFIGURATION_TEST =
            "de.chriswohlbrecht.maintenance.configuration.SwaggerConfigurationTest";

    @ArchTest
    ArchRule wholeContextOnlyWhereNecessary = classes()
            .that().areAnnotatedWith(SpringBootTest.class)
            .should(beCucumberOrSwaggerTest())
            .allowEmptyShould(false)
            .because("loading the whole application context for a unit test is slow; use JUnit/Mockito, "
                    + "@DataJpaTest or an ApplicationContextRunner instead");

    @ArchTest
    ArchRule springTestsActivateTestProfile = classes()
            .that().areAnnotatedWith(SpringBootTest.class).or().areAnnotatedWith(DataJpaTest.class)
            .should(activateTestProfile())
            .allowEmptyShould(false)
            .because("Spring tests must run against H2 with application-test.properties, never against a real database");

    private static ArchCondition<JavaClass> beCucumberOrSwaggerTest() {
        return new ArchCondition<>("be a Cucumber configuration or the Swagger configuration test") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean cucumber = javaClass.getPackageName().contains(".cucumber");
                boolean swagger = javaClass.getEnclosingClass()
                        .map(enclosing -> enclosing.getName().equals(SWAGGER_CONFIGURATION_TEST))
                        .orElse(false);
                boolean satisfied = cucumber || swagger;
                events.add(new SimpleConditionEvent(javaClass, satisfied, javaClass.getName()
                        + (satisfied ? " may" : " must not") + " load the whole application context"));
            }
        };
    }

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
