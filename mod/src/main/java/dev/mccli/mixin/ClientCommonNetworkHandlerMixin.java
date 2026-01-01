package dev.mccli.mixin;

import dev.mccli.McCliMod;
import dev.mccli.util.ServerResourcePackHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerResourcePackLoader;
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
 *
 * For auto-accept: Set the loader to accept mode so the default handler downloads without prompting.
 * For auto-reject: Send decline status and cancel the handler.
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
            // Set the loader to accept all packs, then let the default handler proceed
            // The default handler will check acceptAll and download without showing prompt
            MinecraftClient client = MinecraftClient.getInstance();
            ServerResourcePackLoader loader = client.getServerResourcePackLoader();
            loader.acceptAll();
            // Do NOT cancel - let the default handler download the pack
        } else {
            McCliMod.LOGGER.info("Auto-rejecting server resource pack: {} (required: {})", url, required);
            // Send declined status
            sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.DECLINED));
            // Cancel the default handling since we rejected it
            ci.cancel();
        }
    }
}
