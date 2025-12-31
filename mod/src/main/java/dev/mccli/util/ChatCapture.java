package dev.mccli.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

/**
 * Captures and stores recent chat messages for retrieval.
 *
 * Thread-safe ring buffer that stores the most recent messages.
 * Used by ChatCommand to return chat history to LLM agents.
 */
public class ChatCapture {
    private static final int MAX_MESSAGES = 500;
    private static final Deque<ChatMessage> messages = new ConcurrentLinkedDeque<>();

    public record ChatMessage(
        Instant timestamp,
        String type,      // "chat", "system", "game_info", "action_bar"
        String sender,    // Player name or null for system messages
        String content    // Raw message content
    ) {}

    /**
     * Add a chat message to the buffer.
     */
    public static void addMessage(String type, String sender, String content) {
        messages.addLast(new ChatMessage(Instant.now(), type, sender, content));

        // Trim old messages
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
    }

    /**
     * Get recent messages matching criteria.
     *
     * @param limit Maximum number of messages to return
     * @param type Filter by message type (null for all)
     * @param pattern Regex pattern to filter content (null for all)
     * @return List of matching messages, newest first
     */
    public static List<ChatMessage> getMessages(int limit, String type, String pattern) {
        List<ChatMessage> result = new ArrayList<>();
        Pattern regex = pattern != null ? Pattern.compile(pattern, Pattern.CASE_INSENSITIVE) : null;

        // Iterate in reverse (newest first)
        var iter = messages.descendingIterator();
        while (iter.hasNext() && result.size() < limit) {
            ChatMessage msg = iter.next();

            // Filter by type
            if (type != null && !msg.type().equals(type)) {
                continue;
            }

            // Filter by pattern
            if (regex != null && !regex.matcher(msg.content()).find()) {
                continue;
            }

            result.add(msg);
        }

        return result;
    }

    /**
     * Clear all stored messages.
     */
    public static void clear() {
        messages.clear();
    }

    /**
     * Get the number of stored messages.
     */
    public static int size() {
        return messages.size();
    }
}
