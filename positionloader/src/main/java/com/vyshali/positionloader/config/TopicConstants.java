package com.vyshali.positionloader.config;

/*
 * 12/02/2025 - 12:41 PM
 * @author Vyshali Prabananth Lal
 */

public final class TopicConstants {
    private TopicConstants() {
    } // Prevent instantiation

    // Inbound Topics
    public static final String TOPIC_EOD_TRIGGER = "MSPM_EOD_TRIGGER";
    public static final String TOPIC_INTRADAY = "MSPA_INTRADAY";

    // Consumer Groups
    public static final String GROUP_EOD = "positionloader-eod-group";
    public static final String GROUP_INTRADAY = "positionloader-intraday-group";

    // Outbound Topics
    public static final String TOPIC_CHANGE_EVENTS = "POSITION_CHANGE_EVENTS";
    public static final String TOPIC_SIGNOFF = "CLIENT_REPORTING_SIGNOFF";
}
