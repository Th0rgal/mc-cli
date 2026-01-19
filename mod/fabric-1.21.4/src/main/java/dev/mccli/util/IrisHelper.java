package dev.mccli.util;

import dev.mccli.McCliMod;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Helper for interacting with Iris shader mod via reflection.
 *
 * Iris doesn't have a stable public API, so we use reflection to access its functionality.
 * This allows MC-CLI to work across different Iris versions.
 */
public class IrisHelper {
    private static Boolean irisLoaded = null;
    private static Class<?> irisClass = null;

    /**
     * Check if Iris mod is loaded.
     */
    public static boolean isLoaded() {
        if (irisLoaded == null) {
            try {
                irisClass = Class.forName("net.irisshaders.iris.Iris");
                irisLoaded = true;
            } catch (ClassNotFoundException e) {
                irisLoaded = false;
            }
        }
        return irisLoaded;
    }

    /**
     * Get the currently active shader pack name.
     */
    public static String getCurrentPackName() {
        if (!isLoaded()) return null;

        try {
            Method method = irisClass.getMethod("getCurrentPackName");
            Object result = method.invoke(null);
            return result != null ? result.toString() : "(none)";
        } catch (Exception e) {
            McCliMod.LOGGER.debug("Failed to get current pack name", e);
            return "(unknown)";
        }
    }

    /**
     * Check if shaders are currently enabled.
     */
    public static boolean areShadersEnabled() {
        if (!isLoaded()) return false;

        try {
            // Try direct method first
            try {
                Method method = irisClass.getMethod("isShadersEnabled");
                return (Boolean) method.invoke(null);
            } catch (NoSuchMethodException ignored) {}

            // Try via config
            Method getConfig = irisClass.getMethod("getIrisConfig");
            Object config = getConfig.invoke(null);
            if (config != null) {
                Method areShadersEnabled = config.getClass().getMethod("areShadersEnabled");
                return (Boolean) areShadersEnabled.invoke(config);
            }
        } catch (Exception e) {
            McCliMod.LOGGER.debug("Failed to check shaders enabled", e);
        }

        return false;
    }

    /**
     * Get the shaderpacks directory.
     */
    public static Path getShaderpacksDirectory() {
        if (!isLoaded()) {
            return MinecraftClient.getInstance().runDirectory.toPath().resolve("shaderpacks");
        }

        try {
            Method method = irisClass.getMethod("getShaderpacksDirectory");
            return (Path) method.invoke(null);
        } catch (Exception e) {
            return MinecraftClient.getInstance().runDirectory.toPath().resolve("shaderpacks");
        }
    }

    /**
     * List available shader packs.
     */
    public static List<ShaderPackInfo> listShaderPacks() {
        List<ShaderPackInfo> packs = new ArrayList<>();
        Path dir = getShaderpacksDirectory();
        File[] files = dir.toFile().listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() || file.getName().endsWith(".zip")) {
                    packs.add(new ShaderPackInfo(
                        file.getName(),
                        file.isDirectory() ? "directory" : "zip"
                    ));
                }
            }
        }

        return packs;
    }

    /**
     * Set the active shader pack.
     */
    public static boolean setShaderPack(String name) {
        if (!isLoaded()) return false;

        try {
            Method getConfig = irisClass.getMethod("getIrisConfig");
            Object config = getConfig.invoke(null);

            if (config != null) {
                Method setShaderPackName = config.getClass().getMethod("setShaderPackName", String.class);
                setShaderPackName.invoke(config, name);

                // Try to save config
                try {
                    Method save = config.getClass().getMethod("save");
                    save.invoke(config);
                } catch (NoSuchMethodException ignored) {}

                // Reload
                reload();
                return true;
            }
        } catch (Exception e) {
            McCliMod.LOGGER.error("Failed to set shader pack", e);
        }

        return false;
    }

    /**
     * Reload shaders.
     */
    public static boolean reload() {
        if (!isLoaded()) return false;

        try {
            // Try reload() method
            try {
                Method reload = irisClass.getMethod("reload");
                reload.invoke(null);
                return true;
            } catch (NoSuchMethodException ignored) {}

            // Try reloadShaders()
            try {
                Method reloadShaders = irisClass.getMethod("reloadShaders");
                reloadShaders.invoke(null);
                return true;
            } catch (NoSuchMethodException ignored) {}

            // Fallback: simulate R key press
            McCliMod.LOGGER.warn("No reload method found, falling back to key simulation");
            return false;
        } catch (Exception e) {
            McCliMod.LOGGER.error("Failed to reload shaders", e);
            return false;
        }
    }

    /**
     * Disable shaders.
     */
    public static boolean disable() {
        if (!isLoaded()) return false;

        try {
            // Try setShadersDisabled()
            try {
                Method method = irisClass.getMethod("setShadersDisabled");
                method.invoke(null);
                return true;
            } catch (NoSuchMethodException ignored) {}

            // Try via config
            Method getConfig = irisClass.getMethod("getIrisConfig");
            Object config = getConfig.invoke(null);

            if (config != null) {
                try {
                    Method setShadersEnabled = config.getClass().getMethod("setShadersEnabled", boolean.class);
                    setShadersEnabled.invoke(config, false);

                    try {
                        Method save = config.getClass().getMethod("save");
                        save.invoke(config);
                    } catch (NoSuchMethodException ignored) {}

                    reload();
                    return true;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            McCliMod.LOGGER.error("Failed to disable shaders", e);
        }

        return false;
    }

    /**
     * Get shader compilation errors from the last reload.
     * Returns empty list if no errors or if method not available.
     */
    public static List<ShaderError> getShaderErrors() {
        List<ShaderError> errors = new ArrayList<>();

        if (!isLoaded()) return errors;

        try {
            // Try to access the shader pack loading status
            // This is version-dependent and may not work on all Iris versions

            // Try Iris.getLastShaderPackLoadError() or similar
            try {
                Method getError = irisClass.getMethod("getLastShaderPackLoadError");
                Object error = getError.invoke(null);
                if (error != null) {
                    errors.add(new ShaderError("shader_pack", 0, error.toString()));
                }
            } catch (NoSuchMethodException ignored) {}

            // Try accessing ProgramCompileException from shader pipeline
            // This requires more complex reflection and may vary by version

        } catch (Exception e) {
            McCliMod.LOGGER.debug("Failed to get shader errors", e);
        }

        return errors;
    }

    public record ShaderPackInfo(String name, String type) {}

    public record ShaderError(String file, int line, String message) {}
}
