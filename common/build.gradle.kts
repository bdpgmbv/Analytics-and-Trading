plugins {
    java
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.vyshali"
version = "2.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.3")
    }
}

dependencies {
    // Spring Boot (provided - services will have these)
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-jdbc")
    compileOnly("org.springframework.boot:spring-boot-starter-data-redis")
    compileOnly("org.springframework.boot:spring-boot-starter-actuator")
    compileOnly("org.springframework.kafka:spring-kafka")
    
    // Resilience4j
    api("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    api("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    api("io.github.resilience4j:resilience4j-retry:2.2.0")
    
    // Caffeine cache
    api("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // Micrometer metrics
    api("io.micrometer:micrometer-core")
    
    // Jackson
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    // Validation
    api("jakarta.validation:jakarta.validation-api")
    
    // Logging
    api("org.slf4j:slf4j-api")
}
