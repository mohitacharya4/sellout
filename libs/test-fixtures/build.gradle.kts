plugins {
    id("sellout.java-library")
}

dependencies {
    api(project(":libs:security-starter"))
    api("org.springframework.boot:spring-boot-starter-test")
    api("org.springframework.boot:spring-boot-starter-security-test")
    api("org.springframework.boot:spring-boot-testcontainers")
    api("org.awaitility:awaitility")
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.keycloak)
    api(libs.archunit)
}

tasks.processResources {
    from(rootProject.file("ops/keycloak")) { into("keycloak") }
}
