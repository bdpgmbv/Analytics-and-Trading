package com.vyshali.positionloader.service;

import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.dto.UploadResult;
import com.vyshali.positionloader.repository.BatchRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for handling position file uploads.
 */
@Service
public class PositionUploadService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionUploadService.class);
    
    private final PositionRepository positionRepository;
    private final BatchRepository batchRepository;
    private final PositionValidationService validationService;
    private final LoaderConfig config;
    private final Timer uploadTimer;
    private final Counter uploadsSuccess;
    private final Counter uploadsFailed;
    
    public PositionUploadService(
            PositionRepository positionRepository,
            BatchRepository batchRepository,
            PositionValidationService validationService,
            LoaderConfig config,
            MeterRegistry meterRegistry) {
        this.positionRepository = positionRepository;
        this.batchRepository = batchRepository;
        this.validationService = validationService;
        this.config = config;
        this.uploadTimer = Timer.builder("upload.processing.time")
            .description("File upload processing time")
            .register(meterRegistry);
        this.uploadsSuccess = Counter.builder("upload.success")
            .description("Successful uploads")
            .register(meterRegistry);
        this.uploadsFailed = Counter.builder("upload.failed")
            .description("Failed uploads")
            .register(meterRegistry);
    }
    
    /**
     * Process position file upload.
     */
    @Transactional
    public UploadResult processUpload(MultipartFile file, int accountId, LocalDate businessDate) {
        log.info("Processing upload for account {} date {}: {}", 
            accountId, businessDate, file.getOriginalFilename());
        
        return uploadTimer.record(() -> {
            try {
                // Parse file
                List<PositionDto> positions = parseFile(file, accountId, businessDate);
                
                if (positions.isEmpty()) {
                    return UploadResult.failure("No positions found in file");
                }
                
                // Validate
                var validationResult = validationService.validate(positions);
                List<String> warnings = new ArrayList<>();
                
                if (!validationResult.isValid()) {
                    uploadsFailed.increment();
                    return UploadResult.failure(validationResult.errors());
                }
                
                if (validationResult.hasWarnings()) {
                    warnings.addAll(validationResult.warnings());
                }
                
                // Create batch
                int batchId = batchRepository.createBatch(accountId, businessDate, "UPLOAD");
                
                // Delete existing positions
                int deleted = positionRepository.deleteByAccountAndDate(accountId, businessDate);
                if (deleted > 0) {
                    warnings.add("Replaced " + deleted + " existing positions");
                }
                
                // Insert positions
                int inserted = positionRepository.batchInsert(positions, batchId);
                
                // Complete batch
                batchRepository.completeBatch(batchId, inserted);
                
                uploadsSuccess.increment();
                log.info("Upload complete for account {} date {}: {} positions", 
                    accountId, businessDate, inserted);
                
                if (warnings.isEmpty()) {
                    return UploadResult.success(batchId, inserted);
                } else {
                    return UploadResult.successWithWarnings(batchId, inserted, warnings);
                }
                
            } catch (Exception e) {
                uploadsFailed.increment();
                log.error("Upload failed for account {} date {}", accountId, businessDate, e);
                return UploadResult.failure("Upload processing failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Parse CSV file to positions.
     */
    private List<PositionDto> parseFile(MultipartFile file, int accountId, 
            LocalDate businessDate) throws Exception {
        List<PositionDto> positions = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {
            
            String line;
            int lineNum = 0;
            String[] headers = null;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String[] values = line.split(",");
                
                // First non-empty line is header
                if (headers == null) {
                    headers = values;
                    continue;
                }
                
                try {
                    PositionDto position = parseRow(values, headers, accountId, businessDate);
                    positions.add(position);
                } catch (Exception e) {
                    log.warn("Failed to parse line {}: {}", lineNum, e.getMessage());
                }
            }
        }
        
        return positions;
    }
    
    /**
     * Parse a single CSV row.
     */
    private PositionDto parseRow(String[] values, String[] headers, 
            int accountId, LocalDate businessDate) {
        int productId = 0;
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal price = BigDecimal.ZERO;
        String currency = "USD";
        BigDecimal marketValueLocal = BigDecimal.ZERO;
        BigDecimal marketValueBase = BigDecimal.ZERO;
        String positionType = "PHYSICAL";
        
        for (int i = 0; i < headers.length && i < values.length; i++) {
            String header = headers[i].trim().toLowerCase();
            String value = values[i].trim();
            
            if (value.isEmpty()) continue;
            
            switch (header) {
                case "productid", "product_id", "product" -> productId = Integer.parseInt(value);
                case "quantity", "qty" -> quantity = new BigDecimal(value);
                case "price" -> price = new BigDecimal(value);
                case "currency", "ccy" -> currency = value;
                case "marketvaluelocal", "market_value_local", "mv_local" -> 
                    marketValueLocal = new BigDecimal(value);
                case "marketvaluebase", "market_value_base", "mv_base" -> 
                    marketValueBase = new BigDecimal(value);
                case "positiontype", "position_type", "type" -> positionType = value;
            }
        }
        
        // Calculate market values if not provided
        if (marketValueLocal.compareTo(BigDecimal.ZERO) == 0) {
            marketValueLocal = quantity.multiply(price);
        }
        if (marketValueBase.compareTo(BigDecimal.ZERO) == 0) {
            marketValueBase = marketValueLocal;
        }
        
        return new PositionDto(
            null,
            accountId,
            productId,
            businessDate,
            quantity,
            price,
            currency,
            marketValueLocal,
            marketValueBase,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            "UPLOAD",
            positionType,
            false
        );
    }
    
    /**
     * Validate file before processing.
     */
    public List<String> validateFile(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        
        if (file == null || file.isEmpty()) {
            errors.add("File is empty");
            return errors;
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            errors.add("File must be a CSV file");
        }
        
        // Check file size (10MB max)
        if (file.getSize() > 10 * 1024 * 1024) {
            errors.add("File exceeds maximum size of 10MB");
        }
        
        return errors;
    }
}
