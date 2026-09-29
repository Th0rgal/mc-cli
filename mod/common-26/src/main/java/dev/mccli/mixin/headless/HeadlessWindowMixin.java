package dev.mccli.mixin.headless;

import com.mojang.blaze3d.platform.Window;
import dev.mccli.util.HeadlessMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Headless mode: make sure the game window is hidden once it exists. The window is normally created
 * hidden already (see the version specific creation mixins); this also covers NeoForge, which hands
 * over its (visible) early loading window.
 */
@Mixin(Window.class)
public abstract class HeadlessWindowMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mccli$hideOnCreate(CallbackInfo ci) {
        HeadlessMode.onWindowCreated(((Window) (Object) this).handle());
    }
}
