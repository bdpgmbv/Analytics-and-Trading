plugins {
    java
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
}

// Don't create bootJar for library module
tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    
    // Database
    implementation("org.postgresql:postgresql:42.7.1")
    
    // Utilities
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("com.google.guava:guava:32.1.3-jre")
    
    // MapStruct for DTO mapping
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
    
    // Lombok (already in root, but need processor order)
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2:2.2.224")
}
