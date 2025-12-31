package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Resource pack management command.
 *
 * Params:
 * - action: "list" | "enabled" | "enable" | "disable" | "reload"
 * - name: resource pack name (for "enable" and "disable" actions)
 *
 * Actions:
 * - list: List all available resource packs
 * - enabled: List currently enabled resource packs
 * - enable: Enable a specific resource pack
 * - disable: Disable a specific resource pack
 * - reload: Reload all resource packs
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
        if (!List.of("list", "enabled", "enable", "disable", "reload").contains(action)) {
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

        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ResourcePackManager manager = client.getResourcePackManager();

            return switch (action) {
                case "list" -> listPacks(manager);
                case "enabled" -> listEnabledPacks(manager);
                case "enable" -> enablePack(manager, params.get("name").getAsString());
                case "disable" -> disablePack(manager, params.get("name").getAsString());
                case "reload" -> reloadPacks(client);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            };
        });
    }

    private JsonObject listPacks(ResourcePackManager manager) {
        JsonObject result = new JsonObject();
        JsonArray packs = new JsonArray();

        for (ResourcePackProfile profile : manager.getProfiles()) {
            JsonObject pack = new JsonObject();
            pack.addProperty("id", profile.getId());
            pack.addProperty("name", profile.getDisplayName().getString());
            pack.addProperty("description", profile.getDescription().getString());
            pack.addProperty("enabled", manager.getEnabledIds().contains(profile.getId()));
            pack.addProperty("required", profile.isAlwaysEnabled());
            packs.add(pack);
        }

        result.add("packs", packs);
        result.addProperty("count", packs.size());
        return result;
    }

    private JsonObject listEnabledPacks(ResourcePackManager manager) {
        JsonObject result = new JsonObject();
        JsonArray packs = new JsonArray();

        for (ResourcePackProfile profile : manager.getEnabledProfiles()) {
            JsonObject pack = new JsonObject();
            pack.addProperty("id", profile.getId());
            pack.addProperty("name", profile.getDisplayName().getString());
            pack.addProperty("description", profile.getDescription().getString());
            packs.add(pack);
        }

        result.add("packs", packs);
        result.addProperty("count", packs.size());
        return result;
    }

    private JsonObject enablePack(ResourcePackManager manager, String name) {
        JsonObject result = new JsonObject();

        // Find the pack by ID or display name
        ResourcePackProfile profile = findPack(manager, name);
        if (profile == null) {
            result.addProperty("success", false);
            result.addProperty("error", "Resource pack not found: " + name);
            return result;
        }

        if (manager.getEnabledIds().contains(profile.getId())) {
            result.addProperty("success", true);
            result.addProperty("already_enabled", true);
            result.addProperty("id", profile.getId());
            return result;
        }

        // Enable the pack
        Collection<String> enabled = new ArrayList<>(manager.getEnabledIds());
        enabled.add(profile.getId());
        manager.setEnabledProfiles(enabled);

        result.addProperty("success", true);
        result.addProperty("id", profile.getId());
        result.addProperty("name", profile.getDisplayName().getString());
        return result;
    }

    private JsonObject disablePack(ResourcePackManager manager, String name) {
        JsonObject result = new JsonObject();

        // Find the pack by ID or display name
        ResourcePackProfile profile = findPack(manager, name);
        if (profile == null) {
            result.addProperty("success", false);
            result.addProperty("error", "Resource pack not found: " + name);
            return result;
        }

        if (profile.isAlwaysEnabled()) {
            result.addProperty("success", false);
            result.addProperty("error", "Cannot disable required resource pack: " + name);
            return result;
        }

        if (!manager.getEnabledIds().contains(profile.getId())) {
            result.addProperty("success", true);
            result.addProperty("already_disabled", true);
            result.addProperty("id", profile.getId());
            return result;
        }

        // Disable the pack
        Collection<String> enabled = new ArrayList<>(manager.getEnabledIds());
        enabled.remove(profile.getId());
        manager.setEnabledProfiles(enabled);

        result.addProperty("success", true);
        result.addProperty("id", profile.getId());
        result.addProperty("name", profile.getDisplayName().getString());
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

    private ResourcePackProfile findPack(ResourcePackManager manager, String name) {
        // Try exact ID match first
        for (ResourcePackProfile profile : manager.getProfiles()) {
            if (profile.getId().equals(name)) {
                return profile;
            }
        }

        // Try display name match
        for (ResourcePackProfile profile : manager.getProfiles()) {
            if (profile.getDisplayName().getString().equalsIgnoreCase(name)) {
                return profile;
            }
        }

        // Try partial ID match
        for (ResourcePackProfile profile : manager.getProfiles()) {
            if (profile.getId().toLowerCase().contains(name.toLowerCase())) {
                return profile;
            }
        }

        return null;
    }
}
