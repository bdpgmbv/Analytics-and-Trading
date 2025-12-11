plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.vyshali.tradefillprocessor"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    // CRITICAL: Shared library with schema definitions
    implementation(project(":common"))

    // Web & Data
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Messaging
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")

    // Observability
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Tools
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
}

dependencyManagement {
    imports { mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.0") }
}

tasks.test {
    useJUnitPlatform()
}
