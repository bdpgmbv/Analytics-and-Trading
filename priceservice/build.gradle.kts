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
    implementation("org.springframework.boot:spring-boot-starter-webflux") // For reactive price streaming
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Database
    implementation("org.postgresql:postgresql:42.7.1")
    
    // Resilience - critical for price service to handle upstream failures
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    implementation("io.github.resilience4j:resilience4j-timelimiter:2.2.0")
    
    // Caching - important for price caching to avoid Issue #1 (prices dropping to zero)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // WebSocket for real-time price updates
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    
    // Monitoring
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Scheduling for price staleness checks
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
}

springBoot {
    mainClass.set("com.vyshali.fxanalyzer.priceservice.PriceServiceApplication")
}
