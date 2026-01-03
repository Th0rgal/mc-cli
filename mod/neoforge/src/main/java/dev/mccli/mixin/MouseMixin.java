package dev.mccli.mixin;

import dev.mccli.McCliMod;
import dev.mccli.util.WindowFocusManager;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin to intercept window focus requests from Mouse class.
 * The Mouse class can trigger focus grabs when locking/unlocking cursor.
 */
@Mixin(MouseHandler.class)
public class MouseMixin {

    /**
     * Redirect glfwFocusWindow calls from Mouse class.
     * require = 0 because not all versions may have this call.
     */
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwFocusWindow(J)V"), require = 0)
    private void redirectFocusWindow(long window) {
        if (WindowFocusManager.isFocusGrabEnabled()) {
            GLFW.glfwFocusWindow(window);
        } else {
            McCliMod.LOGGER.debug("Suppressed glfwFocusWindow call from Mouse class");
        }
    }
}
