package dev.mccli.server;

import dev.mccli.McCliMod;

import java.io.IOException;
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
 * Protocol: Newline-delimited JSON messages.
 */
public class SocketServer {
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final CommandDispatcher dispatcher = new CommandDispatcher();

    private ServerSocket serverSocket;
    private Thread serverThread;

    public SocketServer(int port) {
        this.port = port;
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
        try {
            serverSocket = new ServerSocket(port);
            McCliMod.LOGGER.info("MC-CLI server listening on port {}", port);

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
        } catch (IOException e) {
            if (running.get()) {
                McCliMod.LOGGER.error("Server socket error", e);
            }
        }
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
    }

    public void stop() {
        running.set(false);

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
