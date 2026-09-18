package dev.sellout.inventory;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import dev.sellout.testing.architecture.HexagonalArchitectureRules;

@AnalyzeClasses(
    packages = "dev.sellout.inventory",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest static final ArchTests hexagonal = ArchTests.in(HexagonalArchitectureRules.class);
}
