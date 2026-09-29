package dev.mccli.mixin.headless;

import com.mojang.blaze3d.platform.GLX;
import dev.mccli.util.HeadlessMode;
import dev.mccli.util.MacosApp;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Headless mode (GLFW): before {@code glfwInit}, disable the Cocoa menu bar so GLFW does not
 * turn the process into a regular macOS app (no Dock icon, no activation). No effect elsewhere.
 */
@Mixin(GLX.class)
public abstract class HeadlessBackendInitMixin {
    @Inject(method = "_initGlfw", at = @At("HEAD"), require = 0)
    private static void mccli$noMenubar(CallbackInfoReturnable<?> cir) {
        if (HeadlessMode.isHidden() && MacosApp.isMacos()) {
            GLFW.glfwInitHint(GLFW.GLFW_COCOA_MENUBAR, GLFW.GLFW_FALSE);
        }
    }
}
