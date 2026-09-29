package dev.mccli.mixin;

import dev.mccli.util.ChatCapture;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture chat messages as they are displayed.
 *
 * This captures all messages that go through the chat HUD, including:
 * - Player chat messages
 * - System messages
 * - Server announcements
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

    // 26.x: all chat HUD messages funnel through the private
    // addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag).
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"))
    private void mccli$captureMessage(Component message, MessageSignature signatureData, GuiMessageSource source, GuiMessageTag indicator, CallbackInfo ci) {
        String content = message.getString();

        // Determine message type from the message source (26.x tells us explicitly)
        String type;
        String sender = null;

        if (source != GuiMessageSource.PLAYER) {
            type = "system";
        } else {
            // Chat message - try to extract sender from content
            type = "chat";
            // Common format: <PlayerName> message or [PlayerName] message
            if (content.startsWith("<") && content.contains(">")) {
                int endIdx = content.indexOf(">");
                sender = content.substring(1, endIdx);
            } else if (content.startsWith("[") && content.contains("]")) {
                int endIdx = content.indexOf("]");
                sender = content.substring(1, endIdx);
            }
        }

        ChatCapture.addMessage(type, sender, content);
    }
}
