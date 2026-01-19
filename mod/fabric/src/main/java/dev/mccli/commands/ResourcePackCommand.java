package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.McCliMod;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Resource pack management command.
 *
 * Params:
 * - action: "list" | "enabled" | "enable" | "disable" | "reload" | "load"
 * - name: resource pack name (for "enable" and "disable" actions)
 * - path: file path to resource pack zip (for "load" action)
 *
 * Actions:
 * - list: List all available resource packs
 * - enabled: List currently enabled resource packs
 * - enable: Enable a specific resource pack
 * - disable: Disable a specific resource pack
 * - reload: Reload all resource packs
 * - load: Load a resource pack from a file path (copies to resourcepacks folder)
 */
public class ResourcePackCommand implements Command {
    @Override
    public String getName() {
        return "resourcepack";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "list";

        // Validate action
        if (!List.of("list", "enabled", "enable", "disable", "reload", "load").contains(action)) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
            return future;
        }

        // Validate 'enable' and 'disable' actions have required 'name' parameter
        if ((action.equals("enable") || action.equals("disable")) && !params.has("name")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: name"));
            return future;
        }

        // Validate 'load' action has required 'path' parameter
        if (action.equals("load") && !params.has("path")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: path"));
            return future;
        }

        // Handle load action separately as it involves file I/O
        if (action.equals("load")) {
            return loadPack(params.get("path").getAsString(),
                params.has("enable") && params.get("enable").getAsBoolean());
        }

        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ResourcePackManager repository = client.getResourcePackManager();

