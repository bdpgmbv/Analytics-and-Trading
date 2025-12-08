plugins {
    java
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.vyshali.gateway"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

extra["springCloudVersion"] = "2023.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // 1. The Core Gateway Engine (Netty/Non-blocking)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")

    // 2. Security (OAuth2 / JWT Validation)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // 3. Rate Limiting (Redis Reactive)
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // 4. Resilience (Circuit Breakers for Gateway)
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")

    // 5. Observability (Metrics & Tracing)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    // 6. Tools
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}