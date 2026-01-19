package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.IrisHelper;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import java.util.concurrent.CompletableFuture;

/**
 * Get current game state.
 *
 * Response includes:
 * - in_game: boolean
 * - world_type: "singleplayer" | "multiplayer"
 * - player: {x, y, z, yaw, pitch}
 * - time: world time (0-24000)
 * - dimension: dimension identifier
 * - iris_loaded: boolean
 * - shader: {active, name} (if Iris loaded)
 */
public class StatusCommand implements Command {
    @Override
    public String getName() {
        return "status";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();

            boolean inGame = client.world != null && client.player != null;
            result.addProperty("in_game", inGame);

            if (inGame) {
                ClientPlayerEntity player = client.player;
                ClientWorld world = client.world;

                // World type
                result.addProperty("world_type",
                    client.isIntegratedServerRunning() ? "singleplayer" : "multiplayer");

                // Player position and rotation
                JsonObject playerInfo = new JsonObject();
                playerInfo.addProperty("x", player.getX());
                playerInfo.addProperty("y", player.getY());
                playerInfo.addProperty("z", player.getZ());
                playerInfo.addProperty("yaw", player.getYaw());
                playerInfo.addProperty("pitch", player.getPitch());
                result.add("player", playerInfo);

                // World time
                result.addProperty("time", world.getTimeOfDay() % 24000);

                // Dimension
                result.addProperty("dimension", world.getRegistryKey().getValue().toString());

                // Iris/shader info
                result.addProperty("iris_loaded", IrisHelper.isLoaded());
                if (IrisHelper.isLoaded()) {
                    JsonObject shaderInfo = new JsonObject();
                    shaderInfo.addProperty("active", IrisHelper.areShadersEnabled());
                    shaderInfo.addProperty("name", IrisHelper.getCurrentPackName());
                    result.add("shader", shaderInfo);
                }
            }

            return result;
        });
    }
}
