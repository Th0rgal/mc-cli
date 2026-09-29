package dev.mccli.mixin.headless;

import dev.mccli.util.HeadlessMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Headless mode: mute the master volume as seen by the sound engine, without changing the saved
 * option (so options.txt is never written with a zero volume). {@code getSoundVolume} multiplies
 * every category by this raw master value, so all sounds are silenced.
 */
@Mixin(GameOptions.class)
public abstract class HeadlessOptionsMixin {
    @Inject(method = "getCategorySoundVolume", at = @At("HEAD"), cancellable = true)
    private void mccli$muteWhileHeadless(SoundCategory category, CallbackInfoReturnable<Float> cir) {
        if (category == SoundCategory.MASTER && HeadlessMode.isMuted()) {
            cir.setReturnValue(0.0F);
        }
    }
}
