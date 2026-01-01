package dev.mccli.util;

import dev.mccli.McCliMod;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Manages window focus behavior for headless/automated operation.
 *
 * When focus grab is disabled:
 * - Minecraft will not request window focus
 * - Screenshots will not force focus the window
 * - Interactions will not steal focus from other applications
 *
 * This is essential for automated testing where Minecraft runs in the background.
 */
public class WindowFocusManager {
    private static volatile boolean focusGrabEnabled = true;

    /**
     * Check if focus grab is enabled.
     */
    public static boolean isFocusGrabEnabled() {
        return focusGrabEnabled;
    }

    /**
     * Enable or disable window focus grabbing.
     *
     * @param enabled true to allow focus grabs, false to suppress them
     */
    public static void setFocusGrabEnabled(boolean enabled) {
        focusGrabEnabled = enabled;
        McCliMod.LOGGER.info("Window focus grab {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Request window focus, respecting the focus grab setting.
     * Only actually focuses the window if focus grab is enabled.
     *
     * @param windowHandle the GLFW window handle
     * @return true if focus was requested, false if suppressed
     */
    public static boolean requestFocus(long windowHandle) {
        if (!focusGrabEnabled) {
            McCliMod.LOGGER.debug("Focus request suppressed (focus grab disabled)");
            return false;
        }
        GLFW.glfwFocusWindow(windowHandle);
        return true;
    }

    /**
     * Request window focus using the main Minecraft window.
     *
     * @return true if focus was requested, false if suppressed
     */
    public static boolean requestFocus() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) {
            return false;
        }
        return requestFocus(client.getWindow().getHandle());
    }

    /**
     * Show and optionally focus window, respecting focus grab setting.
     *
     * @param windowHandle the GLFW window handle
     */
    public static void showWindow(long windowHandle) {
        GLFW.glfwShowWindow(windowHandle);
        if (focusGrabEnabled) {
            GLFW.glfwFocusWindow(windowHandle);
        }
    }
}
