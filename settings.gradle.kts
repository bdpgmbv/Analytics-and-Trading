rootProject.name = "fxanalyzer"

// Include all modules
include(
    "common",
    "database",
    "positionloader",
    "priceservice",
    "tradefillprocessor",
    "hedgeservice",
    "mock-upstream"
)

// Configure plugin management
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // Spring Boot
            version("springBoot", "3.2.1")
            version("springDependencyManagement", "1.1.4")
            
            // Database
            version("postgresql", "42.7.1")
            version("liquibase", "4.25.1")
            version("hikari", "5.1.0")
            
            // Kafka
            version("kafka", "3.6.1")
            
            // Utilities
            version("lombok", "1.18.30")
            version("mapstruct", "1.5.5.Final")
            version("guava", "32.1.3-jre")
            
            // Testing
            version("testcontainers", "1.19.3")
            version("junit", "5.10.1")
            
            // Monitoring
            version("micrometer", "1.12.1")
        }
    }
}
include("database")
