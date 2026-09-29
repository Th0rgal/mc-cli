package dev.mccli.mixin.headless;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mccli.util.HeadlessMode;
import org.lwjgl.sdl.SDLHints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Headless mode (26.3, SDL3): before {@code SDL_Init}, ask SDL to run as a macOS background app so
 * the process gets no Dock icon and is never activated (SDL otherwise makes it a regular app and
 * calls {@code activateIgnoringOtherApps}). Harmless on other platforms. Has no effect when SDL was
 * already initialized by the NeoForge early loading screen.
 */
@Mixin(RenderSystem.class)
public abstract class HeadlessBackendInitMixin {
    @Inject(method = "initBackendSystem", at = @At("HEAD"), require = 0)
    private static void mccli$backgroundApp(CallbackInfoReturnable<?> cir) {
        if (HeadlessMode.isHidden()) {
            SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MAC_BACKGROUND_APP, "1");
        }
    }
}
