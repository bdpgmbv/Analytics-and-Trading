plugins {
    java
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
}
group = "com.vyshali.gateway"
java { sourceCompatibility = JavaVersion.VERSION_21 }
extra["springCloudVersion"] = "2023.0.0"

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server") // Validate Tokens
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive") // <--- NEW
}

dependencyManagement {
    imports { mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}") }
}