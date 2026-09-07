package de.chriswohlbrecht.maintenance.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "de.chriswohlbrecht.maintenance", importOptions = {ImportOption.DoNotIncludeTests.class, ExcludeConfig.class})
public class ArchitecturalNamingTest {
    @ArchTest
    ArchRule controllersShouldBeSuffixed = classes().that().areAnnotatedWith(RestController.class)
            .should().haveSimpleNameEndingWith("Controller")
            .andShould().resideInAPackage("..controller..")
            .because("better recognition of controller classes");

    @ArchTest
    ArchRule componentShouldBeSuffixed = classes().that().areAnnotatedWith(Component.class)
            .should().haveSimpleNameEndingWith("Component")
            .orShould().haveSimpleNameEndingWith("Impl")
            .orShould().haveSimpleNameEndingWith("Handler")
            .orShould().haveSimpleNameEndingWith("Helper")
            .because("better recognition of component classes");

    @ArchTest
    ArchRule serviceShouldBeSuffixed = classes().that().areAnnotatedWith(Service.class)
            .should().haveSimpleNameEndingWith("Service")
            .orShould().haveSimpleNameEndingWith("TaskHandler")
            .orShould().haveSimpleNameEndingWith("ServiceImpl")
            .because("better recognition of service and helper classes");

    @ArchTest
    ArchRule mapperShouldBeSuffixed = classes().that().areInterfaces().and().areAnnotatedWith(Mapper.class)
            .should().haveSimpleNameEndingWith("Mapper")
            .because("better recognition of mapper interfaces")
            // TODO later: remove allowEmptyShould when we actually have mappers
            .allowEmptyShould(true);
}
