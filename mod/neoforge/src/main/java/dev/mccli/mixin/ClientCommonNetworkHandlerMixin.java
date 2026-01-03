package dev.mccli.mixin;

import dev.mccli.McCliMod;
import dev.mccli.util.ServerResourcePackHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
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
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonNetworkHandlerMixin {

    @Shadow
    public abstract void send(Packet<?> packet);

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void mccli$onResourcePackSend(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
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

            // Send ACCEPTED status to server immediately (protocol expects ACCEPTED before download)
            send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.ACCEPTED));

            Minecraft client = Minecraft.getInstance();

            // Schedule download on the main thread to ensure proper initialization
            client.execute(() -> {
                try {
                    DownloadedPackSource loader = client.getDownloadedPackSource();

                    // Call pushPack to download and apply the resource pack
                    URL url = new URL(urlString);
                    // The API expects String hash, not HashCode
                    loader.pushPack(packId, url, hash);

                    McCliMod.LOGGER.info("Resource pack added for download: {}", urlString);
                } catch (Exception e) {
                    McCliMod.LOGGER.error("Failed to download resource pack: {}", e.getMessage(), e);
                    send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD));
                }
            });

            // Cancel default handling to prevent confirmation screen
            ci.cancel();
        } else {
            McCliMod.LOGGER.info("Auto-rejecting server resource pack: {} (required: {})", urlString, required);
            // Send declined status
            send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.DECLINED));
            // Cancel the default handling since we rejected it
            ci.cancel();
        }
    }
}
