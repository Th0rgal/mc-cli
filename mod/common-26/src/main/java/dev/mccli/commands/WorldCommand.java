package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.McCliMod;
import dev.mccli.util.ClientCompat;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
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
 * - create: Open the select world screen, or (26.x, when "name" is given) create and join
 *           a new world directly without any GUI interaction
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
            Minecraft client = Minecraft.getInstance();
            JsonObject result = new JsonObject();
            JsonArray worldsArray = new JsonArray();

            try {
                LevelStorageSource levelSource = client.getLevelSource();
                LevelStorageSource.LevelCandidates candidates = levelSource.findLevelCandidates();
                List<LevelSummary> summaries = levelSource.loadLevelSummaries(candidates).join();

                for (LevelSummary summary : summaries) {
                    JsonObject worldObj = new JsonObject();
                    worldObj.addProperty("name", summary.getLevelId());
                    worldObj.addProperty("display_name", summary.getLevelName());
                    worldObj.addProperty("last_played", summary.getLastPlayed());
                    worldObj.addProperty("game_mode", summary.getGameMode().toString().toLowerCase());
                    worldObj.addProperty("hardcore", summary.isHardcore());
                    // Note: hasCheats() and requiresConversion() may not be available in this API version
                    worldObj.addProperty("locked", summary.isLocked());

                    if (summary.getInfo() != null) {
                        worldObj.addProperty("details", summary.getInfo().getString());
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
     * If a "name" param is given, the world is instead created and joined directly
     * (no GUI interaction), which is what automated agents need.
     *
     * Params (all optional):
     * - name: world folder/display name; enables direct creation
     * - seed: world seed (number or string; default random)
     * - game_mode: survival | creative | adventure | spectator (default: creative)
     *
     * Response:
     * - success: boolean
     * - screen_opened: boolean (screen mode)
     * - name, display_name, created, loading (direct creation mode)
     */
    private CompletableFuture<JsonObject> createWorld(JsonObject params) {
        if (params.has("name") && !params.get("name").isJsonNull()
                && !params.get("name").getAsString().isBlank()) {
            return createWorldDirect(params);
        }
        return MainThreadExecutor.submit(() -> {
            Minecraft client = Minecraft.getInstance();
            JsonObject result = new JsonObject();

            // Check if already in a world
            if (client.level != null) {
                result.addProperty("success", false);
                result.addProperty("error", "Already in a world. Disconnect first.");
                return result;
            }

            try {
                McCliMod.LOGGER.info("Opening select world screen");

                // Open the select world screen - user can create new world from there
                ClientCompat.setScreen(client, new SelectWorldScreen(ClientCompat.screen(client)));

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

    private CompletableFuture<JsonObject> createWorldDirect(JsonObject params) {
        String name = params.get("name").getAsString().trim();
        String gameModeName = params.has("game_mode") ? params.get("game_mode").getAsString() : "creative";
        GameType gameType = GameType.byName(gameModeName.toLowerCase(), null);
        if (gameType == null) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException(
                "Unknown game_mode: " + gameModeName + ". Valid: survival, creative, adventure, spectator"));
            return future;
        }
        WorldOptions options = WorldOptions.defaultWithRandomSeed();
        if (params.has("seed") && !params.get("seed").isJsonNull()) {
            String seedStr = params.get("seed").getAsString();
            options = options.withSeed(WorldOptions.parseSeed(seedStr));
        }
        final WorldOptions worldOptions = options;

        return MainThreadExecutor.submit(() -> {
            Minecraft client = Minecraft.getInstance();
            JsonObject result = new JsonObject();

            if (client.level != null) {
                result.addProperty("success", false);
                result.addProperty("error", "Already in a world. Disconnect first.");
                return result;
            }

            LevelStorageSource levelSource = client.getLevelSource();
            String levelId = name.replaceAll("[^A-Za-z0-9_\\- ]", "_");
            if (levelSource.levelExists(levelId)) {
                result.addProperty("success", false);
                result.addProperty("error", "World already exists: " + levelId);
                result.addProperty("hint", "Use 'world load --name " + levelId + "' to open it");
                return result;
            }
            if (!levelSource.isNewLevelIdAcceptable(levelId)) {
                result.addProperty("success", false);
                result.addProperty("error", "Invalid world name: " + name);
                return result;
            }

            McCliMod.LOGGER.info("Creating world: {} (mode: {})", levelId, gameType.getName());
            LevelSettings settings = new LevelSettings(
                name, gameType, LevelSettings.DifficultySettings.DEFAULT,
                true, WorldDataConfiguration.DEFAULT);
            // Create on the next tick so this response is sent first (creation blocks the main thread)
            MainThreadExecutor.runNextTick(() -> client.createWorldOpenFlows().createFreshLevel(
                levelId, settings, worldOptions, WorldPresets::createNormalWorldDimensions, new TitleScreen()));

            result.addProperty("success", true);
            result.addProperty("name", levelId);
            result.addProperty("display_name", name);
            result.addProperty("game_mode", gameType.getName());
            result.addProperty("created", true);
            result.addProperty("loading", true);
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
            Minecraft client = Minecraft.getInstance();
            JsonObject result = new JsonObject();

            // Check if already in a world
            if (client.level != null) {
                result.addProperty("success", false);
                result.addProperty("error", "Already in a world. Disconnect first.");
                return result;
            }

            try {
                LevelStorageSource levelSource = client.getLevelSource();
                LevelStorageSource.LevelCandidates candidates = levelSource.findLevelCandidates();
                List<LevelSummary> summaries = levelSource.loadLevelSummaries(candidates).join();

                // Find the world by name (folder name or display name)
                LevelSummary targetWorld = null;
                for (LevelSummary summary : summaries) {
                    if (summary.getLevelId().equals(name) || summary.getLevelName().equals(name)) {
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

                String worldName = targetWorld.getLevelId();
                String displayName = targetWorld.getLevelName();

                McCliMod.LOGGER.info("Loading world: {} ({})", displayName, worldName);

                // Start the world on the next tick so this response is sent first:
                // loading blocks the main thread until the world is joined.
                MainThreadExecutor.runNextTick(() ->
                    client.createWorldOpenFlows().openWorld(worldName, () -> {
                        // Called when loading fails - return to title
                        ClientCompat.setScreen(client, new TitleScreen());
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
            Minecraft client = Minecraft.getInstance();
            JsonObject result = new JsonObject();

            // Safety check - don't delete if currently in that world
            if (client.level != null && client.hasSingleplayerServer()) {
                String currentWorld = client.getSingleplayerServer() != null ?
                    client.getSingleplayerServer().getWorldData().getLevelName() : null;
                if (name.equals(currentWorld)) {
                    result.addProperty("success", false);
                    result.addProperty("error", "Cannot delete the currently loaded world. Disconnect first.");
                    return result;
                }
            }

            try {
                LevelStorageSource levelSource = client.getLevelSource();
                LevelStorageSource.LevelCandidates candidates = levelSource.findLevelCandidates();
                List<LevelSummary> summaries = levelSource.loadLevelSummaries(candidates).join();

                // Find the world by name
                LevelSummary targetWorld = null;
                for (LevelSummary summary : summaries) {
                    if (summary.getLevelId().equals(name) || summary.getLevelName().equals(name)) {
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

                String worldName = targetWorld.getLevelId();
                String displayName = targetWorld.getLevelName();

                McCliMod.LOGGER.info("Deleting world: {} ({})", displayName, worldName);

                // Delete the world using LevelStorageSource
                try (LevelStorageSource.LevelStorageAccess session = levelSource.validateAndCreateAccess(worldName)) {
                    session.deleteLevel();
                }

                // Also delete the folder
                Path worldPath = levelSource.getBaseDir().resolve(worldName);
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
