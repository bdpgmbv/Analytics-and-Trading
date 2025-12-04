package com.vyshali.hedgeservice.fix;

/*
 * 12/04/2025 - 2:34 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.dto.HedgeExecutionRequestDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class FixEngine extends MessageCracker implements Application {

    private SocketInitiator initiator;
    private SessionID activeSession;

    @PostConstruct
    public void start() {
        try {
            SessionSettings settings = new SessionSettings(getClass().getResourceAsStream("/quickfix-client.cfg"));
            FileStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new ScreenLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();

            initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
            initiator.start();
            log.info("FIX Engine Started. Connecting to Exchange...");
        } catch (Exception e) {
            log.error("Failed to start FIX Engine", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (initiator != null) initiator.stop();
    }

    // --- SENDING ORDERS ---

    public String sendOrder(HedgeExecutionRequestDTO request) {
        if (activeSession == null) {
            throw new RuntimeException("FIX Session is not active! Cannot trade.");
        }

        String clOrdId = UUID.randomUUID().toString();

        // 1. Build FIX 4.4 Message
        NewOrderSingle order = new NewOrderSingle(new ClOrdID(clOrdId), new Side(request.side().equalsIgnoreCase("BUY") ? Side.BUY : Side.SELL), new TransactTime(LocalDateTime.now()), new OrdType(OrdType.MARKET));

        order.set(new Symbol(request.currencyPair()));
        order.set(new OrderQty(request.quantity().doubleValue()));
        order.set(new HandlInst('1')); // Automated execution

        // 2. Send over TCP
        try {
            Session.sendToTarget(order, activeSession);
            log.info("FIX SENT: NewOrderSingle ID={}", clOrdId);
            return clOrdId;
        } catch (SessionNotFound e) {
            log.error("Session dropped while sending", e);
            throw new RuntimeException("Trade Failed: Session Lost");
        }
    }

    // --- RECEIVING MESSAGES (Callbacks) ---

    @Override
    public void onCreate(SessionID sessionId) {
        log.info("Session Created: {}", sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("Logged On: {}", sessionId);
        this.activeSession = sessionId;
    }

    @Override
    public void onLogout(SessionID sessionId) {
        log.warn("Logged Out: {}", sessionId);
        this.activeSession = null;
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, UnsupportedMessageType, IncorrectTagValue {
        // "Crack" the message (identify type) and call specific handler
        crack(message, sessionId);
    }

    // Handler for Execution Reports (Fills)
    public void onMessage(ExecutionReport message, SessionID sessionID) throws FieldNotFound {
        String execId = message.getExecID().getValue();
        String status = message.getOrdStatus().getValue() + ""; // char to string
        double filledQty = message.getCumQty().getValue();
        double price = message.getAvgPx().getValue();

        log.info("FIX RECV: ExecutionReport ExecID={} Status={} Qty={} Price={}", execId, status, filledQty, price);

        // In a real system, we would push this 'Fill' to the TradeFillProcessor via Kafka here.
    }
}
