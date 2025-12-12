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
    
    // Kafka for message consumption
    implementation("org.springframework.kafka:spring-kafka")
    
    // Database
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("org.liquibase:liquibase-core:4.25.1")
    
    // Resilience
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.2.0")
    
    // Caching
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    
    // Monitoring
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Scheduling
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")
}

springBoot {
    mainClass.set("com.vyshali.fxanalyzer.positionloader.PositionLoaderApplication")
}
