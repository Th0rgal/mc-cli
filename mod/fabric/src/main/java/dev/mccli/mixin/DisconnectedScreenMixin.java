package dev.mccli.mixin;

import dev.mccli.util.ConnectionErrorTracker;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture connection/disconnection error messages.
 *
 * When Minecraft fails to connect (invalid session, server full, banned, etc.)
 * or gets disconnected, it shows a DisconnectedScreen with the reason.
 * We capture this reason so it can be queried via the CLI.
 */
@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin {

    @Inject(method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;)V", at = @At("RETURN"))
    private void onInit3Params(Screen parent, Text title, Text reason, CallbackInfo ci) {
        // Capture the disconnect reason from 3-param constructor
        ConnectionErrorTracker.recordError(reason, null);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;)V", at = @At("RETURN"))
    private void onInit4Params(Screen parent, Text title, Text reason, Text buttonLabel, CallbackInfo ci) {
        // Capture the disconnect reason from 4-param constructor
        ConnectionErrorTracker.recordError(reason, null);
    }
}
