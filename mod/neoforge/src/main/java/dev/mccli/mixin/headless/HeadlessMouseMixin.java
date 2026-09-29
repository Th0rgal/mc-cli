package dev.mccli.mixin.headless;

import dev.mccli.util.HeadlessMode;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Headless mode: never grab (capture/hide) the OS mouse cursor. */
@Mixin(MouseHandler.class)
public abstract class HeadlessMouseMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void mccli$noGrabWhileHidden(CallbackInfo ci) {
        if (HeadlessMode.isHidden()) {
            ci.cancel();
        }
    }
}
