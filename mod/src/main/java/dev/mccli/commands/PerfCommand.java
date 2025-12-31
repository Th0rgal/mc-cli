package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;

/**
 * Get performance metrics.
 *
 * Response:
 * - fps: current FPS
 * - frame_time_ms: average frame time in milliseconds
 * - memory: {used_mb, max_mb, percent}
 * - chunk_updates: pending chunk updates
 * - entity_count: rendered entity count
 */
public class PerfCommand implements Command {
    @Override
    public String getName() {
        return "perf";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();

            // FPS
            int fps = client.getCurrentFps();
            result.addProperty("fps", fps);
            result.addProperty("frame_time_ms", fps > 0 ? 1000.0 / fps : 0);

            // Memory
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();

            JsonObject memory = new JsonObject();
            memory.addProperty("used_mb", usedMemory / (1024 * 1024));
            memory.addProperty("max_mb", maxMemory / (1024 * 1024));
            memory.addProperty("percent", (double) usedMemory / maxMemory * 100);
            result.add("memory", memory);

            // World info
            if (client.world != null && client.worldRenderer != null) {
                result.addProperty("chunk_updates", client.worldRenderer.getChunkCount());

                if (client.world.getRegularEntityCount() >= 0) {
                    result.addProperty("entity_count", client.world.getRegularEntityCount());
                }
            }

            return result;
        });
    }
}
