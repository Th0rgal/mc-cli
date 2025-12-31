package dev.mccli.util;

import dev.mccli.McCliMod;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Executes tasks on Minecraft's main thread.
 *
 * Minecraft is single-threaded - all game state must be accessed/modified on the main thread.
 * This class provides a thread-safe way to queue tasks from the socket handler threads
 * and execute them during the game tick.
 *
 * Usage:
 *   CompletableFuture<JsonObject> future = MainThreadExecutor.submit(() -> {
 *       // This runs on main thread
 *       MinecraftClient client = MinecraftClient.getInstance();
 *       return createResult(client.player.getPos());
 *   });
 */
public class MainThreadExecutor {
    private static final ConcurrentLinkedQueue<Task<?>> taskQueue = new ConcurrentLinkedQueue<>();

    private static class Task<T> {
        final Supplier<T> supplier;
        final CompletableFuture<T> future;

        Task(Supplier<T> supplier, CompletableFuture<T> future) {
            this.supplier = supplier;
            this.future = future;
        }
    }

    /**
     * Submit a task to run on the main thread.
     *
     * @param supplier The task to execute
     * @return Future that completes with the result
     */
    public static <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        taskQueue.add(new Task<>(supplier, future));
        return future;
    }

    /**
     * Submit a void task to run on the main thread.
     *
     * @param runnable The task to execute
     * @return Future that completes when done
     */
    public static CompletableFuture<Void> submitVoid(Runnable runnable) {
        return submit(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Process all pending tasks. Called from the client tick event.
     */
    public static void processPendingTasks() {
        Task<?> task;
        int processed = 0;

        while ((task = taskQueue.poll()) != null) {
            processTask(task);
            processed++;

            // Limit tasks per tick to avoid freezing
            if (processed >= 10) {
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void processTask(Task<T> task) {
        try {
            T result = task.supplier.get();
            task.future.complete(result);
        } catch (Exception e) {
            McCliMod.LOGGER.error("Task execution failed", e);
            task.future.completeExceptionally(e);
        }
    }
}
