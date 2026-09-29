package dev.mccli.mixin;

import dev.mccli.McCliMod;
import dev.mccli.util.WindowFocusManager;
import net.minecraft.client.MouseHandler;
import org.lwjgl.sdl.SDLVideo;
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
     * Redirect SDL_RaiseWindow calls (26.x uses SDL3 instead of GLFW) from Mouse class.
     * require = 0 because not all versions may have this call.
     */
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_RaiseWindow(J)Z"), require = 0)
    private boolean redirectFocusWindow(long window) {
        if (WindowFocusManager.isFocusGrabEnabled()) {
            return SDLVideo.SDL_RaiseWindow(window);
        } else {
            McCliMod.LOGGER.debug("Suppressed SDL_RaiseWindow call from Mouse class");
            return false;
        }
    }
}
