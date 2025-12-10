package com.vyshali.positionloader.service;

/*
 * AMPLIFIED: Sharding support for horizontal scaling
 *
 * WHY NEEDED:
 * - Multiple Position Loader instances may run in Kubernetes
 * - Each instance should only process its assigned accounts
 * - Prevents duplicate processing and enables horizontal scaling
 *
 * HOW IT WORKS:
 * - Each pod gets SHARD_INDEX (0, 1, 2...) and TOTAL_SHARDS (e.g., 3)
 * - Account assigned to shard: accountId % TOTAL_SHARDS
 * - Pod only processes accounts where: accountId % TOTAL_SHARDS == SHARD_INDEX
 *
 * EXAMPLE with 3 pods:
 * - Pod 0 (SHARD_INDEX=0): accounts 1000, 1003, 1006...
 * - Pod 1 (SHARD_INDEX=1): accounts 1001, 1004, 1007...
 * - Pod 2 (SHARD_INDEX=2): accounts 1002, 1005, 1008...
 */

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShardingService {

    @Value("${sharding.enabled:false}")
    private boolean shardingEnabled;

    @Value("${sharding.shard-index:0}")
    private int shardIndex;

    @Value("${sharding.total-shards:1}")
    private int totalShards;

    @PostConstruct
    public void init() {
        if (shardingEnabled) {
            log.info("Sharding ENABLED: this instance is shard {}/{}", shardIndex, totalShards);

            if (shardIndex >= totalShards) {
                throw new IllegalStateException("Invalid shard config: shardIndex=" + shardIndex + " >= totalShards=" + totalShards);
            }
        } else {
            log.info("Sharding DISABLED: this instance processes ALL accounts");
        }
    }

    /**
     * Check if this instance should process the given account.
     *
     * @param accountId Account to check
     * @return true if this shard owns the account
     */
    public boolean isMyAccount(Integer accountId) {
        if (!shardingEnabled || totalShards <= 1) {
            return true;  // Single instance mode - process everything
        }

        int assignedShard = Math.abs(accountId % totalShards);
        return assignedShard == shardIndex;
    }

    /**
     * Filter a list of accounts to only those owned by this shard.
     *
     * @param accountIds All account IDs
     * @return Only accounts this shard should process
     */
    public List<Integer> filterMyAccounts(List<Integer> accountIds) {
        if (!shardingEnabled || totalShards <= 1) {
            return accountIds;
        }

        List<Integer> myAccounts = accountIds.stream().filter(this::isMyAccount).collect(Collectors.toList());

        log.debug("Shard {} filtering: {} total -> {} mine", shardIndex, accountIds.size(), myAccounts.size());

        return myAccounts;
    }

    /**
     * Get the shard that owns a specific account.
     * Useful for routing or debugging.
     */
    public int getShardForAccount(Integer accountId) {
        if (!shardingEnabled || totalShards <= 1) {
            return 0;
        }
        return Math.abs(accountId % totalShards);
    }

    // Getters for monitoring/debugging
    public boolean isShardingEnabled() {
        return shardingEnabled;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    public int getTotalShards() {
        return totalShards;
    }
}