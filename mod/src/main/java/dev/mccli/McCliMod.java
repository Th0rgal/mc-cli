package dev.mccli;

import dev.mccli.server.SocketServer;
import dev.mccli.util.LogCaptureAppender;
import dev.mccli.util.MainThreadExecutor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
public class McCliMod implements ClientModInitializer {
    public static final String MOD_ID = "mccli";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int DEFAULT_PORT = 25580;

    private SocketServer server;
    private LogCaptureAppender logAppender;

    @Override
    public void onInitializeClient() {
        LOGGER.info("MC-CLI initializing...");

        // Register tick handler for main thread execution
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MainThreadExecutor.processPendingTasks();
        });

        // Install log capture appender
        installLogCapture();

        // Start TCP server
        server = new SocketServer(DEFAULT_PORT);
        server.start();

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                server.stop();
            }
            uninstallLogCapture();
        }));

        LOGGER.info("MC-CLI ready on port {}", DEFAULT_PORT);
    }

    private void installLogCapture() {
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

    private void uninstallLogCapture() {
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
}
