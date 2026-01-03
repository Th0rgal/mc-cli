package dev.mccli.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mccli.McCliMod;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles communication with a single connected client.
 *
 * Protocol:
 * - Request: {"id": "1", "command": "status", "params": {}}
 * - Response: {"id": "1", "success": true, "data": {...}}
 *
 * Each request-response is a single JSON line (newline-delimited).
 */
public class ClientHandler {
    private static final Gson GSON = new Gson();

    private final Socket socket;
    private final CommandDispatcher dispatcher;
    private final SocketServer server;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread handlerThread;
    private PrintWriter out;

    public ClientHandler(Socket socket, CommandDispatcher dispatcher, SocketServer server) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.server = server;
    }

    public void start() {
        running.set(true);
        handlerThread = new Thread(this::handleClient, "MC-CLI-Client-" + socket.getRemoteSocketAddress());
        handlerThread.setDaemon(true);
        handlerThread.start();
    }

    private void handleClient() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            this.out = writer;

            String line;
            while (running.get() && (line = in.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                processRequest(line);
            }
        } catch (IOException e) {
            if (running.get()) {
                McCliMod.LOGGER.debug("Client disconnected: {}", socket.getRemoteSocketAddress());
            }
        } finally {
            stop();
            server.removeClient(this);
        }
    }

    private void processRequest(String requestJson) {
        String requestId = null;

        try {
            JsonObject request = JsonParser.parseString(requestJson).getAsJsonObject();
            requestId = request.has("id") ? request.get("id").getAsString() : null;
            String command = request.get("command").getAsString();
            JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();

            final String finalRequestId = requestId;
            dispatcher.dispatch(command, params)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        sendError(finalRequestId, "EXECUTION_ERROR", error.getMessage());
                    } else {
                        sendSuccess(finalRequestId, result);
                    }
                });

        } catch (Exception e) {
            McCliMod.LOGGER.error("Error processing request: {}", requestJson, e);
            sendError(requestId, "PARSE_ERROR", e.getMessage());
        }
    }

    private void sendSuccess(String requestId, JsonObject data) {
        JsonObject response = new JsonObject();
        if (requestId != null) {
            response.addProperty("id", requestId);
        }
        response.addProperty("success", true);
        response.add("data", data != null ? data : new JsonObject());
        sendResponse(response);
    }

    private void sendError(String requestId, String code, String message) {
        JsonObject response = new JsonObject();
        if (requestId != null) {
            response.addProperty("id", requestId);
        }
        response.addProperty("success", false);

        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);

        sendResponse(response);
    }

    private void sendResponse(JsonObject response) {
        if (out != null) {
            synchronized (out) {
                out.println(GSON.toJson(response));
            }
        }
    }

    public void stop() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                McCliMod.LOGGER.error("Error closing client socket", e);
            }
        }
    }
}
