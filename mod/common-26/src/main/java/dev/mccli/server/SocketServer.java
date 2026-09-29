package dev.mccli.server;

import dev.mccli.McCliMod;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP server that accepts client connections.
 *
 * Runs on a daemon thread to avoid blocking the game. Each client connection
 * is handled by a separate ClientHandler thread.
 *
 * Supports dynamic port allocation: if the default port is in use,
 * it will try subsequent ports until one is available.
 *
 * Protocol: Newline-delimited JSON messages.
 */
public class SocketServer {
    private final int basePort;
    private final int maxPortAttempts;
    private int boundPort = -1;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final CommandDispatcher dispatcher = new CommandDispatcher();

    private ServerSocket serverSocket;
    private Thread serverThread;

    public SocketServer(int port) {
        this(port, 10);
    }

    public SocketServer(int port, int maxPortAttempts) {
        this.basePort = port;
        this.maxPortAttempts = maxPortAttempts;
    }

    /**
     * Get the port the server is actually bound to.
     * Returns -1 if not yet bound.
     */
    public int getBoundPort() {
        return boundPort;
    }

    public void start() {
        if (running.getAndSet(true)) {
            McCliMod.LOGGER.warn("Server already running");
            return;
        }

        serverThread = new Thread(this::acceptConnections, "MC-CLI-Server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void acceptConnections() {
        // Try to bind to ports starting from basePort
        serverSocket = bindToAvailablePort();
        if (serverSocket == null) {
            McCliMod.LOGGER.error("Failed to bind to any port in range {}-{}",
                basePort, basePort + maxPortAttempts - 1);
            running.set(false);
            return;
        }

        boundPort = serverSocket.getLocalPort();
        McCliMod.LOGGER.info("MC-CLI server listening on port {}", boundPort);

        // Register in instance registry
        String instanceName = InstanceRegistry.generateInstanceName();
        InstanceRegistry.register(instanceName, boundPort);

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                McCliMod.LOGGER.debug("Client connected: {}", clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket, dispatcher, this);
                clients.add(handler);
                handler.start();
            } catch (IOException e) {
                if (running.get()) {
                    McCliMod.LOGGER.error("Error accepting connection", e);
                }
            }
        }
    }

    /**
     * Try to bind to an available port, starting from basePort.
     */
    private ServerSocket bindToAvailablePort() {
        for (int i = 0; i < maxPortAttempts; i++) {
            int port = basePort + i;
            try {
                ServerSocket socket = new ServerSocket(port);
                if (i > 0) {
                    McCliMod.LOGGER.info("Port {} was in use, bound to port {} instead",
                        basePort, port);
                }
                return socket;
            } catch (BindException e) {
                McCliMod.LOGGER.debug("Port {} is in use, trying next port", port);
            } catch (IOException e) {
                McCliMod.LOGGER.warn("Error binding to port {}: {}", port, e.getMessage());
            }
        }
        return null;
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
    }

    public void stop() {
        running.set(false);

        // Unregister from instance registry
        if (boundPort > 0) {
            InstanceRegistry.unregister(boundPort);
        }

        // Close all client connections
        for (ClientHandler client : clients) {
            client.stop();
        }
        clients.clear();

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                McCliMod.LOGGER.error("Error closing server socket", e);
            }
        }

        McCliMod.LOGGER.info("MC-CLI server stopped");
    }
}
