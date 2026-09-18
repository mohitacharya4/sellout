plugins {
    id("sellout.spring-library")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springframework.boot:spring-boot-starter-micrometer-metrics")
    api("org.springframework.boot:spring-boot-starter-opentelemetry")
    api("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
}
