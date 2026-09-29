package dev.mccli.mixin.headless;

import dev.mccli.util.HeadlessMode;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Headless mode: a hidden window never receives input and may be reported as iconified/unfocused,
 * so vanilla would throttle rendering to 10-30 fps (inactivity / AFK / minimized limits).
 * Render at a fixed {@code mccli.headless.fps} instead.
 */
@Mixin(InactivityFpsLimiter.class)
public abstract class HeadlessFramerateMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void mccli$headlessFramerate(CallbackInfoReturnable<Integer> cir) {
        if (HeadlessMode.isHidden()) {
            cir.setReturnValue(HeadlessMode.fps());
        }
    }
}
