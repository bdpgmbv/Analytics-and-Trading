// ═══════════════════════════════════════════════════════════════════════════
// ADD THESE DEPENDENCIES TO YOUR BUILD FILES
// ═══════════════════════════════════════════════════════════════════════════

// --- COMMON MODULE (common/build.gradle.kts) ---
// Add to dependencies block:
dependencies {
    // Resilience4j for circuit breakers
    api("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    api("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    
    // Caffeine for local caching fallback
    api("com.github.ben-manes.caffeine:caffeine")
    
    // Micrometer for metrics (already may be present)
    api("io.micrometer:micrometer-core")
}


// --- PRICE SERVICE (priceservice/build.gradle.kts) ---
// Already has caffeine, just ensure resilience4j:
dependencies {
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
}


// --- HEDGE SERVICE (hedgeservice/build.gradle.kts) ---
// Add retry support:
dependencies {
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
}


// --- TRADE FILL PROCESSOR (tradefillprocessor/build.gradle.kts) ---
// Add metrics timed annotation support:
dependencies {
    implementation("io.micrometer:micrometer-core")
    implementation("org.springframework.boot:spring-boot-starter-aop") // For @Timed
}
