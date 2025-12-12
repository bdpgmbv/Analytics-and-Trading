plugins {
    id("java")
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.vyshali"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
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
    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL MODULE
    // ═══════════════════════════════════════════════════════════════════════
    implementation(project(":common"))

    // ═══════════════════════════════════════════════════════════════════════
    // SPRING BOOT STARTERS
    // ═══════════════════════════════════════════════════════════════════════
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    
    // ✅ SPRING SECURITY (Required by SecurityConfig.java)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // ═══════════════════════════════════════════════════════════════════════
    // KAFKA
    // ═══════════════════════════════════════════════════════════════════════
    implementation("org.springframework.kafka:spring-kafka")

    // ═══════════════════════════════════════════════════════════════════════
    // DATABASE
    // ═══════════════════════════════════════════════════════════════════════
    runtimeOnly("org.postgresql:postgresql")
    
    // DB2 support (optional - comment out if not needed)
    // runtimeOnly("com.ibm.db2:jcc:11.5.9.0")
    
    // Connection pooling
    implementation("com.zaxxer:HikariCP")

    // ═══════════════════════════════════════════════════════════════════════
    // RESILIENCE & MONITORING
    // ═══════════════════════════════════════════════════════════════════════
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    implementation("io.github.resilience4j:resilience4j-bulkhead:2.2.0")
    implementation("io.github.resilience4j:resilience4j-micrometer:2.2.0")
    
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ═══════════════════════════════════════════════════════════════════════
    // JSON & SERIALIZATION
    // ═══════════════════════════════════════════════════════════════════════
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ═══════════════════════════════════════════════════════════════════════
    // API DOCUMENTATION
    // ═══════════════════════════════════════════════════════════════════════
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // ═══════════════════════════════════════════════════════════════════════
    // LOMBOK
    // ═══════════════════════════════════════════════════════════════════════
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ═══════════════════════════════════════════════════════════════════════
    // TESTING
    // ═══════════════════════════════════════════════════════════════════════
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // H2 for unit tests
    testRuntimeOnly("com.h2database:h2")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.3")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

// ═══════════════════════════════════════════════════════════════════════
// SPRING BOOT CONFIG
// ═══════════════════════════════════════════════════════════════════════
springBoot {
    mainClass.set("com.vyshali.positionloader.PositionLoaderApplication")
}

tasks.bootJar {
    archiveFileName.set("position-loader.jar")
}

tasks.jar {
    enabled = false
}
