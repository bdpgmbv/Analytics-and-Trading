package com.vyshali.positionloader.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Main application configuration.
 * Contains shared constants and enables configuration property binding.
 * 
 * Note: Bean definitions have been moved to dedicated config classes:
 * - CacheConfig: Caffeine cache manager
 * - KafkaConsumerConfig: Kafka consumer factories
 * - RestClientConfig: REST client beans
 */
@Configuration
@EnableConfigurationProperties(LoaderProperties.class)
public class AppConfig {
    
    // ═══════════════════════════════════════════════════════════════════════
    // KAFKA TOPICS
    // ═══════════════════════════════════════════════════════════════════════
    
    public static final String TOPIC_EOD_POSITIONS = "eod-positions";
    public static final String TOPIC_INTRADAY_POSITIONS = "intraday-positions";
    public static final String TOPIC_POSITION_EVENTS = "position-events";
    public static final String TOPIC_TRADE_FILLS = "trade-fills";
    public static final String TOPIC_HEDGE_ORDERS = "hedge-orders";
    public static final String TOPIC_DLQ = "position-loader-dlq";
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONSUMER GROUPS
    // ═══════════════════════════════════════════════════════════════════════
    
    public static final String GROUP_EOD = "position-loader-eod";
    public static final String GROUP_INTRADAY = "position-loader-intraday";
    public static final String GROUP_FILLS = "position-loader-fills";
    
    // ═══════════════════════════════════════════════════════════════════════
    // BATCH STATUSES
    // ═══════════════════════════════════════════════════════════════════════
    
    public static final String BATCH_STATUS_STAGING = "STAGING";
    public static final String BATCH_STATUS_ACTIVE = "ACTIVE";
    public static final String BATCH_STATUS_ARCHIVED = "ARCHIVED";
    public static final String BATCH_STATUS_FAILED = "FAILED";
    public static final String BATCH_STATUS_ROLLED_BACK = "ROLLED_BACK";
    
    // ═══════════════════════════════════════════════════════════════════════
    // EOD RUN STATUSES
    // ═══════════════════════════════════════════════════════════════════════
    
    public static final String EOD_STATUS_PENDING = "PENDING";
    public static final String EOD_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String EOD_STATUS_COMPLETED = "COMPLETED";
    public static final String EOD_STATUS_FAILED = "FAILED";
    public static final String EOD_STATUS_SKIPPED = "SKIPPED";
    
    // ═══════════════════════════════════════════════════════════════════════
    // POSITION SOURCES
    // ═══════════════════════════════════════════════════════════════════════
    
    public static final String SOURCE_MSPM = "MSPM";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_UPLOAD = "UPLOAD";
    public static final String SOURCE_ADJUSTMENT = "ADJUSTMENT";
    
    // ═══════════════════════════════════════════════════════════════════════
    // CACHE NAMES
    // ═══════════════════════════════════════════════════════════════════════
    
    public static final String CACHE_ACCOUNTS = "accounts";
    public static final String CACHE_PRODUCTS = "products";
    public static final String CACHE_FX_RATES = "fxRates";
    public static final String CACHE_HOLIDAYS = "holidays";
    
    // ═══════════════════════════════════════════════════════════════════════
    // METRICS
    // ═══════════════════════════════════════════════════════════════════════
    
    public static final String METRIC_POSITIONS_LOADED = "positions.loaded";
    public static final String METRIC_EOD_DURATION = "eod.duration";
    public static final String METRIC_DLQ_SIZE = "dlq.size";
    public static final String METRIC_CONSUMER_LAG = "consumer.lag";
    
    private AppConfig() {
        // Utility class - prevent instantiation
    }
}
