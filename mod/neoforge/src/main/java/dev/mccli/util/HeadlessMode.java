package dev.mccli.util;

import dev.mccli.McCliMod;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Headless mode: the game window is created hidden and never shown, focused or raised, while the
 * game keeps rendering normally so that screenshots and every command keep working.
 *
 * <p>Enabled at startup with {@code -Dmccli.headless=true} or {@code MCCLI_HEADLESS=1|true}.
 * At runtime {@code window hide} / {@code window show} toggle the same state.
 *
 * <p>While hidden:
 * <ul>
 *   <li>the window stays invisible (creation-time hidden flag + suppressed show/focus calls, see the mixins)</li>
 *   <li>pause-on-lost-focus is ignored (the saved option is not modified)</li>
 *   <li>the mouse is never grabbed</li>
 *   <li>frames keep rendering at {@code mccli.headless.fps} (default 30) regardless of the
 *       inactivity / minimized framerate throttles</li>
 *   <li>master volume is muted (without touching options.txt)</li>
 * </ul>
 *
 * <p>Optional {@code mccli.headless.width} / {@code mccli.headless.height} (default 1280x720) set the
 * framebuffer size used for rendering and screenshots while hidden.
 *
 * <p>Port of the Minecraft 26.x implementation (mod/common-26) to NeoForge 1.21.11.
 */
public final class HeadlessMode {
    public static final String PROPERTY = "mccli.headless";
    public static final String ENV = "MCCLI_HEADLESS";

    /** Whether headless mode was requested at startup. */
    private static final boolean REQUESTED = detect();
    private static final int FPS = intProperty("mccli.headless.fps", 30, 1, 1000);
    private static final int WIDTH = intProperty("mccli.headless.width", 1280, 64, 16384);
    private static final int HEIGHT = intProperty("mccli.headless.height", 720, 64, 16384);

    private static volatile boolean hidden = REQUESTED;
    private static volatile boolean muted = REQUESTED;
    private static volatile boolean sizeApplied = false;
    private static volatile boolean announced = false;

    private HeadlessMode() {}

    private static boolean detect() {
        return truthy(System.getProperty(PROPERTY)) || truthy(System.getenv(ENV));
    }

    private static boolean truthy(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("1") || v.equals("true") || v.equals("yes") || v.equals("on");
    }

    private static int intProperty(String key, int def, int min, int max) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** True if headless mode was requested at startup (system property or environment variable). */
    public static boolean isRequested() {
        return REQUESTED;
    }

    /** True while the window is hidden by headless mode (startup or {@code window hide}). */
    public static boolean isHidden() {
        return hidden;
    }

    /** True while master volume is muted by headless mode. */
    public static boolean isMuted() {
        return muted;
    }

    public static int fps() {
        return FPS;
    }

    public static int width() {
        return WIDTH;
    }

    public static int height() {
        return HEIGHT;
    }

    /**
     * Called at the end of the {@code Window} constructor. Hides the window if it is visible
     * (NeoForge hands over its already visible early loading window).
     */
    public static void onWindowCreated(long handle) {
        if (!hidden || handle == 0L) {
            return;
        }
        boolean wasVisible = isVisible(handle);
        if (wasVisible) {
            McCliMod.LOGGER.warn("[headless] Game window was already visible (early loading screen?); hiding it. "
                + "On NeoForge set earlyWindowControl = false in config/fml.toml to avoid this.");
        }
        GLFW.glfwHideWindow(handle);
        if (wasVisible) {
            // The early loading screen initialized GLFW before our GLX hook, so on macOS the process
            // became a regular (Dock) app; turn it back into a background process.
            MacosApp.makeBackgroundApp();
        }
    }

    /** Called every client tick (main thread). */
    public static void tick(Minecraft client) {
        if (!announced) {
            announced = true;
            if (REQUESTED) {
                McCliMod.LOGGER.info("[headless] Headless mode enabled: window hidden, {} fps, {}x{} framebuffer",
                    FPS, WIDTH, HEIGHT);
            }
            if (hidden) {
                // Startup is finished: make sure nothing (e.g. the NeoForge early window handoff) left
                // the window visible or the process as a regular macOS app with a Dock icon.
                long handle = handle(client);
                if (handle != 0L && isVisible(handle)) {
                    McCliMod.LOGGER.warn("[headless] Game window became visible during startup; hiding it");
                    GLFW.glfwHideWindow(handle);
                }
                MacosApp.makeBackgroundApp();
            }
        }
        if (hidden && !sizeApplied) {
            sizeApplied = true;
            applyFramebufferSize(client);
        }
    }

    /**
     * Resize the (hidden) window so that its framebuffer is WIDTH x HEIGHT, taking the display
     * pixel density into account (e.g. a 640x360 window on a 2x Retina display).
     */
    private static void applyFramebufferSize(Minecraft client) {
        Window window = client.getWindow();
        if (window == null || window.isFullscreen()) {
            return;
        }
        int screenW = Math.max(1, window.getScreenWidth());
        int fbW = Math.max(1, window.getWidth());
        double scale = Math.max(1.0, (double) fbW / screenW);
        int targetW = (int) Math.round(WIDTH / scale);
        int targetH = (int) Math.round(HEIGHT / scale);
        if (targetW != screenW || targetH != window.getScreenHeight()) {
            GLFW.glfwSetWindowSize(window.handle(), targetW, targetH);
        }
    }

    private static long handle(Minecraft client) {
        return client.getWindow() == null ? 0L : client.getWindow().handle();
    }

    /** Hide the window and enter headless mode at runtime. Must run on the main thread. */
    public static void hide(Minecraft client) {
        long handle = handle(client);
        if (client.mouseHandler != null) {
            client.mouseHandler.releaseMouse();
        }
        hidden = true;
        WindowFocusManager.setFocusGrabEnabled(false);
        setMuted(client, true);
        if (handle != 0L) {
            GLFW.glfwHideWindow(handle);
        }
        MacosApp.makeBackgroundApp();
        McCliMod.LOGGER.info("[headless] Window hidden");
    }

    /**
     * Show the window and leave headless mode: restores visibility, default focus handling
     * (focus grab enabled, pause-on-lost-focus honoured again) and sound. Must run on the main thread.
     */
    public static void show(Minecraft client) {
        long handle = handle(client);
        hidden = false;
        WindowFocusManager.setFocusGrabEnabled(true);
        setMuted(client, false);
        if (handle != 0L) {
            MacosApp.makeForegroundApp();
            GLFW.glfwShowWindow(handle);
            GLFW.glfwFocusWindow(handle);
        }
        McCliMod.LOGGER.info("[headless] Window shown");
    }

    private static void setMuted(Minecraft client, boolean value) {
        muted = value;
        if (client.getSoundManager() != null) {
            client.getSoundManager().refreshCategoryVolume(SoundSource.MASTER);
        }
    }

    private static boolean isVisible(long handle) {
        return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE;
    }

    /** Current OS-level visibility of the game window. */
    public static boolean isWindowVisible(Minecraft client) {
        long handle = handle(client);
        return handle != 0L && isVisible(handle);
    }
}
