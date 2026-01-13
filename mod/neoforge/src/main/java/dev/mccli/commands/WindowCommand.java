package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import dev.mccli.util.WindowFocusManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

/**
 * Window management command for controlling focus behavior.
 *
 * Actions:
 * - focus_grab: Enable/disable window focus grabbing (for headless operation)
 * - pause_on_lost_focus: Enable/disable pause menu when window loses focus
 * - focus: Manually request window focus
 * - close_screen: Close any open GUI screen
 *
 * When focus_grab is disabled, Minecraft will not steal focus from other applications,
 * which is essential for automated/background testing.
 *
 * When pause_on_lost_focus is disabled, the pause menu won't appear when the window
 * loses focus, allowing screenshots and commands to work in the background.
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
            case "pause_on_lost_focus" -> handlePauseOnLostFocus(params);
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
     * Enable or disable pause-on-lost-focus behavior.
     * When disabled, the pause menu won't appear when the window loses focus.
     *
     * Params:
     * - enabled: boolean (required) - true to allow pause menu, false to disable it
     *
     * Response:
     * - pause_on_lost_focus_enabled: current state
     */
    private CompletableFuture<JsonObject> handlePauseOnLostFocus(JsonObject params) {
        if (!params.has("enabled")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: enabled"));
            return future;
        }

        boolean enabled = params.get("enabled").getAsBoolean();
        // Note: setPauseOnLostFocusDisabled takes "disable" param, so we invert
        WindowFocusManager.setPauseOnLostFocusDisabled(!enabled);

        JsonObject response = new JsonObject();
        response.addProperty("pause_on_lost_focus_enabled", enabled);
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
            // In 1.21.11, use GLFW to get window handle
            long handle = GLFW.glfwGetCurrentContext();

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
            Minecraft client = Minecraft.getInstance();
            JsonObject response = new JsonObject();

            if (client.screen != null) {
                String screenType = client.screen.getClass().getSimpleName();
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
     * - pause_on_lost_focus_enabled: boolean
     * - screen_open: boolean
     * - screen_type: string (if screen is open)
     */
    private CompletableFuture<JsonObject> handleStatus() {
        return MainThreadExecutor.submit(() -> {
            Minecraft client = Minecraft.getInstance();
            JsonObject response = new JsonObject();

            response.addProperty("focus_grab_enabled", WindowFocusManager.isFocusGrabEnabled());
            response.addProperty("pause_on_lost_focus_enabled", !WindowFocusManager.isPauseOnLostFocusDisabled());
            response.addProperty("screen_open", client.screen != null);

            if (client.screen != null) {
                response.addProperty("screen_type", client.screen.getClass().getSimpleName());
            }

            return response;
        });
    }
}
