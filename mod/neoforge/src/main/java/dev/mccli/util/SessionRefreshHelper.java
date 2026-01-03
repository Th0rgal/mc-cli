package dev.mccli.util;

import dev.mccli.McCliMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.concurrent.CompletableFuture;

/**
 * Helper for refreshing the Minecraft session/profile.
 *
 * When connecting to a server fails with "invalid session", this helper
 * attempts to refresh the user's profile/session using available APIs.
 *
 * Note: Modern Minecraft (1.20+) uses Microsoft authentication, and the
 * session is typically managed by the launcher. In-game refresh capabilities
 * are limited without re-authentication through Microsoft OAuth.
 */
public class SessionRefreshHelper {

    /**
     * Attempt to refresh the current session.
     *
     * This method attempts various refresh mechanisms available in Minecraft.
     * Success is not guaranteed as session refresh often requires launcher-level
     * re-authentication.
     *
     * @return CompletableFuture with result indicating success/failure and details
     */
    public static CompletableFuture<RefreshResult> refreshSession() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                User user = client.getUser();

                if (user == null) {
                    return new RefreshResult(false, "No user session found", null);
                }

                String username = user.getName();
                String uuid = user.getProfileId().toString();
                User.Type accountType = user.getType();

                McCliMod.LOGGER.info("Attempting session refresh for user: {} (type: {})", username, accountType);

                // Try to get profile properties - this can sometimes trigger a refresh
                try {
                    // Access the session service to check/refresh profile
                    var sessionService = client.getMinecraftSessionService();
                    if (sessionService != null) {
                        // Create a game profile and try to fill it - this validates the session
                        var profile = client.getGameProfile();
                        if (profile != null) {
                            McCliMod.LOGGER.debug("Current profile: {} ({})", profile.getName(), profile.getId());

                            // Try to reload profile properties - this may refresh the session
                            try {
                                var filledProfile = sessionService.fetchProfile(profile.getId(), true);
                                if (filledProfile != null) {
                                    McCliMod.LOGGER.info("Profile fetch successful - session may be refreshed");
                                    return new RefreshResult(true, "Profile fetched successfully", username);
                                }
                            } catch (Exception e) {
                                McCliMod.LOGGER.debug("Profile fetch failed: {}", e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    McCliMod.LOGGER.debug("Session service check failed: {}", e.getMessage());
                }

                // If we get here, the session couldn't be refreshed through available APIs
                // Return partial success with a note that a game restart may be needed
                return new RefreshResult(
                    false,
                    "Session refresh through game APIs is limited. " +
                    "For Microsoft accounts, you may need to restart the game or re-authenticate through the launcher.",
                    username
                );

            } catch (Exception e) {
                McCliMod.LOGGER.error("Session refresh failed with exception", e);
                return new RefreshResult(false, "Refresh failed: " + e.getMessage(), null);
            }
        });
    }

    /**
     * Check if the current session appears to be valid.
     *
     * This is a basic check based on whether we have user info.
     * It doesn't guarantee the session will work for server connections.
     */
    public static boolean hasSession() {
        try {
            Minecraft client = Minecraft.getInstance();
            User user = client.getUser();
            return user != null && user.getProfileId() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get information about the current session.
     */
    public static SessionInfo getSessionInfo() {
        try {
            Minecraft client = Minecraft.getInstance();
            User user = client.getUser();

            if (user == null) {
                return new SessionInfo(false, null, null, null);
            }

            return new SessionInfo(
                true,
                user.getName(),
                user.getProfileId().toString(),
                user.getType().toString()
            );
        } catch (Exception e) {
            return new SessionInfo(false, null, null, null);
        }
    }

    /**
     * Result of a session refresh attempt.
     */
    public record RefreshResult(boolean success, String message, String username) {}

    /**
     * Information about the current session.
     */
    public record SessionInfo(boolean hasSession, String username, String uuid, String accountType) {}
}
