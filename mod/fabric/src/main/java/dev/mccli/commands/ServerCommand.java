package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.ConnectionErrorTracker;
import dev.mccli.util.MainThreadExecutor;
import dev.mccli.util.SessionRefreshHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Server connection management.
 *
 * Actions:
 * - connect: Connect to a multiplayer server
 *   Params: address (required), port (optional, default 25565),
 *           refresh_session (optional, boolean): attempt session refresh before connecting
 * - disconnect: Disconnect from current server/world
 * - status: Get current server connection info
 * - connection_error: Get the last connection/disconnection error
 *   Returns: has_error, error, is_invalid_session, suggestion (for invalid session)
 * - refresh_session: Attempt to refresh the authentication session
 *   Use this when connection fails with "invalid session" error
 * - session_info: Get information about the current session
 */
public class ServerCommand implements Command {
    @Override
    public String getName() {
        return "server";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "status";

        return switch (action) {
            case "connect" -> connect(params);
            case "disconnect" -> disconnect();
            case "status" -> status();
            case "connection_error" -> connectionError(params);
            case "refresh_session" -> refreshSession();
            case "session_info" -> sessionInfo();
            default -> {
                CompletableFuture<JsonObject> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
                yield future;
            }
        };
    }

    private CompletableFuture<JsonObject> connect(JsonObject params) {
        if (!params.has("address")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required param: address"));
            return future;
        }

        String addressStr = params.get("address").getAsString();
        int port = params.has("port") ? params.get("port").getAsInt() : 25565;
        boolean shouldRefreshSession = params.has("refresh_session")
            && params.get("refresh_session").getAsBoolean();

        // Build full address string
        String fullAddress = addressStr.contains(":") ? addressStr : addressStr + ":" + port;

        // If session refresh requested, do it first, then connect
        if (shouldRefreshSession) {
            return SessionRefreshHelper.refreshSession().thenCompose(refreshResult -> {
                // Proceed with connection regardless of refresh result
                // (user may want to try anyway, or it might still work)
                return doConnect(fullAddress, refreshResult.success());
            });
        }

        return doConnect(fullAddress, false);
    }

    private CompletableFuture<JsonObject> doConnect(String fullAddress, boolean sessionRefreshed) {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();

            // Check if already in a world
            if (client.world != null) {
                result.addProperty("success", false);
                result.addProperty("error", "Already in a world. Disconnect first.");
                return result;
            }

            try {
                ServerAddress serverAddress = ServerAddress.parse(fullAddress);
                ServerInfo serverInfo = new ServerInfo(
                    "CLI Server",
                    serverAddress.getAddress() + ":" + serverAddress.getPort(),
                    ServerInfo.ServerType.OTHER
                );

                // Start connection
                ConnectScreen.connect(
                    client.currentScreen,
                    client,
                    serverAddress,
                    serverInfo,
                    false,
                    null
                );

                result.addProperty("success", true);
                result.addProperty("connecting", true);
                result.addProperty("address", serverAddress.getAddress());
                result.addProperty("port", serverAddress.getPort());
                if (sessionRefreshed) {
                    result.addProperty("session_refreshed", true);
                }
            } catch (Exception e) {
                result.addProperty("success", false);
                result.addProperty("error", "Failed to connect: " + e.getMessage());
            }

            return result;
        });
    }

    private CompletableFuture<JsonObject> disconnect() {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();

            if (client.world == null) {
                result.addProperty("success", false);
                result.addProperty("error", "Not connected to any server or world");
                return result;
            }

            boolean wasMultiplayer = !client.isIntegratedServerRunning();
            String worldName = wasMultiplayer ? "multiplayer" : "singleplayer";

            // Disconnect and return to title screen (1.21.11 requires a Screen parameter)
            client.disconnect(new TitleScreen(), false);

            result.addProperty("success", true);
            result.addProperty("disconnected", true);
            result.addProperty("was_multiplayer", wasMultiplayer);
            result.addProperty("previous_world", worldName);

            return result;
        });
    }

    private CompletableFuture<JsonObject> status() {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();

            result.addProperty("connected", client.world != null);

            if (client.world != null) {
                boolean isMultiplayer = !client.isIntegratedServerRunning();
                result.addProperty("multiplayer", isMultiplayer);

                if (isMultiplayer) {
                    ClientPlayNetworkHandler connection = client.getNetworkHandler();
                    if (connection != null) {
                        ServerInfo serverInfo = client.getCurrentServerEntry();
                        if (serverInfo != null) {
                            result.addProperty("server_name", serverInfo.name);
                            result.addProperty("server_address", serverInfo.address);
                        }

                        // Player count info
                        if (connection.getPlayerList() != null) {
                            result.addProperty("player_count", connection.getPlayerList().size());
                        }
                    }
                } else {
                    result.addProperty("world_type", "singleplayer");
                    if (client.getServer() != null) {
                        result.addProperty("world_name", client.getServer().getSaveProperties().getLevelName());
                    }
                }
            }

            return result;
        });
    }

    private CompletableFuture<JsonObject> connectionError(JsonObject params) {
        boolean clear = params.has("clear") && params.get("clear").getAsBoolean();

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        JsonObject result = new JsonObject();

        String lastError = ConnectionErrorTracker.getLastError();
        long lastErrorTime = ConnectionErrorTracker.getLastErrorTime();
        String lastServerAddress = ConnectionErrorTracker.getLastServerAddress();
        boolean isInvalidSession = ConnectionErrorTracker.isInvalidSession();

        result.addProperty("has_error", lastError != null);

        if (lastError != null) {
            result.addProperty("error", lastError);
            result.addProperty("timestamp", lastErrorTime);
            result.addProperty("recent", ConnectionErrorTracker.hasRecentError());
            result.addProperty("is_invalid_session", isInvalidSession);

            if (lastServerAddress != null) {
                result.addProperty("server_address", lastServerAddress);
            }

            // Provide actionable advice for invalid session errors
            if (isInvalidSession) {
                result.addProperty("suggestion", "Session has expired. Use action 'refresh_session' to attempt refresh, or restart the game.");
            }
        }

        if (clear) {
            ConnectionErrorTracker.clear();
            result.addProperty("cleared", true);
        }

        future.complete(result);
        return future;
    }

    private CompletableFuture<JsonObject> refreshSession() {
        return SessionRefreshHelper.refreshSession().thenApply(refreshResult -> {
            JsonObject result = new JsonObject();
            result.addProperty("success", refreshResult.success());
            result.addProperty("message", refreshResult.message());

            if (refreshResult.username() != null) {
                result.addProperty("username", refreshResult.username());
            }

            // Clear the error tracker if refresh was successful
            if (refreshResult.success()) {
                ConnectionErrorTracker.clear();
            }

            return result;
        });
    }

    private CompletableFuture<JsonObject> sessionInfo() {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        SessionRefreshHelper.SessionInfo info = SessionRefreshHelper.getSessionInfo();
        JsonObject result = new JsonObject();

        result.addProperty("has_session", info.hasSession());

        if (info.hasSession()) {
            result.addProperty("username", info.username());
            result.addProperty("uuid", info.uuid());
            result.addProperty("account_type", info.accountType());
        }

        future.complete(result);
        return future;
    }
}
