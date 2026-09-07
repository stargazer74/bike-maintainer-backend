package de.chriswohlbrecht.maintenance.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "de.chriswohlbrecht.maintenance", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitecturalControllerTest {
    @ArchTest
    ArchRule noPersistenceInController = noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..persistence..")
            .because("controllers must use business components instead of the persistence layer");
}
