package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.ItemJson;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Item inspection command.
 *
 * Params:
 * - action: "hand" | "slot" (default: "hand")
 * - hand: "main" | "off" (default: "main")
 * - slot: slot index (for "slot" action)
 * - include_nbt: include NBT SNBT string (default: true)
 *
 * Response:
 * - item: serialized item data
 */
public class ItemCommand implements Command {
    @Override
    public String getName() {
        return "item";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "hand";
        boolean includeNbt = !params.has("include_nbt") || params.get("include_nbt").getAsBoolean();

        if (!List.of("hand", "slot").contains(action)) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
            return future;
        }

        if (action.equals("slot") && !params.has("slot")) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameter: slot"));
            return future;
        }

        return MainThreadExecutor.submit(() -> {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null) {
                throw new IllegalStateException("Player not in game");
            }

            ItemStack stack;
            if (action.equals("slot")) {
                int slot = params.get("slot").getAsInt();
                if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
                    throw new IllegalArgumentException("Invalid slot index: " + slot);
                }
                stack = player.getInventory().getItem(slot);
            } else {
                String hand = params.has("hand") ? params.get("hand").getAsString() : "main";
                stack = hand.equalsIgnoreCase("off") ? player.getOffhandItem() : player.getMainHandItem();
            }

            JsonObject result = new JsonObject();
            result.add("item", ItemJson.fromStack(stack, includeNbt));
            return result;
        });
    }
}
