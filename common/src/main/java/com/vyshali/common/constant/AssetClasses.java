package com.vyshali.common.constant;

/*
 * 12/08/2025 - 3:26 PM
 * @author Vyshali Prabananth Lal
 */

/*
 * Shared constants to prevent "Magic String" errors across microservices.
 */
public final class AssetClasses {
    private AssetClasses() {} // Prevent instantiation

    public static final String EQUITY = "EQUITY";
    public static final String FX = "FX";
    public static final String CASH = "CASH";
    public static final String FX_FORWARD = "FX_FORWARD";
    public static final String EQUITY_SWAP = "EQUITY_SWAP";
}