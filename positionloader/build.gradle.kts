plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.vyshali.positionloader"
version = "1.0.0"

dependencies {
    implementation(project(":common"))

    // CORE SPRING
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // FIX: VALIDATION
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // FIX: KAFKA
    implementation("org.springframework.kafka:spring-kafka")

    // DB Connection
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")

    // FIX: DISTRIBUTED LOCKING
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.10.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.10.0")

    // FIX: RESILIENCE
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    // OBSERVABILITY
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    // UTILS
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // TESTING
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")

    // Fixes "Cannot resolve symbol 'io.swagger...'"
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
}