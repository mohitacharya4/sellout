pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

rootProject.name = "sellout"

include(
    "libs:platform-bom",
    "libs:test-fixtures",
    "libs:observability-starter",
    "libs:security-starter",
    "services:inventory-service",
    "services:psp-simulator",
)
