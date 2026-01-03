package dev.mccli.util;

import dev.mccli.McCliMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Manages window focus behavior for headless/automated operation.
 *
 * When focus grab is disabled:
 * - Minecraft will not request window focus
 * - Screenshots will not force focus the window
 * - Interactions will not steal focus from other applications
 * - Optionally disables pause-on-lost-focus to prevent the pause menu
 *
 * This is essential for automated testing where Minecraft runs in the background.
 */
public class WindowFocusManager {
    private static volatile boolean focusGrabEnabled = true;
    private static volatile boolean pauseOnLostFocusOverride = false;
    private static volatile Boolean originalPauseOnLostFocus = null;

    /**
     * Check if focus grab is enabled.
     */
    public static boolean isFocusGrabEnabled() {
        return focusGrabEnabled;
    }

    /**
     * Check if pause-on-lost-focus override is active.
     */
    public static boolean isPauseOnLostFocusDisabled() {
        return pauseOnLostFocusOverride;
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
     * Enable or disable the pause-on-lost-focus override.
     * When enabled, Minecraft will not pause when the window loses focus.
     *
     * @param disable true to disable pause-on-lost-focus, false to restore original behavior
     */
    public static void setPauseOnLostFocusDisabled(boolean disable) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return;
        }

        if (disable) {
            // Save original value if not already saved
            if (originalPauseOnLostFocus == null) {
                originalPauseOnLostFocus = client.options.pauseOnLostFocus;
            }
            client.options.pauseOnLostFocus = false;
            pauseOnLostFocusOverride = true;
            McCliMod.LOGGER.info("Pause-on-lost-focus disabled for headless operation");
        } else {
            // Restore original value
            if (originalPauseOnLostFocus != null) {
                client.options.pauseOnLostFocus = originalPauseOnLostFocus;
                originalPauseOnLostFocus = null;
            }
            pauseOnLostFocusOverride = false;
            McCliMod.LOGGER.info("Pause-on-lost-focus restored to original setting");
        }
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
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null) {
            return false;
        }
        return requestFocus(client.getWindow().getWindow());
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
