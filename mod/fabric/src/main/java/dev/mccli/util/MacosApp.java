package dev.mccli.util;

import dev.mccli.McCliMod;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Platform;
import org.lwjgl.system.macosx.ObjCRuntime;

/**
 * macOS application activation policy helpers (no-ops elsewhere).
 *
 * <p>In headless mode the windowing library is asked not to turn the Java process into a regular
 * Dock application (GLFW: {@code GLFW_COCOA_MENUBAR = false}; SDL3: {@code SDL_MAC_BACKGROUND_APP}),
 * so no Dock icon appears and the app is never activated. When the window is shown at runtime the
 * process must become a regular application again, otherwise the window cannot be focused.
 */
public final class MacosApp {
    private static final long NS_APPLICATION_ACTIVATION_POLICY_REGULAR = 0L;
    private static final long NS_APPLICATION_ACTIVATION_POLICY_PROHIBITED = 2L;

    private MacosApp() {}

    public static boolean isMacos() {
        return Platform.get() == Platform.MACOSX;
    }

    /** {@code [NSApp setActivationPolicy:NSApplicationActivationPolicyRegular]; [NSApp activateIgnoringOtherApps:YES]}. Main thread only. */
    public static void makeForegroundApp() {
        setPolicy(NS_APPLICATION_ACTIVATION_POLICY_REGULAR, true);
    }

    /**
     * {@code [NSApp setActivationPolicy:NSApplicationActivationPolicyProhibited]; [NSApp deactivate]}:
     * back to a background process (no Dock icon, not frontmost) after the window was hidden again,
     * handing focus back to the previously active application. Main thread only.
     */
    public static void makeBackgroundApp() {
        setPolicy(NS_APPLICATION_ACTIVATION_POLICY_PROHIBITED, false);
    }

    private static void setPolicy(long policy, boolean activate) {
        if (!isMacos()) {
            return;
        }
        try {
            long objcMsgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
            long nsApplication = ObjCRuntime.objc_getClass("NSApplication");
            long app = JNI.invokePPP(nsApplication, ObjCRuntime.sel_getUid("sharedApplication"), objcMsgSend);
            if (app == 0L) {
                return;
            }
            JNI.invokePPPZ(app, ObjCRuntime.sel_getUid("setActivationPolicy:"), policy, objcMsgSend);
            if (activate) {
                JNI.invokePPPV(app, ObjCRuntime.sel_getUid("activateIgnoringOtherApps:"), 1L, objcMsgSend);
            } else {
                JNI.invokePPV(app, ObjCRuntime.sel_getUid("deactivate"), objcMsgSend);
            }
        } catch (Throwable t) {
            McCliMod.LOGGER.debug("[headless] Could not change macOS activation policy", t);
        }
    }
}
