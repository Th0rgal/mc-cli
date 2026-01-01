package dev.mccli.mixin;

import dev.mccli.McCliMod;
import dev.mccli.util.ServerResourcePackHandler;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin to intercept server resource pack prompts and auto-accept/reject based on policy.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {

    @Shadow
    public abstract void sendPacket(Packet<?> packet);

    @Inject(method = "onResourcePackSend", at = @At("HEAD"), cancellable = true)
    private void mccli$onResourcePackSend(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
        if (!ServerResourcePackHandler.shouldAutoHandle()) {
            // Let the default behavior handle it (show prompt)
            return;
        }

        UUID packId = packet.id();
        String url = packet.url();
        boolean required = packet.required();

        if (ServerResourcePackHandler.shouldAccept()) {
            McCliMod.LOGGER.info("Auto-accepting server resource pack: {} (required: {})", url, required);
            // Send accepted status - the client will then download the pack
            sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.ACCEPTED));
            // Note: We don't cancel here because we want the pack to actually download
            // The default handler will proceed with downloading after we send ACCEPTED
        } else {
            McCliMod.LOGGER.info("Auto-rejecting server resource pack: {} (required: {})", url, required);
            // Send declined status
            sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.DECLINED));
            // Cancel the default handling since we rejected it
            ci.cancel();
        }
    }
}
