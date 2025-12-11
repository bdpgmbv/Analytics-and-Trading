package com.vyshali.positionloader.exception;

/**
 * Exception thrown when a duplicate position snapshot is detected.
 * Part of Phase 4 Enhancement #19 - Duplicate Detection.
 */
public class DuplicateSnapshotException extends RuntimeException {
    
    private final Integer accountId;
    private final String contentHash;
    
    public DuplicateSnapshotException(String message) {
        super(message);
        this.accountId = null;
        this.contentHash = null;
    }
    
    public DuplicateSnapshotException(String message, Integer accountId) {
        super(message);
        this.accountId = accountId;
        this.contentHash = null;
    }
    
    public DuplicateSnapshotException(String message, Integer accountId, String contentHash) {
        super(message);
        this.accountId = accountId;
        this.contentHash = contentHash;
    }
    
    public Integer getAccountId() {
        return accountId;
    }
    
    public String getContentHash() {
        return contentHash;
    }
}
