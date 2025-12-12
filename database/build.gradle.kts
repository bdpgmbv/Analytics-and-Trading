plugins {
    java
    id("org.liquibase.gradle") version "2.2.1"
}

dependencies {
    // Liquibase
    implementation("org.liquibase:liquibase-core:4.25.1")
    
    // PostgreSQL driver
    implementation("org.postgresql:postgresql:42.7.1")
    
    // Liquibase Gradle plugin runtime
    liquibaseRuntime("org.liquibase:liquibase-core:4.25.1")
    liquibaseRuntime("org.postgresql:postgresql:42.7.1")
    liquibaseRuntime("info.picocli:picocli:4.7.5")
    liquibaseRuntime("org.yaml:snakeyaml:2.2")
}

// Liquibase configuration
liquibase {
    activities.register("main") {
        this.arguments = mapOf(
            "changeLogFile" to "src/main/resources/db/changelog/db.changelog-master.yaml",
            "url" to (project.findProperty("dbUrl") ?: "jdbc:postgresql://localhost:5432/fxanalyzer"),
            "username" to (project.findProperty("dbUser") ?: "postgres"),
            "password" to (project.findProperty("dbPassword") ?: "postgres"),
            "driver" to "org.postgresql.Driver"
        )
    }
    runList = "main"
}

// Custom tasks for common operations
tasks.register("dbUpdate") {
    dependsOn("update")
    group = "liquibase"
    description = "Apply all pending database changes"
}

tasks.register("dbRollback") {
    dependsOn("rollbackCount")
    group = "liquibase"
    description = "Rollback the last changeset"
}

tasks.register("dbStatus") {
    dependsOn("status")
    group = "liquibase"
    description = "Show pending changesets"
}

tasks.register("dbValidate") {
    dependsOn("validate")
    group = "liquibase"
    description = "Validate changelog"
}
