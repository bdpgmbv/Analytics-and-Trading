plugins {
    `java-library`
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("net.logstash.logback:logstash-logback-encoder:7.4")

    // BLOCKER FIX: Database Migration Engine
    // Without this, your .sql files in /resources/db/changelog are ignored.
    api("org.liquibase:liquibase-core")
}