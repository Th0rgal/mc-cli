package dev.mccli.platform;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

/**
 * Minecraft 26.2 specifics (compiled only into the 26.2 jars; see common-26/src/mc26.3 for 26.3).
 *
 * 26.2 still uses GLFW: {@code Window.handle()} is a GLFW window handle.
 * {@code LocalPlayer.swing(hand)} animates the arm and sends the swing packet.
 */
public final class Platform {
    private Platform() {}

    /** Raise/focus the window. */
    public static boolean raiseWindow(long windowHandle) {
        GLFW.glfwFocusWindow(windowHandle);
        return true;
    }

    /** Show the window (un-hide). */
    public static void showWindow(long windowHandle) {
        GLFW.glfwShowWindow(windowHandle);
    }

    /** Swing the arm for a use/right-click action, as vanilla does. */
    public static void swingUse(LocalPlayer player, InteractionHand hand) {
        player.swing(hand);
    }

    /** Swing the main hand for an attack/left-click action, as vanilla does. */
    public static void swingAttack(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND);
    }
}
