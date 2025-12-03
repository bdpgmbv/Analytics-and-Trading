plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.vyshali.positionloader"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation(project(":common"))

    // Core
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // Security & Vault
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.cloud:spring-cloud-starter-vault-config")

    // Distributed Locking
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.10.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.10.0")

    // DB
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")
    implementation("org.liquibase:liquibase-core")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Messaging
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Observability
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Tools
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.2.1")
}

dependencyManagement {
    imports { mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.0") }
}

tasks.withType<Test> { useJUnitPlatform() }