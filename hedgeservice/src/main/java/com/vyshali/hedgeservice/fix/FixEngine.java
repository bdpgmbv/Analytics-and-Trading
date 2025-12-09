package com.vyshali.hedgeservice.fix;

/*
 * 12/04/2025 - 2:34 PM
 * @author Vyshali Prabananth Lal
 */

package com.vyshali.hedgeservice.fix;

import com.vyshali.hedgeservice.dto.HedgeExecutionRequestDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired; // Use Autowired for circular dep avoidance if needed
import org.springframework.kafka.core.KafkaTemplate; // <--- NEW
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class FixEngine extends MessageCracker implements Application {

    private SocketInitiator initiator;
    private SessionID activeSession;

    // Inject Kafka to close the loop
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @PostConstruct
    public void start() {
        // ... (Existing startup code) ...
        try {
            SessionSettings settings = new SessionSettings(getClass().getResourceAsStream("/quickfix-client.cfg"));
            FileStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new ScreenLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();
            initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
            initiator.start();
            log.info("FIX Engine Started.");
        } catch (Exception e) {
            log.error("Failed to start FIX Engine", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (initiator != null) initiator.stop();
    }

    public String sendOrder(HedgeExecutionRequestDTO request) {
        if (activeSession == null) throw new RuntimeException("FIX Session Down");

        String clOrdId = UUID.randomUUID().toString();
        NewOrderSingle order = new NewOrderSingle(
                new ClOrdID(clOrdId),
                new Side(request.side().equalsIgnoreCase("BUY") ? Side.BUY : Side.SELL),
                new TransactTime(LocalDateTime.now()),
                new OrdType(OrdType.MARKET)
        );
        order.set(new Symbol(request.currencyPair()));
        order.set(new OrderQty(request.quantity().doubleValue()));
        order.set(new HandlInst('1'));

        try {
            Session.sendToTarget(order, activeSession);
            return clOrdId;
        } catch (SessionNotFound e) {
            throw new RuntimeException("Session Lost");
        }
    }

    // --- CALLBACKS ---
    // ... (onCreate, onLogon, onLogout, toAdmin, fromAdmin, toApp same as before) ...
    @Override public void onCreate(SessionID sessionId) {}
    @Override public void onLogon(SessionID sessionId) { this.activeSession = sessionId; }
    @Override public void onLogout(SessionID sessionId) { this.activeSession = null; }
    @Override public void toAdmin(Message message, SessionID sessionId) {}
    @Override public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {}
    @Override public void toApp(Message message, SessionID sessionId) throws DoNotSend {}

    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, UnsupportedMessageType, IncorrectTagValue {
        crack(message, sessionId);
    }

    // --- THE CRITICAL FIX ---
    public void onMessage(ExecutionReport message, SessionID sessionID) throws FieldNotFound {
        String execId = message.getExecID().getValue();
        String orderId = message.getClOrdID().getValue(); // Correlate with our ClOrdId
        double filledQty = message.getCumQty().getValue();
        double price = message.getAvgPx().getValue();
        String symbol = message.getSymbol().getValue();
        String side = message.getSide().getValue() == Side.BUY ? "BUY" : "SELL";

        log.info("FIX FILL: ID={} Order={} Qty={} Price={}", execId, orderId, filledQty, price);

        // 1. Create a DTO that matches TradeFillProcessor's expectation
        // We reuse the DTO format defined in common or replicate it here
        // Assuming JSON serialization:
        String jsonPayload = String.format("""
            {
                "execId": "%s",
                "orderId": "%s",
                "symbol": "%s",
                "side": "%s",
                "lastQty": %f,
                "lastPx": %f,
                "status": "FILLED",
                "venue": "FIX_EXCHANGE"
            }
        """, execId, orderId, symbol, side, filledQty, price);

        // 2. Publish to Kafka (TradeFillProcessor listens to RAW_EXECUTION_REPORTS)
        kafkaTemplate.send("RAW_EXECUTION_REPORTS", orderId, jsonPayload);
    }
}