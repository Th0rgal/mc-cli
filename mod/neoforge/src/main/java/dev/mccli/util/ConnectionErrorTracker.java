package dev.mccli.util;

import dev.mccli.McCliMod;
import net.minecraft.network.chat.Component;

/**
 * Tracks the last connection error for reporting via CLI.
 *
 * When a connection fails (e.g., invalid session, server full, banned),
 * Minecraft shows a DisconnectedScreen with a reason. This class captures
 * that reason so it can be queried via the server command.
 */
public class ConnectionErrorTracker {
    private static volatile String lastError = null;
    private static volatile long lastErrorTime = 0;
    private static volatile String lastServerAddress = null;
    private static volatile boolean isInvalidSession = false;

    // Common invalid session error patterns
    private static final String[] INVALID_SESSION_PATTERNS = {
        "invalid session",
        "failed to login",
        "failed to verify username",
        "authentication servers are down",
        "not authenticated with minecraft.net",
        "multiplayer is disabled",
        "session has expired"
    };

    /**
     * Check if error message indicates an invalid session.
     */
    private static boolean checkInvalidSession(String errorMessage) {
        if (errorMessage == null) return false;
        String lowerError = errorMessage.toLowerCase();
        for (String pattern : INVALID_SESSION_PATTERNS) {
            if (lowerError.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Record a connection error.
     *
     * @param reason The disconnect reason text
     * @param serverAddress The server that was being connected to (if known)
     */
    public static void recordError(Component reason, String serverAddress) {
        String errorMessage = reason != null ? reason.getString() : "Unknown error";
        lastError = errorMessage;
        lastErrorTime = System.currentTimeMillis();
        lastServerAddress = serverAddress;
        isInvalidSession = checkInvalidSession(errorMessage);
        McCliMod.LOGGER.debug("Connection error recorded: {} (server: {}, invalidSession: {})",
            errorMessage, serverAddress, isInvalidSession);
    }

    /**
     * Record a connection error with just a string message.
     */
    public static void recordError(String reason) {
        lastError = reason != null ? reason : "Unknown error";
        lastErrorTime = System.currentTimeMillis();
        lastServerAddress = null;
        isInvalidSession = checkInvalidSession(lastError);
        McCliMod.LOGGER.debug("Connection error recorded: {} (invalidSession: {})", reason, isInvalidSession);
    }

    /**
     * Get the last recorded error message.
     *
     * @return The error message, or null if no error has been recorded
     */
    public static String getLastError() {
        return lastError;
    }

    /**
     * Get the timestamp of the last error.
     *
     * @return Unix timestamp in milliseconds, or 0 if no error recorded
     */
    public static long getLastErrorTime() {
        return lastErrorTime;
    }

    /**
     * Get the server address that caused the last error.
     *
     * @return Server address or null
     */
    public static String getLastServerAddress() {
        return lastServerAddress;
    }

    /**
     * Check if there's a recent error (within the last 30 seconds).
     */
    public static boolean hasRecentError() {
        if (lastError == null) return false;
        return (System.currentTimeMillis() - lastErrorTime) < 30000;
    }

    /**
     * Check if the last error was an invalid session error.
     *
     * @return true if the last error indicates an invalid/expired session
     */
    public static boolean isInvalidSession() {
        return isInvalidSession;
    }

    /**
     * Check if there's a recent invalid session error (within the last 30 seconds).
     */
    public static boolean hasRecentInvalidSessionError() {
        return hasRecentError() && isInvalidSession;
    }

    /**
     * Clear the last error.
     */
    public static void clear() {
        lastError = null;
        lastErrorTime = 0;
        lastServerAddress = null;
        isInvalidSession = false;
    }
}
