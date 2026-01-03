package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.Pack;

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
            Minecraft client = Minecraft.getInstance();
            PackRepository repository = client.getResourcePackRepository();

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

    private JsonObject listPacks(PackRepository repository) {
        JsonObject result = new JsonObject();
        JsonArray packs = new JsonArray();

        for (Pack pack : repository.getAvailablePacks()) {
            JsonObject packObj = new JsonObject();
            packObj.addProperty("id", pack.getId());
            packObj.addProperty("name", pack.getTitle().getString());
            packObj.addProperty("description", pack.getDescription().getString());
            packObj.addProperty("enabled", repository.getSelectedIds().contains(pack.getId()));
            packObj.addProperty("required", pack.isRequired());
            packs.add(packObj);
        }

        result.add("packs", packs);
        result.addProperty("count", packs.size());
        return result;
    }

    private JsonObject listEnabledPacks(PackRepository repository) {
        JsonObject result = new JsonObject();
        JsonArray packs = new JsonArray();

        for (Pack pack : repository.getSelectedPacks()) {
            JsonObject packObj = new JsonObject();
            packObj.addProperty("id", pack.getId());
            packObj.addProperty("name", pack.getTitle().getString());
            packObj.addProperty("description", pack.getDescription().getString());
            packs.add(packObj);
        }

        result.add("packs", packs);
        result.addProperty("count", packs.size());
        return result;
    }

    private JsonObject enablePack(PackRepository repository, String name) {
        JsonObject result = new JsonObject();

        // Find the pack by ID or display name
        Pack pack = findPack(repository, name);
        if (pack == null) {
            result.addProperty("success", false);
            result.addProperty("error", "Resource pack not found: " + name);
            return result;
        }

        if (repository.getSelectedIds().contains(pack.getId())) {
            result.addProperty("success", true);
            result.addProperty("already_enabled", true);
            result.addProperty("id", pack.getId());
            return result;
        }

        // Enable the pack
        Collection<String> enabled = new ArrayList<>(repository.getSelectedIds());
        enabled.add(pack.getId());
        repository.setSelected(enabled);

        result.addProperty("success", true);
        result.addProperty("id", pack.getId());
        result.addProperty("name", pack.getTitle().getString());
        return result;
    }

    private JsonObject disablePack(PackRepository repository, String name) {
        JsonObject result = new JsonObject();

        // Find the pack by ID or display name
        Pack pack = findPack(repository, name);
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

        if (!repository.getSelectedIds().contains(pack.getId())) {
            result.addProperty("success", true);
            result.addProperty("already_disabled", true);
            result.addProperty("id", pack.getId());
            return result;
        }

        // Disable the pack
        Collection<String> enabled = new ArrayList<>(repository.getSelectedIds());
        enabled.remove(pack.getId());
        repository.setSelected(enabled);

        result.addProperty("success", true);
        result.addProperty("id", pack.getId());
        result.addProperty("name", pack.getTitle().getString());
        return result;
    }

    private JsonObject reloadPacks(Minecraft client) {
        JsonObject result = new JsonObject();

        // Trigger resource reload
        client.reloadResourcePacks();

        result.addProperty("success", true);
        result.addProperty("reloading", true);
        return result;
    }

    private Pack findPack(PackRepository repository, String name) {
        // Try exact ID match first
        for (Pack pack : repository.getAvailablePacks()) {
            if (pack.getId().equals(name)) {
                return pack;
            }
        }

        // Try display name match
        for (Pack pack : repository.getAvailablePacks()) {
            if (pack.getTitle().getString().equalsIgnoreCase(name)) {
                return pack;
            }
        }

        // Try partial ID match
        for (Pack pack : repository.getAvailablePacks()) {
            if (pack.getId().toLowerCase().contains(name.toLowerCase())) {
                return pack;
            }
        }

        return null;
    }
}
