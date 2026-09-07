package de.chriswohlbrecht.maintenance.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "de.chriswohlbrecht.maintenance", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitecturalLayerTest {

    @ArchTest
    static final ArchRule layerDependencyRule = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Component").definedBy("..component..")

            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Component")
            .whereLayer("Component").mayOnlyBeAccessedByLayers("Service", "Controller")

            .allowEmptyShould(true)
            .because("Layers must respect a defined dependency hierarchy");

    @ArchTest
    static final ArchRule serviceClassesShouldBeAnnotated =
            classes().that().resideInAPackage("..service..")
                    .and().areNotInterfaces()
                    .and().areNotAnonymousClasses()
                    .should().beAnnotatedWith(Service.class)
                    .because("Service classes in layer service should be annotated with @Service");

    @ArchTest
    static final ArchRule componentClassesShouldBeAnnotated =
            classes()
                    .that().resideInAPackage("..component..")
                    .and().areNotInterfaces()
                    .and().areNotAnonymousClasses()
                    .and().resideOutsideOfPackage("..component.model..")
                    .and().resideOutsideOfPackage("..component.helper..")
                    .should().beAnnotatedWith(Component.class)
                    .because("Component classes in layer component should be annotated with @Component");

    @ArchTest
    static final ArchRule noServiceAnnotationInComponent =
            classes()
                    .that().resideInAPackage("..component..")
                    .should().notBeAnnotatedWith(Service.class)
                    .because("@Service is not allowed in component layer");

    @ArchTest
    static final ArchRule noComponentAnnotationInService =
            classes()
                    .that().resideInAPackage("..service..")
                    .should().notBeAnnotatedWith(Component.class)
                    .because("@Component is not allowed in service layer");

}
