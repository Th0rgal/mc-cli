package dev.mccli.mixin;

import dev.mccli.util.ChatCapture;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
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
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"))
    private void mccli$captureMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        String content = message.getString();

        // Determine message type based on indicator
        String type;
        String sender = null;

        if (indicator == null) {
            type = "system";
        } else if (indicator == MessageIndicator.system()) {
            type = "system";
        } else if (indicator == MessageIndicator.singlePlayer()) {
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
