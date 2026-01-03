package dev.mccli.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.mccli.McCliMod;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the instance registry file (~/.mccli/instances.json).
 *
 * This allows multiple Minecraft instances to register themselves
 * and lets CLI clients discover available instances.
 */
public class InstanceRegistry {
    private static final Path REGISTRY_DIR = Paths.get(System.getProperty("user.home"), ".mccli");
    private static final Path REGISTRY_FILE = REGISTRY_DIR.resolve("instances.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Represents a registered MC-CLI instance.
     */
    public static class Instance {
        public String name;
        public int port;
        public long pid;
        public long startTime;
        public String version;

        public Instance() {}

        public Instance(String name, int port) {
            this.name = name;
            this.port = port;
            this.pid = ProcessHandle.current().pid();
            this.startTime = System.currentTimeMillis();
            this.version = McCliMod.MOD_ID;
        }
    }

    /**
     * Register this instance in the registry.
     */
    public static synchronized void register(String name, int port) {
        try {
            ensureRegistryDir();
            List<Instance> instances = readRegistry();

            // Remove any stale entries (dead processes or same port)
            cleanStaleEntries(instances);
            instances.removeIf(i -> i.port == port);

            // Add this instance
            Instance instance = new Instance(name, port);
            instances.add(instance);

            writeRegistry(instances);
            McCliMod.LOGGER.info("Registered instance '{}' on port {}", name, port);
        } catch (IOException e) {
            McCliMod.LOGGER.warn("Failed to register instance: {}", e.getMessage());
        }
    }

    /**
     * Unregister this instance from the registry.
     */
    public static synchronized void unregister(int port) {
        try {
            if (!Files.exists(REGISTRY_FILE)) {
                return;
            }

            List<Instance> instances = readRegistry();
            instances.removeIf(i -> i.port == port);
            cleanStaleEntries(instances);

            if (instances.isEmpty()) {
                Files.deleteIfExists(REGISTRY_FILE);
            } else {
                writeRegistry(instances);
            }

            McCliMod.LOGGER.info("Unregistered instance on port {}", port);
        } catch (IOException e) {
            McCliMod.LOGGER.warn("Failed to unregister instance: {}", e.getMessage());
        }
    }

    /**
     * Get all registered instances.
     */
    public static synchronized List<Instance> getInstances() {
        try {
            if (!Files.exists(REGISTRY_FILE)) {
                return new ArrayList<>();
            }

            List<Instance> instances = readRegistry();
            cleanStaleEntries(instances);
            writeRegistry(instances);
            return instances;
        } catch (IOException e) {
            McCliMod.LOGGER.warn("Failed to read registry: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Generate a unique instance name based on the world or context.
     */
    public static String generateInstanceName() {
        Minecraft client = Minecraft.getInstance();

        // Try to get world name if in-game
        if (client != null && client.level != null) {
            if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
                String worldName = client.getSingleplayerServer().getWorldData().getLevelName();
                return sanitizeName(worldName);
            } else if (client.getCurrentServer() != null) {
                return sanitizeName(client.getCurrentServer().ip);
            }
        }

        // Fall back to process ID
        return "minecraft-" + ProcessHandle.current().pid();
    }

    private static String sanitizeName(String name) {
        // Remove special characters, keep alphanumeric, dash, underscore
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    private static void ensureRegistryDir() throws IOException {
        if (!Files.exists(REGISTRY_DIR)) {
            Files.createDirectories(REGISTRY_DIR);
        }
    }

    private static List<Instance> readRegistry() throws IOException {
        if (!Files.exists(REGISTRY_FILE)) {
            return new ArrayList<>();
        }

        String content = Files.readString(REGISTRY_FILE);
        Type listType = new TypeToken<ArrayList<Instance>>(){}.getType();
        List<Instance> instances = GSON.fromJson(content, listType);
        return instances != null ? instances : new ArrayList<>();
    }

    private static void writeRegistry(List<Instance> instances) throws IOException {
        ensureRegistryDir();
        String json = GSON.toJson(instances);
        Files.writeString(REGISTRY_FILE, json);
    }

    /**
     * Remove entries for processes that are no longer running.
     */
    private static void cleanStaleEntries(List<Instance> instances) {
        Iterator<Instance> iter = instances.iterator();
        while (iter.hasNext()) {
            Instance instance = iter.next();
            if (!isProcessAlive(instance.pid)) {
                McCliMod.LOGGER.debug("Removing stale instance '{}' (pid {} no longer running)",
                    instance.name, instance.pid);
                iter.remove();
            }
        }
    }

    private static boolean isProcessAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
