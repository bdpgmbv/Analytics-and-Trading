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
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Kafka - for publishing position messages like MSPM
    implementation("org.springframework.kafka:spring-kafka")
    
    // Database - for storing generated test data
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.h2database:h2:2.2.224") // Can run standalone with H2
    
    // Data generation
    implementation("com.github.javafaker:javafaker:1.0.2") {
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    implementation("org.yaml:snakeyaml:2.2")
    
    // Scheduling for periodic data generation
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    
    // JSON processing
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}

springBoot {
    mainClass.set("com.vyshali.fxanalyzer.mockupstream.MockUpstreamApplication")
}

// Profile configuration for different mock scenarios
tasks.bootRun {
    systemProperty("spring.profiles.active", project.findProperty("profile") ?: "local")
}
