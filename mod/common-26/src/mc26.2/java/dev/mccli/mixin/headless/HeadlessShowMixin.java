package dev.mccli.mixin.headless;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mccli.McCliMod;
import dev.mccli.util.HeadlessMode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Headless mode (26.2, GLFW): {@code Minecraft.<init>} shows the (hidden) window with
 * {@code glfwShowWindow} once it is set up; skip that while headless.
 */
@Mixin(Minecraft.class)
public abstract class HeadlessShowMixin {
    @WrapOperation(method = "<init>",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"),
        require = 0)
    private void mccli$noShowWhileHidden(long window, Operation<Void> original) {
        if (HeadlessMode.isHidden()) {
            McCliMod.LOGGER.debug("[headless] Suppressed glfwShowWindow during startup");
            return;
        }
        original.call(window);
    }
}
