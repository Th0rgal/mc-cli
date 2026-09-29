package dev.mccli.mixin.headless;

import dev.mccli.util.HeadlessMode;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Headless mode: make sure the game window is hidden once it exists (safety net in case another
 * mod created or showed it despite {@link HeadlessCreateMixin}).
 */
@Mixin(Window.class)
public abstract class HeadlessWindowMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mccli$hideOnCreate(CallbackInfo ci) {
        HeadlessMode.onWindowCreated(((Window) (Object) this).getHandle());
    }
}
