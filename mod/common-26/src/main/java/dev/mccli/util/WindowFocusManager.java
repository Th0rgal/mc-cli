package dev.mccli.util;

import dev.mccli.McCliMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;

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
     * @param windowHandle the SDL window handle ({@code Window.handle()})
     * @return true if focus was requested, false if suppressed
     */
    public static boolean requestFocus(long windowHandle) {
        if (!focusGrabEnabled) {
            McCliMod.LOGGER.debug("Focus request suppressed (focus grab disabled)");
            return false;
        }
        SDLVideo.SDL_RaiseWindow(windowHandle);
        return true;
    }

    /**
     * Request window focus using the main Minecraft window.
     *
     * @return true if focus was requested, false if suppressed
     */
    public static boolean requestFocus() {
        Minecraft client = Minecraft.getInstance();
        long handle = ClientCompat.windowHandle(client);
        if (handle == 0L) {
            return false;
        }
        // 26.x uses SDL3 windows; Window.handle() is the SDL_Window pointer
        return requestFocus(handle);
    }

    /**
     * Show and optionally focus window, respecting focus grab setting.
     *
     * @param windowHandle the SDL window handle ({@code Window.handle()})
     */
    public static void showWindow(long windowHandle) {
        SDLVideo.SDL_ShowWindow(windowHandle);
        if (focusGrabEnabled) {
            SDLVideo.SDL_RaiseWindow(windowHandle);
        }
    }
}
