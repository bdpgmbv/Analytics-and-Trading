package com.vyshali.common.exception;

/**
 * Exception thrown when a requested entity is not found
 */
public class EntityNotFoundException extends FxAnalyzerException {

    private final String entityType;
    private final String identifier;
    
    public EntityNotFoundException(String entityType, String identifier) {
        super("FXAN-1001", String.format("%s not found with identifier: %s", entityType, identifier));
        this.entityType = entityType;
        this.identifier = identifier;
    }
    
    public EntityNotFoundException(String entityType, Long id) {
        this(entityType, String.valueOf(id));
    }
    
    public String getEntityType() {
        return entityType;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    // Factory methods for common entities
    public static EntityNotFoundException client(String clientCode) {
        return new EntityNotFoundException("Client", clientCode);
    }
    
    public static EntityNotFoundException fund(String fundCode) {
        return new EntityNotFoundException("Fund", fundCode);
    }
    
    public static EntityNotFoundException account(String accountNumber) {
        return new EntityNotFoundException("Account", accountNumber);
    }
    
    public static EntityNotFoundException product(String identifier) {
        return new EntityNotFoundException("Product", identifier);
    }
    
    public static EntityNotFoundException position(Long positionId) {
        return new EntityNotFoundException("Position", positionId);
    }
    
    public static EntityNotFoundException snapshot(Long snapshotId) {
        return new EntityNotFoundException("Snapshot", snapshotId);
    }
}
