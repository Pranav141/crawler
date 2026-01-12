package com.pranav;

public class LogColors {
    // Reset
    public static final String RESET = "\u001B[0m";

    // Standard Colors
    public static final String RED    = "\u001B[31m"; // Errors / Alerts
    public static final String GREEN  = "\u001B[32m"; // Success / Acquired
    public static final String YELLOW = "\u001B[33m"; // Rotation / Warnings
    public static final String BLUE   = "\u001B[34m"; // Testing / Checking
    public static final String PURPLE = "\u001B[35m"; // Kafka Operations
    public static final String CYAN   = "\u001B[36m"; // Active Crawling
    public static final String WHITE  = "\u001B[37m"; // General Info

    // Bold Versions (Optional - use for extra visibility)
    public static final String RED_BOLD = "\u001B[1;31m";
}
