package dev.mccli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mccli.util.ItemJson;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Inventory inspection command.
 *
 * Params:
 * - action: "list" (default: "list")
 * - section: "hotbar" | "main" | "armor" | "offhand" (optional)
 * - include_empty: include empty slots (default: false)
 * - include_nbt: include NBT SNBT string (default: false)
 *
 * Response:
 * - items: array of {slot, slot_type, item}
 * - count: number of returned slots
 */
public class InventoryCommand implements Command {
    @Override
    public String getName() {
        return "inventory";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "list";
        boolean includeEmpty = params.has("include_empty") && params.get("include_empty").getAsBoolean();
        boolean includeNbt = params.has("include_nbt") && params.get("include_nbt").getAsBoolean();
        String section = params.has("section") ? params.get("section").getAsString() : null;

        if (!List.of("list").contains(action)) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
            return future;
        }

        return MainThreadExecutor.submit(() -> {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null) {
                throw new IllegalStateException("Player not in game");
            }

            Inventory inv = player.getInventory();
            JsonArray items = new JsonArray();

            if (section == null || section.equalsIgnoreCase("hotbar") || section.equalsIgnoreCase("main")) {
                int start = 0;
                int end = 36;
                if ("hotbar".equalsIgnoreCase(section)) {
                    end = 9;
                } else if ("main".equalsIgnoreCase(section)) {
                    start = 9;
                }

                for (int slot = start; slot < end; slot++) {
                    ItemStack stack = inv.getItem(slot);
                    if (!includeEmpty && stack.isEmpty()) {
                        continue;
                    }
                    JsonObject entry = new JsonObject();
                    entry.addProperty("slot", slot);
                    entry.addProperty("slot_type", slot < 9 ? "hotbar" : "main");
                    entry.add("item", ItemJson.fromStack(stack, includeNbt));
                    items.add(entry);
                }
            }

            // Armor slots (slots 36-39 in player inventory)
            if (section == null || section.equalsIgnoreCase("armor")) {
                // Armor slots are 36-39 (feet=36, legs=37, chest=38, head=39)
                for (int i = 0; i < 4; i++) {
                    int armorSlot = 36 + i;
                    ItemStack stack = inv.getItem(armorSlot);
                    if (!includeEmpty && stack.isEmpty()) {
                        continue;
                    }
                    JsonObject entry = new JsonObject();
                    entry.addProperty("slot", i);
                    entry.addProperty("slot_type", "armor");
                    entry.add("item", ItemJson.fromStack(stack, includeNbt));
                    items.add(entry);
                }
            }

            // Offhand slot (slot 40)
            if (section == null || section.equalsIgnoreCase("offhand")) {
                ItemStack stack = inv.getItem(Inventory.SLOT_OFFHAND);
                if (includeEmpty || !stack.isEmpty()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("slot", 0);
                    entry.addProperty("slot_type", "offhand");
                    entry.add("item", ItemJson.fromStack(stack, includeNbt));
                    items.add(entry);
                }
            }

            JsonObject result = new JsonObject();
            result.add("items", items);
            result.addProperty("count", items.size());
            return result;
        });
    }
}
