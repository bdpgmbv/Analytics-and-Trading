package com.vyshali.positionloader.service;

import com.vyshali.common.lifecycle.GracefulShutdownManager;
import com.vyshali.common.logging.LogContext;
import com.vyshali.common.repository.AuditRepository;
import com.vyshali.common.service.AlertService;
import com.vyshali.common.service.BusinessDayService;
import com.vyshali.positionloader.repository.BatchRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * EOD (End of Day) processing service.
 * 
 * ✅ MODIFIED: Now uses common module services instead of local duplicates:
 *    - BusinessDayService from common (was local)
 *    - AlertService from common (was local)
 *    - AuditRepository from common (was local)
 *    - GracefulShutdownManager from common (new)
 *    - LogContext from common (new)
 */
@Service
public class EodService {

    private static final Logger log = LoggerFactory.getLogger(EodService.class);

    // ✅ INJECT FROM COMMON MODULE
    private final BusinessDayService businessDayService;
    private final AlertService alertService;
    private final AuditRepository auditRepository;
    private final GracefulShutdownManager shutdownManager;

    // Keep your existing repositories
    private final BatchRepository batchRepository;
    private final PositionRepository positionRepository;
    private final MeterRegistry metrics;

    public EodService(
            // ✅ Common module services
            BusinessDayService businessDayService,
            AlertService alertService,
            AuditRepository auditRepository,
            GracefulShutdownManager shutdownManager,
            // Your existing repositories
            BatchRepository batchRepository,
            PositionRepository positionRepository,
            MeterRegistry metrics) {
        this.businessDayService = businessDayService;
        this.alertService = alertService;
        this.auditRepository = auditRepository;
        this.shutdownManager = shutdownManager;
        this.batchRepository = batchRepository;
        this.positionRepository = positionRepository;
        this.metrics = metrics;
    }

    /**
     * Run EOD processing for a specific date.
     */
    @Transactional
    public void runEod(LocalDate businessDate) {
        // ✅ Use LogContext for structured logging
        LogContext.with("businessDate", businessDate.toString())
                .run(() -> doRunEod(businessDate));
    }

    private void doRunEod(LocalDate businessDate) {
        // ✅ Check shutdown state before starting
        if (shutdownManager.isShuttingDown()) {
            log.warn("Shutdown in progress, skipping EOD for {}", businessDate);
            return;
        }

        // ✅ Track job for graceful shutdown
        shutdownManager.jobStarted();
        try {
            // ✅ Use common BusinessDayService (DELETE your local BusinessDayService.java)
            if (!businessDayService.isBusinessDay(businessDate)) {
                log.info("Skipping EOD for non-business day: {}", businessDate);
                auditRepository.logAsync("EOD_SKIPPED", businessDate.toString(), "system",
                        "Non-business day");
                return;
            }

            log.info("Starting EOD processing for {}", businessDate);
            auditRepository.logAsync("EOD_STARTED", businessDate.toString(), "system", "");

            // Your existing EOD logic here...
            processEodBatches(businessDate);
            archiveOldPositions(businessDate);
            generateEodReports(businessDate);

            // ✅ Use common AlertService (DELETE your local AlertService.java)
            log.info("EOD completed successfully for {}", businessDate);
            auditRepository.logAsync("EOD_COMPLETED", businessDate.toString(), "system",
                    "Success");
            metrics.counter("eod.completed.success").increment();

        } catch (Exception e) {
            log.error("EOD failed for {}: {}", businessDate, e.getMessage(), e);
            
            // ✅ Alert via common AlertService
            alertService.eodFailed(businessDate, e.getMessage());
            
            auditRepository.logAsync("EOD_FAILED", businessDate.toString(), "system",
                    "Error: " + e.getMessage());
            metrics.counter("eod.completed.failed").increment();
            throw e;
            
        } finally {
            // ✅ Mark job as ended for shutdown manager
            shutdownManager.jobEnded();
        }
    }

    /**
     * Get next business day for scheduling.
     */
    public LocalDate getNextBusinessDay() {
        // ✅ Use common BusinessDayService
        return businessDayService.addBusinessDays(LocalDate.now(), 1);
    }

    /**
     * Get T+2 settlement date.
     */
    public LocalDate getSettlementDate(LocalDate tradeDate) {
        // ✅ Use common BusinessDayService
        return businessDayService.getT2SettlementDate(tradeDate);
    }

    // Your existing private methods (keep these)
    private void processEodBatches(LocalDate businessDate) {
        // Your existing batch processing logic
        log.debug("Processing EOD batches for {}", businessDate);
    }

    private void archiveOldPositions(LocalDate businessDate) {
        // Your existing archive logic
        log.debug("Archiving old positions for {}", businessDate);
    }

    private void generateEodReports(LocalDate businessDate) {
        // Your existing report generation logic
        log.debug("Generating EOD reports for {}", businessDate);
    }
}
