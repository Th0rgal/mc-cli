package dev.mccli.mixin.headless;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.mccli.util.HeadlessMode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Headless mode: ignore "pause on lost focus" while hidden (the option itself is left untouched).
 */
@Mixin(Minecraft.class)
public abstract class HeadlessPauseMixin {
    @ModifyExpressionValue(method = "pauseIfInactive",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;pauseOnLostFocus:Z"))
    private boolean mccli$noPauseWhileHidden(boolean original) {
        return original && !HeadlessMode.isHidden();
    }
}
