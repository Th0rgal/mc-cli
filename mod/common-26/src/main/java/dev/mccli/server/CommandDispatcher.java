package dev.mccli.server;

import com.google.gson.JsonObject;
import dev.mccli.McCliMod;
import dev.mccli.commands.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Routes incoming commands to their handlers.
 *
 * Commands are registered at startup and dispatched by name.
 * Each command returns a CompletableFuture to support async execution.
 */
public class CommandDispatcher {
    private final Map<String, Command> commands = new HashMap<>();

    public CommandDispatcher() {
        // Core commands
        register(new StatusCommand());
        register(new TeleportCommand());
        register(new CameraCommand());
        register(new TimeCommand());
        register(new ExecuteCommand());

        // Shader commands
        register(new ShaderCommand());

        // Resource pack commands
        register(new ResourcePackCommand());

        // Chat commands
        register(new ChatCommand());

        // Visual commands
        register(new ScreenshotCommand());

        // Debugging commands
        register(new PerfCommand());
        register(new LogsCommand());

        // Inspection commands
        register(new ItemCommand());
        register(new InventoryCommand());
        register(new BlockCommand());
        register(new EntityCommand());

        // Interaction commands
        register(new InteractCommand());

        // Server connection commands
        register(new ServerCommand());

        // Window management commands
        register(new WindowCommand());

        // World management commands
        register(new WorldCommand());

        McCliMod.LOGGER.info("Registered {} commands", commands.size());
    }

    private void register(Command command) {
        commands.put(command.getName(), command);
    }

    public CompletableFuture<JsonObject> dispatch(String commandName, JsonObject params) {
        Command command = commands.get(commandName);

        if (command == null) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Unknown command: " + commandName));
            return future;
        }

        try {
            return command.execute(params);
        } catch (Exception e) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
