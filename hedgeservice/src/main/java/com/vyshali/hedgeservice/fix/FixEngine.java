package com.vyshali.hedgeservice.fix;

/*
 * 12/10/2025 - FIXED: Removed duplicate package declaration (was on lines 1 AND 8)
 * @author Vyshali Prabananth Lal
 *
 * FIX Protocol Engine for executing hedge trades via FX Matrix
 */

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.ExecutionReport;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class FixEngine implements Application {

    @Value("${fix.config.path:fix-client.cfg}")
    private String configPath;

    @Value("${fix.sender.comp.id:FXANALYZER}")
    private String senderCompId;

    @Value("${fix.target.comp.id:FXMATRIX}")
    private String targetCompId;

    private SessionID sessionId;
    private SocketInitiator initiator;
    private final ConcurrentMap<String, OrderCallback> pendingOrders = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface OrderCallback {
        void onExecutionReport(String clOrdId, String execType, BigDecimal fillQty, BigDecimal fillPx, String ordStatus);
    }

    @PostConstruct
    public void init() {
        try {
            SessionSettings settings = new SessionSettings(configPath);
            MessageStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new FileLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();

            initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
            initiator.start();
            log.info("FIX Engine started - SenderCompID: {}, TargetCompID: {}", senderCompId, targetCompId);
        } catch (ConfigError e) {
            log.error("FIX configuration error: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize FIX engine", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (initiator != null) {
            initiator.stop();
            log.info("FIX Engine stopped");
        }
    }

    // ============================================================
    // Application Interface Implementation
    // ============================================================

    @Override
    public void onCreate(SessionID sessionId) {
        this.sessionId = sessionId;
        log.info("FIX session created: {}", sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("FIX session logged on: {}", sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        log.warn("FIX session logged out: {}", sessionId);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        // Outgoing admin messages (logon, heartbeat, etc.)
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        // Incoming admin messages
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        log.debug("Sending FIX message: {}", message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {

        if (message instanceof ExecutionReport report) {
            handleExecutionReport(report);
        }
    }

    // ============================================================
    // Order Submission
    // ============================================================

    /**
     * Send a new FX order to FX Matrix
     *
     * @param currencyPair e.g., "EUR/USD"
     * @param side         BUY or SELL
     * @param quantity     Amount in base currency
     * @param orderType    MARKET or LIMIT
     * @param limitPrice   Price for limit orders (null for market)
     * @param callback     Callback for execution reports
     * @return Client Order ID
     */
    public String sendOrder(String currencyPair, Side side, BigDecimal quantity, OrdType orderType, BigDecimal limitPrice, OrderCallback callback) {

        String clOrdId = generateClOrdId();

        try {
            NewOrderSingle order = new NewOrderSingle(new ClOrdID(clOrdId), side, new TransactTime(LocalDateTime.now()), orderType);

            // Set symbol (currency pair)
            order.set(new Symbol(currencyPair));

            // Set quantity
            order.set(new OrderQty(quantity.doubleValue()));

            // Set price for limit orders
            if (orderType.getValue() == OrdType.LIMIT && limitPrice != null) {
                order.set(new Price(limitPrice.doubleValue()));
            }

            // Time in force - Day order
            order.set(new TimeInForce(TimeInForce.DAY));

            // Custom fields for FX
            order.setString(15, extractBaseCurrency(currencyPair));  // Currency

            // Register callback
            if (callback != null) {
                pendingOrders.put(clOrdId, callback);
            }

            // Send order
            Session.sendToTarget(order, sessionId);

            log.info("Order sent - ClOrdId: {}, Pair: {}, Side: {}, Qty: {}", clOrdId, currencyPair, side, quantity);

            return clOrdId;

        } catch (SessionNotFound e) {
            log.error("FIX session not found: {}", e.getMessage());
            throw new RuntimeException("Cannot send order - FIX session not available", e);
        }
    }

    /**
     * Send market order (convenience method)
     */
    public String sendMarketOrder(String currencyPair, boolean isBuy, BigDecimal quantity, OrderCallback callback) {
        return sendOrder(currencyPair, isBuy ? new Side(Side.BUY) : new Side(Side.SELL), quantity, new OrdType(OrdType.MARKET), null, callback);
    }

    /**
     * Send limit order (convenience method)
     */
    public String sendLimitOrder(String currencyPair, boolean isBuy, BigDecimal quantity, BigDecimal limitPrice, OrderCallback callback) {
        return sendOrder(currencyPair, isBuy ? new Side(Side.BUY) : new Side(Side.SELL), quantity, new OrdType(OrdType.LIMIT), limitPrice, callback);
    }

    // ============================================================
    // Execution Report Handling
    // ============================================================

    private void handleExecutionReport(ExecutionReport report) throws FieldNotFound {
        String clOrdId = report.getClOrdID().getValue();
        char execType = report.getExecType().getValue();
        char ordStatus = report.getOrdStatus().getValue();

        BigDecimal fillQty = BigDecimal.ZERO;
        BigDecimal fillPx = BigDecimal.ZERO;

        if (report.isSetLastQty()) {
            fillQty = BigDecimal.valueOf(report.getLastQty().getValue());
        }
        if (report.isSetLastPx()) {
            fillPx = BigDecimal.valueOf(report.getLastPx().getValue());
        }

        log.info("Execution Report - ClOrdId: {}, ExecType: {}, OrdStatus: {}, " + "FillQty: {}, FillPx: {}", clOrdId, execType, ordStatus, fillQty, fillPx);

        // Invoke callback if registered
        OrderCallback callback = pendingOrders.get(clOrdId);
        if (callback != null) {
            callback.onExecutionReport(clOrdId, String.valueOf(execType), fillQty, fillPx, String.valueOf(ordStatus));

            // Remove callback if order is terminal
            if (ordStatus == OrdStatus.FILLED || ordStatus == OrdStatus.CANCELED || ordStatus == OrdStatus.REJECTED) {
                pendingOrders.remove(clOrdId);
            }
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String generateClOrdId() {
        return "FXA-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String extractBaseCurrency(String currencyPair) {
        // "EUR/USD" -> "EUR"
        if (currencyPair != null && currencyPair.contains("/")) {
            return currencyPair.split("/")[0];
        }
        return currencyPair != null ? currencyPair.substring(0, 3) : "USD";
    }

    /**
     * Check if FIX session is connected
     */
    public boolean isConnected() {
        return sessionId != null && Session.lookupSession(sessionId) != null && Session.lookupSession(sessionId).isLoggedOn();
    }

    /**
     * Get count of pending orders
     */
    public int getPendingOrderCount() {
        return pendingOrders.size();
    }
}