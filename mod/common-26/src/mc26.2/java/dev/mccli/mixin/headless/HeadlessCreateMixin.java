package dev.mccli.mixin.headless;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import dev.mccli.util.HeadlessMode;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Headless mode (26.2, GLFW): vanilla already creates the window with {@code GLFW_VISIBLE = false}
 * and shows it later (see {@link HeadlessShowMixin}); additionally make sure it is not focused when
 * created or shown.
 */
@Mixin(Window.class)
public abstract class HeadlessCreateMixin {
    @WrapOperation(method = "createGlfwWindow",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"),
        require = 0)
    private static long mccli$createHidden(int width, int height, CharSequence title, long monitor, long share,
                                           Operation<Long> original) {
        if (HeadlessMode.isHidden()) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
        return original.call(width, height, title, monitor, share);
    }
}
