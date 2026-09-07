package de.chriswohlbrecht.maintenance.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;


@AnalyzeClasses(
        packages = "de.chriswohlbrecht.maintenance",
        importOptions = {
                ImportOption.DoNotIncludeTests.class,
                ImportOption.DoNotIncludeJars.class,
                ImportOption.DoNotIncludeArchives.class
        }
)
final class ValidationArchTest {
    @ArchTest
    public static ArchRule noCustomValidators =
            classes()
                    .should().notImplement(ConstraintValidator.class)
                    .because("complex validation should be performed explicitly");

    @ArchTest
    public static ArchRule noCustomValidationAnnotations =
            classes()
                    .should().notBeAnnotatedWith(Constraint.class)
                    .because("complex validation should be performed explicitly");

    @ArchTest
    public static ArchRule allConfigurationPropertiesShouldBeValidated =
            classes()
                    .that().areAnnotatedWith(ConfigurationProperties.class)
                    .should().beAnnotatedWith(Validated.class)
                    .because("validation of configuration properties ensures that many misconfiguration can be caught on startup");

    @ArchTest
    public static ArchRule nestedConfigurationPropertiesShouldBeValidated =
            fields()
                    .that().areAnnotatedWith(NestedConfigurationProperty.class)
                    .should().beAnnotatedWith(Valid.class)
                    .because("this annotation is necessary to trigger downstream validation");
}
