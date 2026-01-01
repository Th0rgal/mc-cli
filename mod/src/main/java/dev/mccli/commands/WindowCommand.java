package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import dev.mccli.util.WindowFocusManager;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

/**
 * Window management command for controlling focus behavior.
 *
 * Actions:
 * - focus_grab: Enable/disable window focus grabbing (for headless operation)
 * - focus: Manually request window focus
 * - close_screen: Close any open GUI screen
 *
 * When focus_grab is disabled, Minecraft will not steal focus from other applications,
 * which is essential for automated/background testing.
 */
public class WindowCommand implements Command {
    @Override
    public String getName() {
        return "window";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "status";

        return switch (action) {
            case "focus_grab" -> handleFocusGrab(params);
            case "focus" -> handleFocus();
            case "close_screen" -> handleCloseScreen();
            case "status" -> handleStatus();
            default -> {
                CompletableFuture<JsonObject> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
                yield future;
            }
        };
    }

    /**
     * Enable or disable window focus grabbing.
     *
     * Params:
     * - enabled: boolean (required)
     *
     * Response:
     * - focus_grab_enabled: current state
     */
    private CompletableFuture<JsonObject> handleFocusGrab(JsonObject params) {
        if (!params.has("enabled")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: enabled"));
            return future;
        }

        boolean enabled = params.get("enabled").getAsBoolean();
        WindowFocusManager.setFocusGrabEnabled(enabled);

        JsonObject response = new JsonObject();
        response.addProperty("focus_grab_enabled", enabled);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Manually request window focus.
     *
     * Response:
     * - focused: boolean (true if focus was requested, false if suppressed)
     */
    private CompletableFuture<JsonObject> handleFocus() {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            long handle = client.getWindow().getHandle();

            GLFW.glfwShowWindow(handle);
            GLFW.glfwFocusWindow(handle);

            JsonObject response = new JsonObject();
            response.addProperty("focused", true);
            return response;
        });
    }

    /**
     * Close any currently open GUI screen.
     *
     * Response:
     * - closed: boolean
     * - screen_type: the type of screen that was closed (if any)
     */
    private CompletableFuture<JsonObject> handleCloseScreen() {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject response = new JsonObject();

            if (client.currentScreen != null) {
                String screenType = client.currentScreen.getClass().getSimpleName();
                client.setScreen(null);
                response.addProperty("closed", true);
                response.addProperty("screen_type", screenType);
            } else {
                response.addProperty("closed", false);
                response.addProperty("reason", "No screen open");
            }

            return response;
        });
    }

    /**
     * Get current window/focus status.
     *
     * Response:
     * - focus_grab_enabled: boolean
     * - screen_open: boolean
     * - screen_type: string (if screen is open)
     */
    private CompletableFuture<JsonObject> handleStatus() {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject response = new JsonObject();

            response.addProperty("focus_grab_enabled", WindowFocusManager.isFocusGrabEnabled());
            response.addProperty("screen_open", client.currentScreen != null);

            if (client.currentScreen != null) {
                response.addProperty("screen_type", client.currentScreen.getClass().getSimpleName());
            }

            return response;
        });
    }
}
