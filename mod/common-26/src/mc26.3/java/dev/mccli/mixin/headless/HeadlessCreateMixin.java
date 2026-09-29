package dev.mccli.mixin.headless;

import com.mojang.blaze3d.platform.Window;
import dev.mccli.util.HeadlessMode;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Headless mode (26.3, SDL3): create the game window with {@code SDL_WINDOW_HIDDEN} so it never
 * appears. Vanilla passes {@code RESIZABLE | HIGH_PIXEL_DENSITY} (visible) to
 * {@code GpuBackend.createWindow} from {@code Window.createWindow}.
 */
@Mixin(Window.class)
public abstract class HeadlessCreateMixin {
    @ModifyArg(method = "createWindow",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"),
        index = 3, require = 0)
    private long mccli$createHidden(long flags) {
        return HeadlessMode.isHidden() ? flags | SDLVideo.SDL_WINDOW_HIDDEN : flags;
    }
}
