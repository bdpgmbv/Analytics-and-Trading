plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.vyshali.hedgeservice"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    // Web & DB
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database Driver & Pool
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")

    // Internal Modules (Access to Shared Schema)
    implementation(project(":common"))

    // Security
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    // Tools
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
    imports { mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.0") }
}