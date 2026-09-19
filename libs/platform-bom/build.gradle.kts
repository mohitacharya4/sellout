plugins {
    `java-platform`
}

group = "dev.sellout"
version = "0.1.0-SNAPSHOT"

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(libs.spring.boot.bom))
    constraints {
        api(libs.testcontainers.core)
        api(libs.testcontainers.postgresql)
        api(libs.testcontainers.kafka)
        api(libs.testcontainers.keycloak)
        api(libs.archunit)
        api(libs.jqwik)
        api(libs.tomcat.embed.core)
        api(libs.tomcat.embed.el)
        api(libs.tomcat.embed.websocket)
    }
}
