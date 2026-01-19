package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Get or set world time.
 *
 * Params:
 * - action: "get" | "set" (default: "get")
 * - value: int (0-24000) or named time (for "set")
 *
 * Named times:
 * - sunrise: 0
 * - day: 1000
 * - noon: 6000
 * - sunset: 12000
 * - night: 13000
 * - midnight: 18000
 *
 * Response:
 * - time: current world time
 */
public class TimeCommand implements Command {
    private static final Map<String, Integer> NAMED_TIMES = Map.of(
        "sunrise", 0,
        "day", 1000,
        "noon", 6000,
        "sunset", 12000,
        "night", 13000,
        "midnight", 18000
    );

    @Override
    public String getName() {
        return "time";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "get";

        if (action.equals("set")) {
            if (!params.has("value")) {
                CompletableFuture<JsonObject> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalArgumentException("Missing required parameter: value"));
                return future;
            }

            int time;
            if (params.get("value").isJsonPrimitive() && params.get("value").getAsJsonPrimitive().isString()) {
                String name = params.get("value").getAsString().toLowerCase();
                if (!NAMED_TIMES.containsKey(name)) {
                    CompletableFuture<JsonObject> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalArgumentException(
                        "Unknown time name: " + name + ". Valid: " + String.join(", ", NAMED_TIMES.keySet())));
                    return future;
                }
                time = NAMED_TIMES.get(name);
            } else {
                time = params.get("value").getAsInt();
            }

            return setTime(time);
        } else {
            return getTime();
        }
    }

    private CompletableFuture<JsonObject> getTime() {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientWorld world = client.world;

            if (world == null) {
                throw new IllegalStateException("Not in game");
            }

            JsonObject result = new JsonObject();
            result.addProperty("time", world.getTimeOfDay() % 24000);
            return result;
        });
    }

    private CompletableFuture<JsonObject> setTime(int time) {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;

            if (player == null || player.networkHandler == null) {
                throw new IllegalStateException("Not in game");
            }

            // Use /time set command
            player.networkHandler.sendChatCommand("time set " + time);

            JsonObject result = new JsonObject();
            result.addProperty("time", time);
            result.addProperty("set", true);
            return result;
        });
    }
}
