package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.concurrent.CompletableFuture;

/**
 * Teleport player to coordinates.
 *
 * Params:
 * - x: double (required)
 * - y: double (required)
 * - z: double (required)
 *
 * Response:
 * - x, y, z: final position
 */
public class TeleportCommand implements Command {
    @Override
    public String getName() {
        return "teleport";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        if (!params.has("x") || !params.has("y") || !params.has("z")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameters: x, y, z"));
            return future;
        }

        double x = params.get("x").getAsDouble();
        double y = params.get("y").getAsDouble();
        double z = params.get("z").getAsDouble();

        return MainThreadExecutor.submit(() -> {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;

            if (player == null) {
                throw new IllegalStateException("Player not in game");
            }

            player.setPos(x, y, z);

            JsonObject result = new JsonObject();
            result.addProperty("x", player.getX());
            result.addProperty("y", player.getY());
            result.addProperty("z", player.getZ());
            return result;
        });
    }
}
