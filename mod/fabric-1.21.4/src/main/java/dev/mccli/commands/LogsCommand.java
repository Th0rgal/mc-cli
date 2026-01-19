package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.util.LogCapture;
import dev.mccli.util.MainThreadExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Get recent game logs.
 *
 * Params:
 * - level: "error" | "warn" | "info" | "debug" (default: "info")
 * - limit: max number of entries (default: 50)
 * - filter: regex pattern to filter messages (optional)
 * - clear: clear captured logs after returning (default: false)
 *
 * Response:
 * - logs: array of {timestamp, level, logger, message}
 * - count: number of log entries
 */
public class LogsCommand implements Command {
    @Override
    public String getName() {
        return "logs";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String level = params.has("level") ? params.get("level").getAsString() : "info";
        int limit = params.has("limit") ? params.get("limit").getAsInt() : 50;
        String filter = params.has("filter") ? params.get("filter").getAsString() : null;
        boolean clear = params.has("clear") && params.get("clear").getAsBoolean();

        return MainThreadExecutor.submit(() -> {
            List<LogCapture.LogEntry> entries = LogCapture.getRecentLogs(level, limit, filter);

            JsonArray logsArray = new JsonArray();
            for (LogCapture.LogEntry entry : entries) {
                JsonObject logJson = new JsonObject();
                logJson.addProperty("timestamp", entry.timestamp());
                logJson.addProperty("level", entry.level());
                logJson.addProperty("logger", entry.logger());
                logJson.addProperty("message", entry.message());
                logsArray.add(logJson);
            }

            if (clear) {
                LogCapture.clear();
            }

            JsonObject result = new JsonObject();
            result.add("logs", logsArray);
            result.addProperty("count", logsArray.size());
            return result;
        });
    }
}
