package dev.mccli.commands;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for all MC-CLI commands.
 *
 * Commands receive parameters as JsonObject and return results asynchronously.
 * The CompletableFuture allows commands to defer execution to the main thread
 * via MainThreadExecutor while keeping the socket handler non-blocking.
 */
public interface Command {
    /**
     * @return The command name (used for routing)
     */
    String getName();

    /**
     * Execute the command with given parameters.
     *
     * @param params Command parameters from the request
     * @return Future that completes with the result data
     */
    CompletableFuture<JsonObject> execute(JsonObject params);
}
