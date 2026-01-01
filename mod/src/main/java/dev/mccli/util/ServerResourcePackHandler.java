package dev.mccli.util;

import dev.mccli.McCliMod;

/**
 * Handles server resource pack auto-accept/reject policy.
 *
 * Policy values:
 * - "prompt" (default): Show the normal Minecraft prompt
 * - "accept": Automatically accept and download the resource pack
 * - "reject": Automatically reject the resource pack
 */
public final class ServerResourcePackHandler {
    private ServerResourcePackHandler() {}

    public enum Policy {
        PROMPT,  // Default behavior - show prompt to user
        ACCEPT,  // Auto-accept the resource pack
        REJECT   // Auto-reject the resource pack
    }

    private static Policy currentPolicy = Policy.PROMPT;

    /**
     * Set the resource pack policy for the next/current server connection.
     */
    public static void setPolicy(Policy policy) {
        currentPolicy = policy;
        McCliMod.LOGGER.info("Server resource pack policy set to: {}", policy);
    }

    /**
     * Set policy from string value.
     */
    public static void setPolicy(String policyStr) {
        Policy policy = switch (policyStr.toLowerCase()) {
            case "accept" -> Policy.ACCEPT;
            case "reject" -> Policy.REJECT;
            default -> Policy.PROMPT;
        };
        setPolicy(policy);
    }

    /**
     * Get the current resource pack policy.
     */
    public static Policy getPolicy() {
        return currentPolicy;
    }

    /**
     * Check if we should auto-handle the resource pack prompt.
     */
    public static boolean shouldAutoHandle() {
        return currentPolicy != Policy.PROMPT;
    }

    /**
     * Check if we should accept the resource pack.
     */
    public static boolean shouldAccept() {
        return currentPolicy == Policy.ACCEPT;
    }

    /**
     * Reset policy to default (prompt) after disconnection.
     */
    public static void reset() {
        currentPolicy = Policy.PROMPT;
        McCliMod.LOGGER.debug("Server resource pack policy reset to PROMPT");
    }
}
