package de.chriswohlbrecht.maintenance.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Value;

import java.util.Random;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.*;

@AnalyzeClasses(packages = "de.chriswohlbrecht.maintenance", importOptions = {ImportOption.DoNotIncludeTests.class, ExcludeConfig.class})
public class ArchitecturalCommonTest {

    @ArchTest
    ArchRule noClassesShouldUseJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    ArchRule noClassesShouldUseFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    ArchRule noClassesShouldAccessStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    ArchRule noJavaUtilDate =
            noClasses()
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName(java.util.Date.class.getName())
                    .because("use LocalDate instead of java.util.Date");

    @ArchTest
    ArchRule noFieldNamedUuid = noFields()
            .that().haveRawType(UUID.class)
            .should().haveName("uuid")
            .because("developers have to use the allowed name 'id' or think of a more specific name regarding their purpose");

    @ArchTest
    ArchRule valueAnnotationShouldNotBeUsed =
            noFields()
                    .should().beAnnotatedWith(Value.class)
                    .because("we want to use ConfigurationProperties instead");

    @ArchTest
    ArchRule noInsecureRandomnessSource =
            noClasses()
                    .should().dependOnClassesThat().haveFullyQualifiedName(Random.class.getCanonicalName())
                    .because("we want to use SecureRandom instead");

    @ArchTest
    ArchRule entitiesDeclareToString =
            classes()
                    .that().areAnnotatedWith(Entity.class).or().areAnnotatedWith(Embeddable.class)
                    .should(declareToString())
                    .because("Object.toString() is useless in logs and Lombok builders print related entities with it; "
                            + "use @ToString(onlyExplicitlyIncluded = true) with the id, never personal data");

    private static ArchCondition<JavaClass> declareToString() {
        return new ArchCondition<>("declare toString()") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean declared = javaClass.getMethods().stream()
                        .anyMatch(method -> method.getName().equals("toString") && method.getRawParameterTypes().isEmpty());
                if (!declared) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " does not declare toString()"));
                }
            }
        };
    }
}
