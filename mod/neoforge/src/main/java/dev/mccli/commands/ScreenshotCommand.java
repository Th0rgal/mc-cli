package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.McCliMod;
import dev.mccli.util.IrisHelper;
import dev.mccli.util.MainThreadExecutor;
import dev.mccli.util.WindowFocusManager;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Take a screenshot and save to file.
 *
 * Params:
 * - path: absolute path to save screenshot (required)
 * - clean: hide HUD before capture (default: false)
 * - delay_ms: delay before capture in milliseconds (default: 0)
 * - settle_ms: additional delay after cleanup (default: 200 when clean)
 *
 * Response:
 * - path: saved file path
 * - width: image width
 * - height: image height
 */
public class ScreenshotCommand implements Command {
    @Override
    public String getName() {
        return "screenshot";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        if (!params.has("path")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: path"));
            return future;
        }

        String path = params.get("path").getAsString();
        boolean clean = params.has("clean") && params.get("clean").getAsBoolean();
        int delayMs = params.has("delay_ms") ? params.get("delay_ms").getAsInt() : 0;
        int settleMs = params.has("settle_ms") ? params.get("settle_ms").getAsInt() : (clean ? 200 : 0);

        if (!clean && delayMs == 0) {
            return takeScreenshot(path);
        }

        return takeScreenshotWithOptions(path, clean, delayMs, settleMs);
    }

    private CompletableFuture<JsonObject> takeScreenshotWithOptions(String path, boolean clean, int delayMs, int settleMs) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        new Thread(() -> {
            boolean[] originalState = null;

            try {
                // Initial delay
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }

                // Clean mode: hide HUD and focus window
                if (clean) {
                    originalState = MainThreadExecutor.submit(() -> {
                        Minecraft client = Minecraft.getInstance();
                        boolean[] state = new boolean[] {
                            client.options.hideGui,
                            client.options.pauseOnLostFocus
                        };

                        // Focus window (respects WindowFocusManager setting)
                        // In 1.21.11, use GLFW to get window handle
                        long handle = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
                        WindowFocusManager.showWindow(handle);

                        // Hide HUD and disable pause
                        client.options.hideGui = true;
                        client.options.pauseOnLostFocus = false;

                        // Close any open screen
                        if (client.screen != null) {
                            client.setScreen(null);
                        }

                        return state;
                    }).get();

                    // Wait for render to settle
                    if (settleMs > 0) {
                        Thread.sleep(settleMs);
                    }
                }

                // Take screenshot
                JsonObject result = takeScreenshot(path).get();
                future.complete(result);

            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                // Restore state
                if (clean && originalState != null) {
                    boolean[] state = originalState;
                    try {
                        MainThreadExecutor.submitVoid(() -> {
                            Minecraft client = Minecraft.getInstance();
                            client.options.hideGui = state[0];
                            client.options.pauseOnLostFocus = state[1];
                        }).get();
                    } catch (Exception e) {
                        McCliMod.LOGGER.warn("Failed to restore UI state", e);
                    }
                }
            }
        }, "MC-CLI-Screenshot").start();

        return future;
    }

    private CompletableFuture<JsonObject> takeScreenshot(String path) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        MainThreadExecutor.submitVoid(() -> {
            Minecraft client = Minecraft.getInstance();
            RenderTarget framebuffer = client.getMainRenderTarget();

            // New callback-based API in 1.21.11
            Screenshot.takeScreenshot(framebuffer, image -> {
                if (image == null) {
                    future.completeExceptionally(new RuntimeException("Failed to capture screenshot"));
                    return;
                }

                try {
                    File file = new File(path);
                    File parentDir = file.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    image.writeToFile(file);

                    JsonObject result = new JsonObject();
                    result.addProperty("path", file.getAbsolutePath());
                    result.addProperty("width", image.getWidth());
                    result.addProperty("height", image.getHeight());
                    result.add("metadata", buildMetadata(client));

                    McCliMod.LOGGER.info("Screenshot saved to {}", file.getAbsolutePath());
                    future.complete(result);
                } catch (Exception e) {
                    future.completeExceptionally(new RuntimeException("Failed to process screenshot", e));
                } finally {
                    image.close();
                }
            });
        });

        return future;
    }

    private JsonObject buildMetadata(Minecraft client) {
        JsonObject meta = new JsonObject();
        meta.addProperty("timestamp", Instant.now().toString());

        if (client.player != null) {
            JsonObject player = new JsonObject();
            player.addProperty("x", client.player.getX());
            player.addProperty("y", client.player.getY());
            player.addProperty("z", client.player.getZ());
            player.addProperty("yaw", client.player.getYRot());
            player.addProperty("pitch", client.player.getXRot());
            meta.add("player", player);
        }

        ClientLevel level = client.level;
        if (level != null) {
            meta.addProperty("time", level.getDayTime() % 24000);
            // In 1.21.11, use toString() for dimension key
            meta.addProperty("dimension", level.dimension().toString());
        }

        meta.addProperty("iris_loaded", IrisHelper.isLoaded());
        if (IrisHelper.isLoaded()) {
            JsonObject shader = new JsonObject();
            shader.addProperty("active", IrisHelper.areShadersEnabled());
            shader.addProperty("name", IrisHelper.getCurrentPackName());
            meta.add("shader", shader);
        }

        JsonArray packs = new JsonArray();
        for (String packId : client.getResourcePackRepository().getSelectedIds()) {
            packs.add(packId);
        }
        meta.add("resource_packs", packs);

        return meta;
    }
}