            return switch (action) {
                case "list" -> listPacks(repository);
                case "enabled" -> listEnabledPacks(repository);
                case "enable" -> enablePack(repository, params.get("name").getAsString());
                case "disable" -> disablePack(repository, params.get("name").getAsString());
                case "reload" -> reloadPacks(client);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            };
        });
    }

    private JsonObject listPacks(ResourcePackManager repository) {
        JsonObject result = new JsonObject();
        JsonArray packs = new JsonArray();

        for (ResourcePackProfile pack : repository.getProfiles()) {
            JsonObject packObj = new JsonObject();
            packObj.addProperty("id", pack.getId());
            packObj.addProperty("name", pack.getDisplayName().getString());
            packObj.addProperty("description", pack.getDescription().getString());
            packObj.addProperty("enabled", repository.getEnabledIds().contains(pack.getId()));
            packObj.addProperty("required", pack.isRequired());
            packs.add(packObj);
        }

        result.add("packs", packs);
        result.addProperty("count", packs.size());
        return result;
    }

    private JsonObject listEnabledPacks(ResourcePackManager repository) {
        JsonObject result = new JsonObject();
        JsonArray packs = new JsonArray();

        for (ResourcePackProfile pack : repository.getEnabledProfiles()) {
            JsonObject packObj = new JsonObject();
            packObj.addProperty("id", pack.getId());
            packObj.addProperty("name", pack.getDisplayName().getString());
            packObj.addProperty("description", pack.getDescription().getString());
            packs.add(packObj);
        }

        result.add("packs", packs);
        result.addProperty("count", packs.size());
        return result;
    }

    private JsonObject enablePack(ResourcePackManager repository, String name) {
        JsonObject result = new JsonObject();

        // Find the pack by ID or display name
        ResourcePackProfile pack = findPack(repository, name);
        if (pack == null) {
            result.addProperty("success", false);
            result.addProperty("error", "Resource pack not found: " + name);
            return result;
        }

        if (repository.getEnabledIds().contains(pack.getId())) {
            result.addProperty("success", true);
            result.addProperty("already_enabled", true);
            result.addProperty("id", pack.getId());
            return result;
        }

        // Enable the pack
        Collection<String> enabled = new ArrayList<>(repository.getEnabledIds());
        enabled.add(pack.getId());
        repository.setEnabledProfiles(enabled);

        result.addProperty("success", true);
        result.addProperty("id", pack.getId());
        result.addProperty("name", pack.getDisplayName().getString());
        return result;
    }

    private JsonObject disablePack(ResourcePackManager repository, String name) {
        JsonObject result = new JsonObject();

        // Find the pack by ID or display name
        ResourcePackProfile pack = findPack(repository, name);
        if (pack == null) {
            result.addProperty("success", false);
            result.addProperty("error", "Resource pack not found: " + name);
            return result;
        }

        if (pack.isRequired()) {
            result.addProperty("success", false);
            result.addProperty("error", "Cannot disable required resource pack: " + name);
            return result;
        }

        if (!repository.getEnabledIds().contains(pack.getId())) {
            result.addProperty("success", true);
            result.addProperty("already_disabled", true);
            result.addProperty("id", pack.getId());
            return result;
        }

        // Disable the pack
        Collection<String> enabled = new ArrayList<>(repository.getEnabledIds());
        enabled.remove(pack.getId());
        repository.setEnabledProfiles(enabled);

        result.addProperty("success", true);
        result.addProperty("id", pack.getId());
        result.addProperty("name", pack.getDisplayName().getString());
        return result;
    }

    private JsonObject reloadPacks(MinecraftClient client) {
        JsonObject result = new JsonObject();

        // Trigger resource reload
        client.reloadResources();

        result.addProperty("success", true);
        result.addProperty("reloading", true);
        return result;
    }

    private CompletableFuture<JsonObject> loadPack(String sourcePath, boolean enableAfterLoad) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject result = new JsonObject();

            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                result.addProperty("success", false);
                result.addProperty("error", "File not found: " + sourcePath);
                return result;
            }

            if (!sourceFile.getName().endsWith(".zip") && !sourceFile.isDirectory()) {
                result.addProperty("success", false);
                result.addProperty("error", "Resource pack must be a .zip file or directory");
                return result;
            }

            try {
                MinecraftClient client = MinecraftClient.getInstance();
                Path resourcepacksDir = client.getResourcePackDir();

                // Create resourcepacks directory if it doesn't exist
                if (!Files.exists(resourcepacksDir)) {
                    Files.createDirectories(resourcepacksDir);
                }

                Path destPath = resourcepacksDir.resolve(sourceFile.getName());

                // Copy or replace the file
                if (sourceFile.isDirectory()) {
                    // For directories, we need to copy recursively
                    copyDirectory(sourceFile.toPath(), destPath);
                } else {
                    Files.copy(sourceFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                }

                McCliMod.LOGGER.info("Copied resource pack to: {}", destPath);

                result.addProperty("success", true);
                result.addProperty("copied_to", destPath.toString());
                result.addProperty("filename", sourceFile.getName());

                // Scan for new packs and optionally enable
                return MainThreadExecutor.submit(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    ResourcePackManager repository = mc.getResourcePackManager();

                    // Scan for new packs
                    repository.scanPacks();

                    String packId = "file/" + sourceFile.getName();
                    ResourcePackProfile pack = findPack(repository, packId);

                    if (pack != null) {
                        result.addProperty("pack_id", pack.getId());
                        result.addProperty("pack_name", pack.getDisplayName().getString());

                        if (enableAfterLoad) {
                            Collection<String> enabled = new ArrayList<>(repository.getEnabledIds());
                            if (!enabled.contains(pack.getId())) {
                                enabled.add(pack.getId());
                                repository.setEnabledProfiles(enabled);
                                result.addProperty("enabled", true);

                                // Reload resources to apply the pack
                                mc.reloadResources();
                                result.addProperty("reloading", true);
                            }
                        }
                    } else {
                        result.addProperty("warning", "Pack copied but not found in repository. Try 'reload' action.");
                    }

                    return result;
                }).join();

            } catch (IOException e) {
                McCliMod.LOGGER.error("Failed to copy resource pack", e);
                result.addProperty("success", false);
                result.addProperty("error", "Failed to copy: " + e.getMessage());
                return result;
            }
        });
    }

    private void copyDirectory(Path source, Path dest) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = dest.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectory(targetPath);
                    }
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy: " + sourcePath, e);
            }
        });
    }

    private ResourcePackProfile findPack(ResourcePackManager repository, String name) {
        // Try exact ID match first
        for (ResourcePackProfile pack : repository.getProfiles()) {
            if (pack.getId().equals(name)) {
                return pack;
            }
        }

        // Try display name match
        for (ResourcePackProfile pack : repository.getProfiles()) {
            if (pack.getDisplayName().getString().equalsIgnoreCase(name)) {
                return pack;
            }
        }

        // Try partial ID match
        for (ResourcePackProfile pack : repository.getProfiles()) {
            if (pack.getId().toLowerCase().contains(name.toLowerCase())) {
                return pack;
            }
        }

        return null;
    }
}
