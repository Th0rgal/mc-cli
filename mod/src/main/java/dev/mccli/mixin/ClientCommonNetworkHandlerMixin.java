package dev.mccli.mixin;

import dev.mccli.McCliMod;
import dev.mccli.util.ServerResourcePackHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;
import java.util.UUID;

/**
 * Mixin to intercept server resource pack prompts and auto-accept/reject based on policy.
 *
 * For auto-accept: Directly trigger the download via ServerResourcePackLoader.addResourcePack()
 *                  to bypass the confirmation screen entirely.
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
        String urlString = packet.url();
        String hash = packet.hash();
        boolean required = packet.required();

        if (ServerResourcePackHandler.shouldAccept()) {
            McCliMod.LOGGER.info("Auto-accepting server resource pack: {} (required: {})", urlString, required);

            try {
                // Parse the URL
                URL url = new URL(urlString);

                // Send ACCEPTED status to server
                sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.ACCEPTED));

                // Directly add the resource pack for download, bypassing the confirmation screen
                MinecraftClient client = MinecraftClient.getInstance();
                ServerResourcePackLoader loader = client.getServerResourcePackLoader();
                loader.addResourcePack(packId, url, hash.isEmpty() ? null : hash);

                McCliMod.LOGGER.info("Resource pack download initiated for: {}", urlString);
            } catch (Exception e) {
                McCliMod.LOGGER.error("Failed to auto-accept resource pack: {}", e.getMessage());
                // Send failed status if we couldn't initiate the download
                sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.FAILED_DOWNLOAD));
            }

            // Cancel the default handling to prevent the confirmation screen
            ci.cancel();
        } else {
            McCliMod.LOGGER.info("Auto-rejecting server resource pack: {} (required: {})", urlString, required);
            // Send declined status
            sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.DECLINED));
            // Cancel the default handling since we rejected it
            ci.cancel();
        }
    }
}
