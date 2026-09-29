package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.McCliMod;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.level.storage.LevelSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * World management command for singleplayer worlds.
 *
 * Actions:
 * - list: List all available singleplayer worlds
 * - create: Open create world screen (optionally with preset name)
 * - load: Load an existing world
 * - delete: Delete a world
 *
 * This enables automated testing without manual GUI interaction.
 */
public class WorldCommand implements Command {
    @Override
    public String getName() {
        return "world";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "list";

        return switch (action) {
            case "list" -> listWorlds();
            case "create" -> createWorld(params);
            case "load" -> loadWorld(params);
            case "delete" -> deleteWorld(params);
            default -> {
                CompletableFuture<JsonObject> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
                yield future;
            }
        };
    }

    /**
     * List all available singleplayer worlds.
     *
     * Response:
     * - worlds: array of world info objects
     * - count: number of worlds
     */
    private CompletableFuture<JsonObject> listWorlds() {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();
            JsonArray worldsArray = new JsonArray();

            try {
                LevelStorage levelSource = client.getLevelStorage();
                LevelStorage.LevelList candidates = levelSource.getLevelList();
                List<LevelSummary> summaries = levelSource.loadSummaries(candidates).join();

                for (LevelSummary summary : summaries) {
                    JsonObject worldObj = new JsonObject();
                    worldObj.addProperty("name", summary.getName());
                    worldObj.addProperty("display_name", summary.getDisplayName());
                    worldObj.addProperty("last_played", summary.getLastPlayed());
                    worldObj.addProperty("game_mode", summary.getGameMode().toString().toLowerCase());
                    worldObj.addProperty("hardcore", summary.isHardcore());
                    // Note: hasCheats() and requiresConversion() may not be available in this API version
                    worldObj.addProperty("locked", summary.isLocked());

                    if (summary.getDetails() != null) {
                        worldObj.addProperty("details", summary.getDetails().getString());
                    }

                    worldsArray.add(worldObj);
                }

                result.add("worlds", worldsArray);
                result.addProperty("count", worldsArray.size());
            } catch (Exception e) {
                McCliMod.LOGGER.error("Failed to list worlds", e);
                result.addProperty("error", "Failed to list worlds: " + e.getMessage());
                result.add("worlds", worldsArray);
                result.addProperty("count", 0);
            }

            return result;
        });
    }

    /**
     * Open the select world screen for creating or managing worlds.
     *
     * Response:
     * - success: boolean
     * - screen_opened: boolean
     */
    private CompletableFuture<JsonObject> createWorld(JsonObject params) {
        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();

            // Check if already in a world
            if (client.world != null) {
                result.addProperty("success", false);
                result.addProperty("error", "Already in a world. Disconnect first.");
                return result;
            }

            try {
                McCliMod.LOGGER.info("Opening select world screen");

                // Open the select world screen - user can create new world from there
                client.setScreen(new SelectWorldScreen(client.currentScreen));

                result.addProperty("success", true);
                result.addProperty("screen_opened", true);
                result.addProperty("note", "Select world screen opened. Use 'Create New World' button or select an existing world.");

            } catch (Exception e) {
                McCliMod.LOGGER.error("Failed to open select world screen", e);
                result.addProperty("success", false);
                result.addProperty("error", "Failed to open select world screen: " + e.getMessage());
            }

            return result;
        });
    }

    /**
     * Load an existing singleplayer world.
     *
     * Params:
     * - name: World folder name or display name (required)
     *
     * Response:
     * - success: boolean
     * - name: world name
     * - loading: boolean
     */
    private CompletableFuture<JsonObject> loadWorld(JsonObject params) {
        if (!params.has("name")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: name"));
            return future;
        }

        String name = params.get("name").getAsString();

        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();

            // Check if already in a world
            if (client.world != null) {
                result.addProperty("success", false);
                result.addProperty("error", "Already in a world. Disconnect first.");
                return result;
            }

            try {
                LevelStorage levelSource = client.getLevelStorage();
                LevelStorage.LevelList candidates = levelSource.getLevelList();
                List<LevelSummary> summaries = levelSource.loadSummaries(candidates).join();

                // Find the world by name (folder name or display name)
                LevelSummary targetWorld = null;
                for (LevelSummary summary : summaries) {
                    if (summary.getName().equals(name) || summary.getDisplayName().equals(name)) {
                        targetWorld = summary;
                        break;
                    }
                }

                if (targetWorld == null) {
                    result.addProperty("success", false);
                    result.addProperty("error", "World not found: " + name);
                    result.addProperty("hint", "Use 'world list' to see available worlds");
                    return result;
                }

                if (targetWorld.isLocked()) {
                    result.addProperty("success", false);
                    result.addProperty("error", "World is locked: " + name);
                    return result;
                }

                String worldName = targetWorld.getName();
                String displayName = targetWorld.getDisplayName();

                McCliMod.LOGGER.info("Loading world: {} ({})", displayName, worldName);

                // Start the world on a later main-thread task: start() blocks the render thread
                // until the integrated server is up, which would otherwise hold this response
                // past the CLI's socket timeout.
                client.send(() -> client.createIntegratedServerLoader().start(worldName, () -> {
                    // Called when loading fails - return to title
                    client.setScreen(new TitleScreen());
                }));

                result.addProperty("success", true);
                result.addProperty("name", worldName);
                result.addProperty("display_name", displayName);
                result.addProperty("loading", true);

            } catch (Exception e) {
                McCliMod.LOGGER.error("Failed to load world", e);
                result.addProperty("success", false);
                result.addProperty("error", "Failed to load world: " + e.getMessage());
            }

            return result;
        });
    }

    /**
     * Delete a singleplayer world.
     *
     * Params:
     * - name: World folder name or display name (required)
     *
     * Response:
     * - success: boolean
     * - name: deleted world name
     */
    private CompletableFuture<JsonObject> deleteWorld(JsonObject params) {
        if (!params.has("name")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: name"));
            return future;
        }

        String name = params.get("name").getAsString();

        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            JsonObject result = new JsonObject();

            // Safety check - don't delete if currently in that world
            if (client.world != null && client.isIntegratedServerRunning()) {
                String currentWorld = client.getServer() != null ?
                    client.getServer().getSaveProperties().getLevelName() : null;
                if (name.equals(currentWorld)) {
                    result.addProperty("success", false);
                    result.addProperty("error", "Cannot delete the currently loaded world. Disconnect first.");
                    return result;
                }
            }

            try {
                LevelStorage levelSource = client.getLevelStorage();
                LevelStorage.LevelList candidates = levelSource.getLevelList();
                List<LevelSummary> summaries = levelSource.loadSummaries(candidates).join();

                // Find the world by name
                LevelSummary targetWorld = null;
                for (LevelSummary summary : summaries) {
                    if (summary.getName().equals(name) || summary.getDisplayName().equals(name)) {
                        targetWorld = summary;
                        break;
                    }
                }

                if (targetWorld == null) {
                    result.addProperty("success", false);
                    result.addProperty("error", "World not found: " + name);
                    return result;
                }

                if (targetWorld.isLocked()) {
                    result.addProperty("success", false);
                    result.addProperty("error", "World is locked and cannot be deleted: " + name);
                    return result;
                }

                String worldName = targetWorld.getName();
                String displayName = targetWorld.getDisplayName();

                McCliMod.LOGGER.info("Deleting world: {} ({})", displayName, worldName);

                // Delete the world using LevelStorage (Session.deleteSessionLock is Mojang's deleteLevel)
                try (LevelStorage.Session session = levelSource.createSession(worldName)) {
                    session.deleteSessionLock();
                }

                // Also delete the folder
                Path worldPath = levelSource.getSavesDirectory().resolve(worldName);
                if (Files.exists(worldPath)) {
                    deleteDirectory(worldPath);
                }

                result.addProperty("success", true);
                result.addProperty("name", worldName);
                result.addProperty("display_name", displayName);
                result.addProperty("deleted", true);

            } catch (Exception e) {
                McCliMod.LOGGER.error("Failed to delete world", e);
                result.addProperty("success", false);
                result.addProperty("error", "Failed to delete world: " + e.getMessage());
            }

            return result;
        });
    }

    /**
     * Recursively delete a directory.
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        McCliMod.LOGGER.warn("Failed to delete: {}", p, e);
                    }
                });
        }
    }
}
