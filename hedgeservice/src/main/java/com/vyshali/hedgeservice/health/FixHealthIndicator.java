package com.vyshali.hedgeservice.health;

import com.vyshali.hedgeservice.fix.FixEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for FIX session connectivity.
 * Critical for hedge execution availability.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixHealthIndicator implements HealthIndicator {

    private final FixEngine fixEngine;

    @Override
    public Health health() {
        try {
            boolean connected = fixEngine.isConnected();
            int pending = fixEngine.getPendingOrderCount();
            
            if (connected) {
                return Health.up()
                        .withDetail("status", "connected")
                        .withDetail("pendingOrders", pending)
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "disconnected")
                        .withDetail("pendingOrders", pending)
                        .build();
            }
        } catch (Exception e) {
            log.error("FIX health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
