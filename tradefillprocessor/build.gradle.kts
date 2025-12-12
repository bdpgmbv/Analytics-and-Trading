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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Kafka - core for receiving trade fills from FX Matrix
    implementation("org.springframework.kafka:spring-kafka")
    
    // Database
    implementation("org.postgresql:postgresql:42.7.1")
    
    // Resilience - for handling downstream failures when sending to custody/fx cash/TM
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    implementation("io.github.resilience4j:resilience4j-bulkhead:2.2.0")
    
    // Caching
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    
    // Monitoring - critical for tracking trade execution status (Issue #2)
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // JSON processing
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")
    testImplementation("org.awaitility:awaitility:4.2.0")
}

springBoot {
    mainClass.set("com.vyshali.fxanalyzer.tradefillprocessor.TradeFillProcessorApplication")
}
