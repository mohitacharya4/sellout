package dev.sellout.testing.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Constitution §2.1. Include from a service test with {@code @ArchTest static final ArchTests rules
 * = ArchTests.in(HexagonalArchitectureRules.class);} under {@code @AnalyzeClasses}.
 */
public final class HexagonalArchitectureRules {

  private HexagonalArchitectureRules() {}

  @ArchTest
  public static final ArchRule domainIsFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..adapters..")
          .because("domain logic must be testable without Spring or JPA");

  @ArchTest
  public static final ArchRule applicationDoesNotSeeAdaptersOrTransport =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..adapters..", "org.springframework.web..", "org.springframework.data..")
          .because("use cases depend on ports, never on adapters or transport");

  @ArchTest
  public static final ArchRule layersPointInward =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy("..domain..")
          .layer("Application")
          .definedBy("..application..")
          // Optional: services adopting this rule set may not have adapter classes yet
          // (e.g. inventory-service in slice 000), and an empty layer must not fail the rule.
          .optionalLayer("Adapters")
          .definedBy("..adapters..")
          .whereLayer("Adapters")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Adapters")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Adapters");
}
