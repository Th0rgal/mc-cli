package dev.mccli;

import dev.mccli.server.SocketServer;
import dev.mccli.util.LogCaptureAppender;
import dev.mccli.util.MainThreadExecutor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MC-CLI: Minecraft Command-Line Interface for LLM-Assisted Shader Development
 *
 * This mod provides a TCP server that accepts JSON commands to control Minecraft,
 * designed for integration with AI/LLM agents for shader development workflows.
 *
 * Architecture:
 * - SocketServer: Listens on port 25580 for TCP connections
 * - CommandDispatcher: Routes commands to handlers
 * - MainThreadExecutor: Ensures Minecraft operations run on the game thread
 *
 * Commands:
 * - status: Get game state (position, time, shader info)
 * - teleport: Move player to coordinates
 * - camera: Set view direction
 * - time: Control world time
 * - shader: Shader management (list, get, set, reload, errors)
 * - screenshot: Capture screen
 * - perf: Performance metrics
 * - logs: Get game logs
 */
@Mod(value = McCliMod.MOD_ID, dist = Dist.CLIENT)
public class McCliMod {
    public static final String MOD_ID = "mccli";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int DEFAULT_PORT = 25580;

    private static SocketServer server;
    private static LogCaptureAppender logAppender;

    public McCliMod() {
        LOGGER.info("MC-CLI initializing...");

        // Register client tick event handler
        NeoForge.EVENT_BUS.register(ClientTickHandler.class);

        // Install log capture appender
        installLogCapture();

        // Start TCP server with dynamic port allocation
        server = new SocketServer(DEFAULT_PORT);
        server.start();

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                server.stop();
            }
            uninstallLogCapture();
        }));

        // Note: Actual bound port is logged by SocketServer after binding
        LOGGER.info("MC-CLI initialized (base port: {})", DEFAULT_PORT);
    }

    private static void installLogCapture() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            logAppender = new LogCaptureAppender();
            logAppender.start();

            config.addAppender(logAppender);
            LoggerConfig root = config.getRootLogger();
            root.addAppender(logAppender, Level.ALL, null);
            ctx.updateLoggers();
        } catch (Exception e) {
            LOGGER.warn("Failed to install log capture appender", e);
        }
    }

    private static void uninstallLogCapture() {
        try {
            if (logAppender == null) {
                return;
            }
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig root = config.getRootLogger();
            root.removeAppender(logAppender.getName());
            logAppender.stop();
            ctx.updateLoggers();
        } catch (Exception e) {
            LOGGER.warn("Failed to uninstall log capture appender", e);
        }
    }

    /**
     * Static event handler for client tick events.
     * Registered with NeoForge event bus.
     */
    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientTickHandler {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            MainThreadExecutor.processPendingTasks();
        }
    }
}
