plugins {
    id("sellout.spring-service")
}

description = "Seats, holds, confirm/release — the contended core (placeholder in slice 000)"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}
