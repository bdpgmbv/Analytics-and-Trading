/*
 * Position Loader - Build Configuration
 * 12/10/2025 - Complete with all performance and testing dependencies
 * Author: Vyshali Prabananth Lal
 */

plugins {
    java
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    jacoco
}

group = "com.vyshali"
version = "2.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // ==================== SPRING BOOT STARTERS ====================
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // ==================== CACHING ====================
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // ==================== REDIS ====================
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // ==================== KAFKA ====================
    implementation("org.springframework.kafka:spring-kafka")

    // ==================== WEBFLUX (for async REST client) ====================
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // ==================== RESILIENCE4J ====================
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.1.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.1.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.1.0")
    implementation("io.github.resilience4j:resilience4j-bulkhead:2.1.0")

    // ==================== DISTRIBUTED TRACING ====================
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")
    implementation("io.micrometer:micrometer-observation")

    // ==================== METRICS ====================
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ==================== DATABASE ====================
    runtimeOnly("org.postgresql:postgresql")

    // ==================== LOMBOK ====================
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ==================== JSON ====================
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ==================== API DOCS (Optional) ====================
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // ==================== TEST DEPENDENCIES ====================
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")

    // Async testing
    testImplementation("org.awaitility:awaitility:4.2.0")

    // AssertJ
    testImplementation("org.assertj:assertj-core:3.24.2")

    // Mockito
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Enable parallel test execution
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")

    // Increase heap for integration tests
    maxHeapSize = "1g"

    // Test logging
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// ==================== JACOCO CODE COVERAGE ====================
jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    // Exclude generated code and configs
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/config/**",
                    "**/dto/**",
                    "**/exception/**",
                    "**/*Application*"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal() // 80% coverage target
            }
        }

        rule {
            element = "CLASS"
            includes = listOf("com.vyshali.positionloader.service.*")

            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal() // 85% for services
            }
        }
    }
}

// Run coverage check after tests
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ==================== CUSTOM TASKS ====================

// Integration test task (runs only *IntegrationTest classes)
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests with Testcontainers"
    group = "verification"

    useJUnitPlatform {
        includeTags("integration")
    }

    // Needs Docker
    systemProperty("testcontainers.reuse.enable", "true")

    shouldRunAfter(tasks.test)
}

// Unit test task (excludes integration tests)
tasks.register<Test>("unitTest") {
    description = "Runs unit tests only (no Testcontainers)"
    group = "verification"

    useJUnitPlatform {
        excludeTags("integration")
    }
}

// ==================== BOOT JAR CONFIGURATION ====================
tasks.bootJar {
    archiveFileName.set("positionloader.jar")

    manifest {
        attributes(
            "Implementation-Title" to "Position Loader",
            "Implementation-Version" to version,
            "Built-By" to "Vyshali Prabananth Lal"
        )
    }
}

// ==================== SPRING BOOT CONFIGURATION ====================
springBoot {
    buildInfo()
}