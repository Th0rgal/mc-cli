package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.concurrent.CompletableFuture;

/**
 * Set camera/player view direction.
 *
 * Params:
 * - yaw: float (-180 to 180, horizontal rotation)
 * - pitch: float (-90 to 90, vertical rotation)
 *
 * Response:
 * - yaw, pitch: final rotation
 */
public class CameraCommand implements Command {
    @Override
    public String getName() {
        return "camera";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        if (!params.has("yaw") || !params.has("pitch")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameters: yaw, pitch"));
            return future;
        }

        float yaw = params.get("yaw").getAsFloat();
        float pitch = params.get("pitch").getAsFloat();

        // Clamp values
        pitch = Math.max(-90, Math.min(90, pitch));
        while (yaw > 180) yaw -= 360;
        while (yaw < -180) yaw += 360;

        final float finalYaw = yaw;
        final float finalPitch = pitch;

        return MainThreadExecutor.submit(() -> {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;

            if (player == null) {
                throw new IllegalStateException("Player not in game");
            }

            player.setYRot(finalYaw);
            player.setXRot(finalPitch);

            JsonObject result = new JsonObject();
            result.addProperty("yaw", player.getYRot());
            result.addProperty("pitch", player.getXRot());
            return result;
        });
    }
}
