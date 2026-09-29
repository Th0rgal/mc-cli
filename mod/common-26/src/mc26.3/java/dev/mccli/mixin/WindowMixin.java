package dev.mccli.mixin;

import dev.mccli.McCliMod;
import dev.mccli.util.WindowFocusManager;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin to intercept window focus requests from Minecraft.
 * When focus grab is disabled via WindowFocusManager, suppress focus operations.
 */
@Mixin(Window.class)
public class WindowMixin {

    /**
     * Redirect all SDL_RaiseWindow calls (Minecraft 26.3 uses SDL3 instead of GLFW) in Window class to go through our manager.
     * This intercepts Minecraft's attempts to focus the window.
     * require = 0 because not all versions may have this call.
     */
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_RaiseWindow(J)Z"), require = 0)
    private boolean redirectFocusWindow(long window) {
        if (WindowFocusManager.isFocusGrabEnabled()) {
            return SDLVideo.SDL_RaiseWindow(window);
        } else {
            McCliMod.LOGGER.debug("Suppressed SDL_RaiseWindow call from Window class");
            return false;
        }
    }
}
