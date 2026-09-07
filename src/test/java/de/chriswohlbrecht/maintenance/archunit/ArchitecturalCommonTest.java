package de.chriswohlbrecht.maintenance.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Value;

import java.util.Random;
import java.util.UUID;

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
}
