package dev.mccli.mixin.headless;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mccli.util.HeadlessMode;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Headless mode (GLFW): 1.21.11 creates the window visible (default {@code GLFW_VISIBLE}); create it
 * hidden and unfocused instead so it never appears on screen.
 */
@Mixin(Window.class)
public abstract class HeadlessCreateMixin {
    @WrapOperation(method = "<init>",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private long mccli$createHidden(int width, int height, CharSequence title, long monitor, long share,
                                    Operation<Long> original) {
        if (HeadlessMode.isHidden()) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
        return original.call(width, height, title, monitor, share);
    }
}
