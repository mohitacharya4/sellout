plugins {
    id("sellout.java-library")
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

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
