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
 * For auto-accept: Cancel the default handler, call acceptAll() then add the pack.
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

            MinecraftClient client = MinecraftClient.getInstance();

            // Schedule on the main thread to ensure proper initialization
            client.execute(() -> {
                try {
                    ServerResourcePackLoader loader = client.getServerResourcePackProvider();

                    // Call acceptAll first to set the acceptance state
                    loader.acceptAll();

                    // Parse and add the resource pack
                    URL url = new URL(urlString);
                    String hashToUse = (hash == null || hash.isEmpty()) ? null : hash;
                    loader.addResourcePack(packId, url, hashToUse);

                    // Send ACCEPTED status to server
                    sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.ACCEPTED));

                    McCliMod.LOGGER.info("Resource pack added for download: {}", urlString);
                } catch (Exception e) {
                    McCliMod.LOGGER.error("Failed to auto-accept resource pack: {}", e.getMessage(), e);
                    sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.FAILED_DOWNLOAD));
                }
            });

            // Cancel default handling to prevent confirmation screen
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
