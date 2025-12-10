package com.vyshali.positionloader.config;

/*
 * AMPLIFIED: Rate limiting for REST API endpoints
 *
 * WHY NEEDED:
 * - OpenAPI docs mention rate limiting but none exists
 * - Prevents abuse and protects downstream systems (DB, MSPM)
 * - Required for production APIs
 *
 * IMPLEMENTATION: Token bucket algorithm with Redis
 * - Each client gets a bucket with X tokens
 * - Each request consumes 1 token
 * - Tokens refill at Y per second
 * - When empty, requests are rejected with 429
 */

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "rate-limiting.enabled", havingValue = "true")
public class RateLimitingConfig {

    @Value("${rate-limiting.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${rate-limiting.burst-capacity:10}")
    private int burstCapacity;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Rate limit filter applied to all /api/** endpoints.
     */
    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(requestsPerMinute, burstCapacity, redisHost, redisPort);
    }

    @Slf4j
    public static class RateLimitFilter extends OncePerRequestFilter {

        private final int requestsPerMinute;
        private final int burstCapacity;
        private final String redisHost;
        private final int redisPort;

        // In-memory fallback if Redis unavailable
        private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

        // Redis-based distributed rate limiting
        private ProxyManager<String> proxyManager;
        private boolean redisAvailable = false;

        public RateLimitFilter(int requestsPerMinute, int burstCapacity, String redisHost, int redisPort) {
            this.requestsPerMinute = requestsPerMinute;
            this.burstCapacity = burstCapacity;
            this.redisHost = redisHost;
            this.redisPort = redisPort;
            initializeRedis();
        }

        private void initializeRedis() {
            try {
                RedisClient client = RedisClient.create("redis://" + redisHost + ":" + redisPort);
                StatefulRedisConnection<String, byte[]> connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

                this.proxyManager = LettuceBasedProxyManager.builderFor(connection).build();
                this.redisAvailable = true;
                log.info("Rate limiting initialized with Redis");
            } catch (Exception e) {
                log.warn("Redis unavailable for rate limiting, using in-memory: {}", e.getMessage());
                this.redisAvailable = false;
            }
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

            String path = request.getRequestURI();

            // Only rate limit API endpoints
            if (!path.startsWith("/api/")) {
                filterChain.doFilter(request, response);
                return;
            }

            String clientKey = resolveClientKey(request);
            Bucket bucket = resolveBucket(clientKey);

            if (bucket.tryConsume(1)) {
                // Request allowed
                long remaining = bucket.getAvailableTokens();
                response.addHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                response.addHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
                filterChain.doFilter(request, response);
            } else {
                // Rate limit exceeded
                log.warn("Rate limit exceeded for client: {}", clientKey);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("""
                        {
                            "error": "Too Many Requests",
                            "message": "Rate limit exceeded. Please try again later.",
                            "retryAfterSeconds": 60
                        }
                        """);
            }
        }

        /**
         * Resolve client key for rate limiting.
         * Priority: API Key > User ID > IP Address
         */
        private String resolveClientKey(HttpServletRequest request) {
            // Check for API key header
            String apiKey = request.getHeader("X-API-Key");
            if (apiKey != null && !apiKey.isEmpty()) {
                return "apikey:" + apiKey;
            }

            // Check for authenticated user
            if (request.getUserPrincipal() != null) {
                return "user:" + request.getUserPrincipal().getName();
            }

            // Fall back to IP address
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
            return "ip:" + ip;
        }

        /**
         * Get or create bucket for client.
         */
        private Bucket resolveBucket(String clientKey) {
            Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder().addLimit(Bandwidth.classic(burstCapacity, Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)))).build();

            if (redisAvailable && proxyManager != null) {
                // Distributed rate limiting via Redis
                return proxyManager.builder().build("ratelimit:" + clientKey, configSupplier);
            } else {
                // Local in-memory rate limiting
                return localBuckets.computeIfAbsent(clientKey, k -> Bucket.builder().addLimit(Bandwidth.classic(burstCapacity, Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)))).build());
            }
        }
    }
}