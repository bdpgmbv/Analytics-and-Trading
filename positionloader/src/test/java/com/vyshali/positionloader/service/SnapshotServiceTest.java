package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 2:40 PM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.repository.AuditRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {

    @Mock
    private MspmService mspm;

    @Mock
    private PositionRepository positions;

    @Mock
    private ReferenceDataRepository refData;

    @Mock
    private AuditRepository audit;

    @Mock
    private KafkaTemplate<String, Object> kafka;

    @Mock
    private ObjectMapper json;

    @InjectMocks
    private SnapshotService snapshotService;

    private AccountSnapshotDTO validSnapshot;
    private List<PositionDTO> validPositions;

    @BeforeEach
    void setup() {
        validPositions = List.of(new PositionDTO(1001, "AAPL", "EQUITY", "USD", new BigDecimal("100"), new BigDecimal("150.00"), "EOD_HOLDING", "REF-001"), new PositionDTO(1002, "GOOGL", "EQUITY", "USD", new BigDecimal("50"), new BigDecimal("2800.00"), "EOD_HOLDING", "REF-002"));

        validSnapshot = new AccountSnapshotDTO(1001,           // accountId
                100,            // clientId
                "Test Client",  // clientName
                200,            // fundId
                "Test Fund",    // fundName
                "USD",          // baseCurrency
                "ACC-1001",     // accountNumber
                "CUSTODY",      // accountType
                validPositions);

        // Default mock behavior
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    // ==================== EOD FLOW TESTS ====================

    @Nested
    @DisplayName("EOD Flow Tests")
    class EodFlowTests {

        @Test
        @DisplayName("processEod - Happy Path")
        void processEod_HappyPath_SavesPositionsAndPublishesEvent() {
            // Given
            when(mspm.fetchSnapshot(1001)).thenReturn(validSnapshot);
            when(positions.createBatch(1001)).thenReturn(5);
            when(audit.isClientComplete(100, java.time.LocalDate.now())).thenReturn(false);

            // When
            snapshotService.processEod(1001);

            // Then
            verify(mspm).fetchSnapshot(1001);
            verify(refData).ensureReferenceData(validSnapshot);
            verify(positions).createBatch(1001);
            verify(positions).insertPositions(eq(1001), anyList(), eq("MSPM_EOD"), eq(5));
            verify(positions).activateBatch(1001, 5);
            verify(audit).markAccountComplete(eq(1001), eq(100), any());
            verify(kafka).send(eq("POSITION_CHANGE_EVENTS"), eq("1001"), any());
        }

        @Test
        @DisplayName("processEod - MSPM Returns Null")
        void processEod_MspmReturnsNull_ThrowsException() {
            // Given
            when(mspm.fetchSnapshot(1001)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> snapshotService.processEod(1001)).isInstanceOf(RuntimeException.class).hasMessageContaining("MSPM returned null");

            verify(positions, never()).createBatch(anyInt());
        }

        @Test
        @DisplayName("processEod - Client Complete Triggers Sign-Off")
        void processEod_AllAccountsComplete_PublishesSignOff() {
            // Given
            when(mspm.fetchSnapshot(1001)).thenReturn(validSnapshot);
            when(positions.createBatch(1001)).thenReturn(5);
            when(audit.isClientComplete(100, java.time.LocalDate.now())).thenReturn(true);
            when(audit.countClientAccounts(100)).thenReturn(3);

            // When
            snapshotService.processEod(1001);

            // Then
            verify(kafka).send(eq("CLIENT_REPORTING_SIGNOFF"), eq("100"), any());
        }

        @Test
        @DisplayName("processEod - Empty Positions Still Creates Batch")
        void processEod_EmptyPositions_CreatesBatchButNoInsert() {
            // Given
            AccountSnapshotDTO emptySnapshot = new AccountSnapshotDTO(1001, 100, "Test", 200, "Fund", "USD", "ACC-1001", "CUSTODY", List.of() // Empty positions
            );
            when(mspm.fetchSnapshot(1001)).thenReturn(emptySnapshot);
            when(positions.createBatch(1001)).thenReturn(5);

            // When
            snapshotService.processEod(1001);

            // Then
            verify(positions).createBatch(1001);
            verify(positions, never()).insertPositions(anyInt(), anyList(), anyString(), anyInt());
            verify(positions).activateBatch(1001, 5);
        }
    }

    // ==================== INTRADAY FLOW TESTS ====================

    @Nested
    @DisplayName("Intraday Flow Tests")
    class IntradayFlowTests {

        @Test
        @DisplayName("processIntraday - Updates Positions")
        void processIntraday_ValidSnapshot_UpdatesEachPosition() {
            // When
            snapshotService.processIntraday(validSnapshot);

            // Then
            verify(refData).ensureReferenceData(validSnapshot);
            verify(positions, times(2)).updatePosition(eq(1001), any(PositionDTO.class));
            verify(kafka).send(eq("POSITION_CHANGE_EVENTS"), eq("1001"), any());
        }

        @Test
        @DisplayName("processIntraday - Empty Positions Does Nothing")
        void processIntraday_EmptyPositions_NoUpdates() {
            // Given
            AccountSnapshotDTO emptySnapshot = new AccountSnapshotDTO(1001, 100, "Test", 200, "Fund", "USD", "ACC-1001", "CUSTODY", List.of());

            // When
            snapshotService.processIntraday(emptySnapshot);

            // Then
            verify(positions, never()).updatePosition(anyInt(), any());
            verify(kafka, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("processIntradayRecord - Parses JSON and Processes")
        void processIntradayRecord_ValidJson_Processes() throws Exception {
            // Given
            String jsonRecord = "{}"; // Actual JSON would go here
            when(json.readValue(jsonRecord, AccountSnapshotDTO.class)).thenReturn(validSnapshot);

            // When
            snapshotService.processIntradayRecord(jsonRecord);

            // Then
            verify(json).readValue(jsonRecord, AccountSnapshotDTO.class);
            verify(positions, times(2)).updatePosition(eq(1001), any());
        }

        @Test
        @DisplayName("processIntradayRecord - Invalid JSON Throws")
        void processIntradayRecord_InvalidJson_ThrowsException() throws Exception {
            // Given
            String badJson = "not valid json";
            when(json.readValue(badJson, AccountSnapshotDTO.class)).thenThrow(new RuntimeException("Parse error"));

            // When/Then
            assertThatThrownBy(() -> snapshotService.processIntradayRecord(badJson)).isInstanceOf(RuntimeException.class).hasMessageContaining("Invalid intraday record");
        }
    }

    // ==================== MANUAL UPLOAD TESTS ====================

    @Nested
    @DisplayName("Manual Upload Flow Tests")
    class ManualUploadTests {

        @Test
        @DisplayName("processManualUpload - Creates New Batch")
        void processManualUpload_ValidSnapshot_CreatesNewBatch() {
            // Given
            when(positions.createBatch(1001)).thenReturn(10);

            // When
            snapshotService.processManualUpload(validSnapshot, "test-user");

            // Then
            verify(refData).ensureReferenceData(validSnapshot);
            verify(positions).createBatch(1001);
            verify(positions).insertPositions(eq(1001), anyList(), eq("MANUAL_UPLOAD"), eq(10));
            verify(positions).activateBatch(1001, 10);
            verify(audit).log(eq("MANUAL_UPLOAD"), eq("1001"), eq("test-user"), contains("2 positions"));
        }

        @Test
        @DisplayName("processManualUpload - Null Account ID Throws")
        void processManualUpload_NullAccountId_ThrowsException() {
            // Given
            AccountSnapshotDTO badSnapshot = new AccountSnapshotDTO(null, 100, "Test", 200, "Fund", "USD", "ACC", "CUSTODY", validPositions);

            // When/Then
            assertThatThrownBy(() -> snapshotService.processManualUpload(badSnapshot, "user")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Account ID is required");
        }
    }

    // ==================== VALIDATION TESTS ====================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Zero Price Positions Logged as Warning")
        void validation_ZeroPricePositions_LogsWarning() {
            // Given
            List<PositionDTO> zeroPricePositions = List.of(new PositionDTO(1001, "AAPL", "EQUITY", "USD", new BigDecimal("100"), BigDecimal.ZERO,  // Zero price
                    "EOD_HOLDING", "REF-001"));
            AccountSnapshotDTO snapshot = new AccountSnapshotDTO(1001, 100, "Test", 200, "Fund", "USD", "ACC-1001", "CUSTODY", zeroPricePositions);
            when(mspm.fetchSnapshot(1001)).thenReturn(snapshot);
            when(positions.createBatch(1001)).thenReturn(5);

            // When - should not throw, just log warning
            snapshotService.processEod(1001);

            // Then - still processes
            verify(positions).insertPositions(anyInt(), anyList(), anyString(), anyInt());
        }

        @Test
        @DisplayName("Null Positions Filtered Out")
        void validation_NullPositionsFiltered() {
            // Given
            List<PositionDTO> mixedPositions = new java.util.ArrayList<>();
            mixedPositions.add(validPositions.get(0));
            mixedPositions.add(null);  // Null position
            mixedPositions.add(validPositions.get(1));

            AccountSnapshotDTO snapshot = new AccountSnapshotDTO(1001, 100, "Test", 200, "Fund", "USD", "ACC-1001", "CUSTODY", mixedPositions);
            when(mspm.fetchSnapshot(1001)).thenReturn(snapshot);
            when(positions.createBatch(1001)).thenReturn(5);

            // When
            snapshotService.processEod(1001);

            // Then - only 2 valid positions inserted
            ArgumentCaptor<List<PositionDTO>> captor = ArgumentCaptor.forClass(List.class);
            verify(positions).insertPositions(eq(1001), captor.capture(), anyString(), anyInt());
            assertThat(captor.getValue()).hasSize(2);
        }
    }
}
