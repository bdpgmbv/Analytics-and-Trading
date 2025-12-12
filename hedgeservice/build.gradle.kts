plugins {
    java
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
}

dependencies {
    // Internal modules
    implementation(project(":common"))
    
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux") // For async operations
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // Database
    implementation("org.postgresql:postgresql:42.7.1")
    
    // Resilience
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.2.0")
    implementation("io.github.resilience4j:resilience4j-bulkhead:2.2.0")
    
    // Caching - for position caching (addresses Issue #8)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // WebSocket for real-time updates (addresses Issue #9 - refresh-only)
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    
    // Kafka for sending trade requests to FX Matrix
    implementation("org.springframework.kafka:spring-kafka")
    
    // Monitoring & Metrics (addresses Issue #11 - scattered timing code)
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // API Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    
    // JSON processing
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")
    testImplementation("io.rest-assured:rest-assured:5.4.0")
}

springBoot {
    mainClass.set("com.vyshali.fxanalyzer.hedgeservice.HedgeServiceApplication")
}
