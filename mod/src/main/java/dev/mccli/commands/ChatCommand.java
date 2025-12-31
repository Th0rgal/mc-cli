package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.util.ChatCapture;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Chat command for sending and receiving chat messages.
 *
 * Params:
 * - action: "send" | "history" | "clear"
 * - message: chat message to send (for "send" action)
 * - limit: max messages to return (for "history", default: 50)
 * - type: filter by type "chat" | "system" (for "history")
 * - filter: regex pattern to filter messages (for "history")
 *
 * Actions:
 * - send: Send a chat message or command
 * - history: Get recent chat messages
 * - clear: Clear chat history buffer
 */
public class ChatCommand implements Command {
    @Override
    public String getName() {
        return "chat";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "history";

        // Validate action
        if (!List.of("send", "history", "clear").contains(action)) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
            return future;
        }

        // Validate 'send' action has required 'message' parameter
        if (action.equals("send") && !params.has("message")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: message"));
            return future;
        }

        return MainThreadExecutor.submit(() -> {
            return switch (action) {
                case "send" -> sendMessage(params.get("message").getAsString());
                case "history" -> getHistory(params);
                case "clear" -> clearHistory();
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            };
        });
    }

    private JsonObject sendMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        if (player == null || player.networkHandler == null) {
            throw new IllegalStateException("Not in game");
        }

        JsonObject result = new JsonObject();

        // Check if it's a command (starts with /)
        if (message.startsWith("/")) {
            // Send as command (without the leading /)
            player.networkHandler.sendCommand(message.substring(1));
            result.addProperty("type", "command");
            result.addProperty("command", message.substring(1));
        } else {
            // Send as chat message
            player.networkHandler.sendChatMessage(message);
            result.addProperty("type", "chat");
            result.addProperty("message", message);
        }

        result.addProperty("sent", true);
        return result;
    }

    private JsonObject getHistory(JsonObject params) {
        int limit = params.has("limit") ? params.get("limit").getAsInt() : 50;
        String type = params.has("type") ? params.get("type").getAsString() : null;
        String filter = params.has("filter") ? params.get("filter").getAsString() : null;

        List<ChatCapture.ChatMessage> messages = ChatCapture.getMessages(limit, type, filter);

        JsonObject result = new JsonObject();
        JsonArray messagesArray = new JsonArray();

        for (ChatCapture.ChatMessage msg : messages) {
            JsonObject msgJson = new JsonObject();
            msgJson.addProperty("timestamp", msg.timestamp().toString());
            msgJson.addProperty("type", msg.type());
            if (msg.sender() != null) {
                msgJson.addProperty("sender", msg.sender());
            }
            msgJson.addProperty("content", msg.content());
            messagesArray.add(msgJson);
        }

        result.add("messages", messagesArray);
        result.addProperty("count", messagesArray.size());
        result.addProperty("total_buffered", ChatCapture.size());
        return result;
    }

    private JsonObject clearHistory() {
        int cleared = ChatCapture.size();
        ChatCapture.clear();

        JsonObject result = new JsonObject();
        result.addProperty("cleared", cleared);
        return result;
    }
}
