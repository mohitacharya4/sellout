plugins {
    id("sellout.java-library")
}

dependencies {
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor(platform(project(":libs:platform-bom")))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
