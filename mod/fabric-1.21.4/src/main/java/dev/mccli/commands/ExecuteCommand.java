package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.concurrent.CompletableFuture;

/**
 * Execute a Minecraft command.
 *
 * Params:
 * - command: string (without leading /)
 *
 * Response:
 * - executed: boolean
 * - command: the command that was executed
 */
public class ExecuteCommand implements Command {
    @Override
    public String getName() {
        return "execute";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        if (!params.has("command")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: command"));
            return future;
        }

        String command = params.get("command").getAsString();
        // Remove leading / if present
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        final String finalCommand = command;

        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;

            if (player == null || player.networkHandler == null) {
                throw new IllegalStateException("Not in game");
            }

            // sendCommand() was renamed to sendChatCommand() in 1.21.11
            player.networkHandler.sendCommand(finalCommand);

            JsonObject result = new JsonObject();
            result.addProperty("executed", true);
            result.addProperty("command", finalCommand);
            return result;
        });
    }
}
