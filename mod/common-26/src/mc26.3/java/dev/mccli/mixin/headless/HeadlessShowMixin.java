package dev.mccli.mixin.headless;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mccli.McCliMod;
import dev.mccli.util.HeadlessMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Headless mode (26.3): vanilla never shows the window after creating it (it is created visible),
 * but NeoForge creates it hidden and shows it in {@code EarlyWindowHandoff.completeWindowHandoff}.
 * Suppress that show while headless. {@code @Pseudo}: the class only exists on NeoForge.
 */
@Pseudo
@Mixin(targets = "net.neoforged.neoforge.client.loading.earlydisplay.EarlyWindowHandoff")
public abstract class HeadlessShowMixin {
    @WrapOperation(method = "completeWindowHandoff",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_ShowWindow(J)Z"),
        require = 0)
    private static boolean mccli$noShowWhileHidden(long window, Operation<Boolean> original) {
        if (HeadlessMode.isHidden()) {
            McCliMod.LOGGER.debug("[headless] Suppressed SDL_ShowWindow during NeoForge window handoff");
            return true;
        }
        return original.call(window);
    }
}
