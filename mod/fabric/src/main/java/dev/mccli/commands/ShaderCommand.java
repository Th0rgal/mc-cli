package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.McCliMod;
import dev.mccli.util.IrisHelper;
import dev.mccli.util.MainThreadExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Comprehensive shader management command.
 *
 * Params:
 * - action: "list" | "get" | "set" | "reload" | "disable" | "errors"
 * - name: shader pack name (for "set" action)
 *
 * Actions:
 * - list: List available shader packs
 * - get: Get current shader info
 * - set: Set active shader pack
 * - reload: Reload current shader
 * - disable: Disable shaders
 * - errors: Get shader compilation errors
 */
public class ShaderCommand implements Command {
    @Override
    public String getName() {
        return "shader";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "get";

        return MainThreadExecutor.submit(() -> {
            if (!IrisHelper.isLoaded()) {
                JsonObject result = new JsonObject();
                result.addProperty("error", "Iris is not loaded");
                result.addProperty("iris_loaded", false);
                return result;
            }

            return switch (action) {
                case "list" -> listShaderPacks();
                case "get" -> getCurrentShader();
                case "set" -> setShaderPack(params);
                case "reload" -> reloadShaders();
                case "disable" -> disableShaders();
                case "errors" -> getShaderErrors();
                default -> {
                    JsonObject result = new JsonObject();
                    result.addProperty("error", "Unknown action: " + action);
                    yield result;
                }
            };
        });
    }

    private JsonObject listShaderPacks() {
        JsonObject result = new JsonObject();
        JsonArray packs = new JsonArray();

        for (IrisHelper.ShaderPackInfo pack : IrisHelper.listShaderPacks()) {
            JsonObject packJson = new JsonObject();
            packJson.addProperty("name", pack.name());
            packJson.addProperty("type", pack.type());
            packs.add(packJson);
        }

        result.add("packs", packs);
        result.addProperty("count", packs.size());
        return result;
    }

    private JsonObject getCurrentShader() {
        JsonObject result = new JsonObject();
        result.addProperty("active", IrisHelper.areShadersEnabled());
        result.addProperty("name", IrisHelper.getCurrentPackName());
        return result;
    }

    private JsonObject setShaderPack(JsonObject params) {
        JsonObject result = new JsonObject();

        if (!params.has("name")) {
            result.addProperty("error", "Missing required parameter: name");
            return result;
        }

        String name = params.get("name").getAsString();
        boolean success = IrisHelper.setShaderPack(name);

        result.addProperty("success", success);
        result.addProperty("name", name);
        return result;
    }

    private JsonObject reloadShaders() {
        JsonObject result = new JsonObject();
        boolean success = IrisHelper.reload();

        result.addProperty("reloaded", success);

        // Check for errors after reload
        List<IrisHelper.ShaderError> errors = IrisHelper.getShaderErrors();
        if (!errors.isEmpty()) {
            JsonArray errorsArray = new JsonArray();
            for (IrisHelper.ShaderError error : errors) {
                JsonObject errorJson = new JsonObject();
                errorJson.addProperty("file", error.file());
                errorJson.addProperty("line", error.line());
                errorJson.addProperty("message", error.message());
                errorsArray.add(errorJson);
            }
            result.add("errors", errorsArray);
            result.addProperty("has_errors", true);
        } else {
            result.addProperty("has_errors", false);
        }

        return result;
    }

    private JsonObject disableShaders() {
        JsonObject result = new JsonObject();
        boolean success = IrisHelper.disable();

        result.addProperty("disabled", success);
        return result;
    }

    private JsonObject getShaderErrors() {
        JsonObject result = new JsonObject();
        List<IrisHelper.ShaderError> errors = IrisHelper.getShaderErrors();

        JsonArray errorsArray = new JsonArray();
        for (IrisHelper.ShaderError error : errors) {
            JsonObject errorJson = new JsonObject();
            errorJson.addProperty("file", error.file());
            errorJson.addProperty("line", error.line());
            errorJson.addProperty("message", error.message());
            errorsArray.add(errorJson);
        }

        result.add("errors", errorsArray);
        result.addProperty("has_errors", !errors.isEmpty());
        result.addProperty("count", errors.size());
        return result;
    }
}
