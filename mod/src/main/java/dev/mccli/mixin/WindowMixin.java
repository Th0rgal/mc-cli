package dev.mccli.mixin;

import dev.mccli.McCliMod;
import dev.mccli.util.WindowFocusManager;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept window focus requests from Minecraft.
 * When focus grab is disabled via WindowFocusManager, suppress focus operations.
 */
@Mixin(Window.class)
public class WindowMixin {

    @Shadow @Final private long handle;

    /**
     * Redirect all glfwFocusWindow calls in Window class to go through our manager.
     * This intercepts Minecraft's attempts to focus the window.
     * require = 0 because not all versions may have this call.
     */
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwFocusWindow(J)V"), require = 0)
    private void redirectFocusWindow(long window) {
        if (WindowFocusManager.isFocusGrabEnabled()) {
            GLFW.glfwFocusWindow(window);
        } else {
            McCliMod.LOGGER.debug("Suppressed glfwFocusWindow call from Window class");
        }
    }
}
