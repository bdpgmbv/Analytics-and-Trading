plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    jacoco  // Code coverage
}

group = "com.vyshali.positionloader"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":common"))

    // ==================== CORE SPRING ====================
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ==================== CACHING (NEW) ====================
    // Caffeine: High-performance local cache
    // - 10x faster than Redis for local lookups
    // - Perfect for reference data (Clients, Funds, Products)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // ==================== SECURITY ====================
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // ==================== KAFKA ====================
    implementation("org.springframework.kafka:spring-kafka")

    // ==================== DATABASE ====================
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")

    // ==================== RESILIENCE ====================
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    // ==================== OBSERVABILITY ====================
    implementation("io.micrometer:micrometer-registry-prometheus")
    // Distributed tracing (required for trace propagation)
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    // ==================== API DOCUMENTATION ====================
    // OpenAPI/Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // ==================== LOMBOK ====================
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ==================== TESTING ====================
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // Better assertions
    testImplementation("org.assertj:assertj-core:3.25.3")

    // Testcontainers for real DB/Kafka testing
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.testcontainers:kafka:1.19.7")

    // Contract testing
    testImplementation("au.com.dius.pact.consumer:junit5:4.6.7")

    // Architecture testing
    testImplementation("com.tngtech.archunit:archunit-junit5:1.2.1")

    // Awaitility for async testing
    testImplementation("org.awaitility:awaitility:4.2.0")
}

// ==================== JACOCO COVERAGE ====================
jacoco {
    toolVersion = "0.8.11"
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                // PRODUCTION: Require 80% line coverage
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            excludes = listOf(
                "*.dto.*", "*.config.*", "*Application"
            )
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

// ==================== INTEGRATION TESTS ====================
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
}

tasks.check {
    dependsOn(tasks.named("integrationTest"))
}