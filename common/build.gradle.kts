plugins {
    `java-library`
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
}

description = "Shared Database Schema & Utilities"

dependencies {
    // Required for GlobalExceptionHandler (ProblemDetail, ResponseEntity)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Core Jackson Annotations (if needed for shared DTOs)
    implementation("com.fasterxml.jackson.core:jackson-annotations")
}