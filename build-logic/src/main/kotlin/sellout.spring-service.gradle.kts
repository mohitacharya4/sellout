plugins {
    id("sellout.java-library")
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation(project(":libs:observability-starter"))
    implementation(project(":libs:security-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Not pulled in transitively by webmvc/security/validation; use cases need
    // @Transactional (org.springframework.transaction.annotation) without a persistence starter.
    implementation("org.springframework:spring-tx")
    annotationProcessor(platform(project(":libs:platform-bom")))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation(project(":libs:test-fixtures"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}

springBoot {
    buildInfo()
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { include("**/domain/**", "**/application/**") }
            },
        ),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

jib {
    from {
        image = providers.gradleProperty("sellout.baseImage").get()
    }
    to {
        image = "sellout/${project.name}"
        tags = setOf("local")
    }
    container {
        user = "65532:65532"
        ports = listOf("8080")
        environment = mapOf("SERVER_PORT" to "8080")
        jvmFlags = listOf(
            "-XX:MaxRAMPercentage=75",
            "-XX:+ExitOnOutOfMemoryError",
            "-Djava.security.egd=file:/dev/./urandom",
        )
    }
}

// Jib 3.5 reads Task.project at execution time, which the configuration cache forbids.
// Opting out per task keeps the cache for every build that does not run Jib.
tasks.matching { it.name.startsWith("jib") }.configureEach {
    notCompatibleWithConfigurationCache("Jib 3.5 reads Task.project at execution time")
}
