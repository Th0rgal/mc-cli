package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.McCliMod;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

import java.io.File;
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
                        MinecraftClient client = MinecraftClient.getInstance();
                        boolean[] state = new boolean[] {
                            client.options.hudHidden,
                            client.options.pauseOnLostFocus
                        };

                        // Focus window
                        long handle = client.getWindow().getHandle();
                        GLFW.glfwShowWindow(handle);
                        GLFW.glfwFocusWindow(handle);

                        // Hide HUD and disable pause
                        client.options.hudHidden = true;
                        client.options.pauseOnLostFocus = false;

                        // Close any open screen
                        if (client.currentScreen != null) {
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
                            MinecraftClient client = MinecraftClient.getInstance();
                            client.options.hudHidden = state[0];
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
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Framebuffer framebuffer = client.getFramebuffer();

            NativeImage image = ScreenshotRecorder.takeScreenshot(framebuffer);

            try {
                File file = new File(path);
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                image.writeTo(file);

                JsonObject result = new JsonObject();
                result.addProperty("path", file.getAbsolutePath());
                result.addProperty("width", image.getWidth());
                result.addProperty("height", image.getHeight());

                McCliMod.LOGGER.info("Screenshot saved to {}", file.getAbsolutePath());
                return result;
            } finally {
                image.close();
            }
        });
    }
}
