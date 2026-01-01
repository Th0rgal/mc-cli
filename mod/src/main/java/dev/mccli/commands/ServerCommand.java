package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import dev.mccli.util.ServerResourcePackHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.resource.server.ServerResourcePackLoader;

import java.util.concurrent.CompletableFuture;

/**
 * Server connection management.
 *
 * Actions:
 * - connect: Connect to a multiplayer server
 *   Params: address (required), port (optional, default 25565),
 *           resourcepack_policy (optional: "prompt", "accept", "reject")
 * - disconnect: Disconnect from current server/world
 * - status: Get current server connection info
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
        String resourcepackPolicy = params.has("resourcepack_policy")
            ? params.get("resourcepack_policy").getAsString()
            : "prompt";

        // Build full address string
        String fullAddress = addressStr.contains(":") ? addressStr : addressStr + ":" + port;

        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();

            // Check if already in a world
            if (client.world != null) {
                result.addProperty("success", false);
                result.addProperty("error", "Already in a world. Disconnect first.");
                return result;
            }

            // Set resource pack policy before connecting
            ServerResourcePackHandler.setPolicy(resourcepackPolicy);

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
                result.addProperty("resourcepack_policy", resourcepackPolicy);
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

            // Reset resource pack policy and loader state
            ServerResourcePackHandler.reset();
            // Also reset the loader's acceptAll state to prevent it from persisting
            ServerResourcePackLoader loader = client.getServerResourcePackLoader();
            loader.onServerDisconnect();

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
                    ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
                    if (networkHandler != null) {
                        ServerInfo serverInfo = networkHandler.getServerInfo();
                        if (serverInfo != null) {
                            result.addProperty("server_name", serverInfo.name);
                            result.addProperty("server_address", serverInfo.address);
                        }

                        // Player count info
                        if (networkHandler.getPlayerList() != null) {
                            result.addProperty("player_count", networkHandler.getPlayerList().size());
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
}
