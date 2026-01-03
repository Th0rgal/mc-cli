package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.McCliMod;
import dev.mccli.util.ItemJson;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Player interaction command for using items, placing blocks, and manipulating inventory.
 *
 * Actions:
 * - "use": Right-click with held item (triggers item use / block placement)
 * - "use_on_block": Right-click on a specific block or targeted block
 * - "attack": Left-click (attack/break)
 * - "drop": Drop item(s) from inventory
 * - "swap": Swap items between slots
 * - "select": Select hotbar slot
 *
 * Params vary by action - see individual action handlers.
 */
public class InteractCommand implements Command {
    @Override
    public String getName() {
        return "interact";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "use";

        if (!List.of("use", "use_on_block", "attack", "drop", "swap", "select").contains(action)) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
            return future;
        }

        return MainThreadExecutor.submit(() -> {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            ClientLevel world = client.level;
            MultiPlayerGameMode interactionManager = client.gameMode;

            if (player == null || world == null || interactionManager == null) {
                throw new IllegalStateException("Player not in game");
            }

            return switch (action) {
                case "use" -> handleUse(params, client, player, interactionManager);
                case "use_on_block" -> handleUseOnBlock(params, client, player, world, interactionManager);
                case "attack" -> handleAttack(params, client, player, world, interactionManager);
                case "drop" -> handleDrop(params, player, interactionManager);
                case "swap" -> handleSwap(params, player, interactionManager);
                case "select" -> handleSelect(params, player);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            };
        });
    }

    /**
     * Use item in hand (right-click in air).
     *
     * Params:
     * - hand: "main" | "off" (default: "main")
     *
     * Response:
     * - result: action result
     * - item: the item that was used
     */
    private JsonObject handleUse(JsonObject params, Minecraft client, LocalPlayer player,
                                  MultiPlayerGameMode interactionManager) {
        InteractionHand hand = getHand(params);
        ItemStack stack = player.getItemInHand(hand);

        InteractionResult result = interactionManager.useItem(player, hand);

        JsonObject response = new JsonObject();
        response.addProperty("result", result.toString());
        response.add("item", ItemJson.fromStack(stack, false));
        return response;
    }

    /**
     * Use item on a block (right-click on block).
     *
     * Params:
     * - hand: "main" | "off" (default: "main")
     * - x, y, z: target block position (optional, uses crosshair target if not specified)
     * - face: block face to click on: "up" | "down" | "north" | "south" | "east" | "west" (default: "up")
     * - inside_block: whether click position is inside the block (default: false)
     *
     * Response:
     * - result: action result
     * - item: the item that was used
     * - block_pos: the block position interacted with
     */
    private JsonObject handleUseOnBlock(JsonObject params, Minecraft client, LocalPlayer player,
                                         ClientLevel world, MultiPlayerGameMode interactionManager) {
        InteractionHand hand = getHand(params);
        ItemStack stack = player.getItemInHand(hand);

        BlockPos targetPos;
        Direction face;
        Vec3 hitPos;

        if (params.has("x") && params.has("y") && params.has("z")) {
            // Use specified coordinates
            int x = params.get("x").getAsInt();
            int y = params.get("y").getAsInt();
            int z = params.get("z").getAsInt();
            targetPos = new BlockPos(x, y, z);
            face = getDirection(params);
            // Hit position on the face
            var normal = face.getNormal();
            hitPos = Vec3.atCenterOf(targetPos).add(normal.getX() * 0.5, normal.getY() * 0.5, normal.getZ() * 0.5);
        } else {
            // Use crosshair target
            HitResult hitResult = player.pick(5.0, 0.0f, false);
            if (hitResult.getType() != HitResult.Type.BLOCK) {
                throw new IllegalStateException("No block targeted - provide x, y, z or look at a block");
            }
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            targetPos = blockHit.getBlockPos();
            face = blockHit.getDirection();
            hitPos = blockHit.getLocation();
        }

        boolean insideBlock = params.has("inside_block") && params.get("inside_block").getAsBoolean();

        BlockHitResult blockHitResult = new BlockHitResult(hitPos, face, targetPos, insideBlock);
        InteractionResult result = interactionManager.useItemOn(player, hand, blockHitResult);

        // Swing hand on successful interaction (matches vanilla behavior)
        if (result.consumesAction()) {
            player.swing(hand);
        } else if (result == InteractionResult.PASS) {
            // Also call useItem to match vanilla behavior for item-based placement
            // This is called when useItemOn returns PASS (block didn't handle it)
            ItemStack stack2 = player.getItemInHand(hand);
            if (!stack2.isEmpty()) {
                InteractionResult itemResult = interactionManager.useItem(player, hand);
                if (itemResult.consumesAction()) {
                    player.swing(hand);
                    result = itemResult;
                }
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("result", result.toString());
        response.add("item", ItemJson.fromStack(stack, false));

        JsonObject blockPosJson = new JsonObject();
        blockPosJson.addProperty("x", targetPos.getX());
        blockPosJson.addProperty("y", targetPos.getY());
        blockPosJson.addProperty("z", targetPos.getZ());
        response.add("block_pos", blockPosJson);

        return response;
    }

    /**
     * Attack / left-click action.
     *
     * Params:
     * - target: "block" | "air" (default: "air" - swings arm)
     * - x, y, z: block position (for target="block", optional - uses crosshair if not specified)
     * - face: block face: "up" | "down" | "north" | "south" | "east" | "west" (default: "up")
     *
     * Response:
     * - result: action result
     */
    private JsonObject handleAttack(JsonObject params, Minecraft client, LocalPlayer player,
                                     ClientLevel world, MultiPlayerGameMode interactionManager) {
        String target = params.has("target") ? params.get("target").getAsString() : "air";

        JsonObject response = new JsonObject();

        if (target.equals("block")) {
            BlockPos targetPos;
            Direction face;

            if (params.has("x") && params.has("y") && params.has("z")) {
                int x = params.get("x").getAsInt();
                int y = params.get("y").getAsInt();
                int z = params.get("z").getAsInt();
                targetPos = new BlockPos(x, y, z);
                face = getDirection(params);
            } else {
                HitResult hitResult = player.pick(5.0, 0.0f, false);
                if (hitResult.getType() != HitResult.Type.BLOCK) {
                    throw new IllegalStateException("No block targeted - provide x, y, z or look at a block");
                }
                BlockHitResult blockHit = (BlockHitResult) hitResult;
                targetPos = blockHit.getBlockPos();
                face = blockHit.getDirection();
            }

            boolean success = interactionManager.startDestroyBlock(targetPos, face);
            response.addProperty("result", success ? "SUCCESS" : "FAIL");

            JsonObject blockPosJson = new JsonObject();
            blockPosJson.addProperty("x", targetPos.getX());
            blockPosJson.addProperty("y", targetPos.getY());
            blockPosJson.addProperty("z", targetPos.getZ());
            response.add("block_pos", blockPosJson);
        } else {
            // Swing arm in air
            player.swing(InteractionHand.MAIN_HAND);
            response.addProperty("result", "SWING");
        }

        return response;
    }

    /**
     * Drop item(s) from inventory.
     *
     * Params:
     * - slot: inventory slot to drop from (default: currently held slot)
     * - all: drop entire stack (default: false, drops single item)
     *
     * Response:
     * - dropped: item that was dropped
     * - count: number of items dropped
     */
    private JsonObject handleDrop(JsonObject params, LocalPlayer player,
                                   MultiPlayerGameMode interactionManager) {
        Inventory inv = player.getInventory();
        int slot;

        if (params.has("slot")) {
            slot = params.get("slot").getAsInt();
            if (slot < 0 || slot >= inv.getContainerSize()) {
                throw new IllegalArgumentException("Invalid slot index: " + slot);
            }
        } else {
            slot = inv.selected;
        }

        boolean dropAll = params.has("all") && params.get("all").getAsBoolean();
        ItemStack stackBefore = inv.getItem(slot).copy();

        if (stackBefore.isEmpty()) {
            JsonObject response = new JsonObject();
            response.addProperty("dropped", false);
            response.addProperty("reason", "Slot is empty");
            return response;
        }

        // Use clickSlot with DROP action
        // For hotbar slots (0-8), the slot ID in the player screen handler is 36 + slot
        // For main inventory (9-35), slot ID is the same
        int screenSlot;
        if (slot < 9) {
            screenSlot = 36 + slot; // Hotbar is at the bottom of player inventory screen
        } else if (slot < 36) {
            screenSlot = slot; // Main inventory
        } else if (slot < 40) {
            // Armor slots: PlayerInventory uses feet(36), legs(37), chest(38), head(39)
            // PlayerScreenHandler uses head(5), chest(6), legs(7), feet(8)
            // So we need to invert: 8 - (slot - 36)
            screenSlot = 8 - (slot - 36); // Armor slots (36->8, 37->7, 38->6, 39->5)
        } else {
            screenSlot = 45; // Offhand
        }

        ClickType actionType = dropAll ? ClickType.THROW : ClickType.THROW;
        int button = dropAll ? 1 : 0; // 0 = drop one, 1 = drop stack

        interactionManager.handleInventoryMouseClick(player.containerMenu.containerId, screenSlot, button, actionType, player);

        int droppedCount = dropAll ? stackBefore.getCount() : 1;

        JsonObject response = new JsonObject();
        response.addProperty("dropped", true);
        response.add("item", ItemJson.fromStack(stackBefore, false));
        response.addProperty("count", droppedCount);
        return response;
    }

    /**
     * Swap items between slots.
     *
     * Params:
     * - from_slot: source slot
     * - to_slot: destination slot
     *
     * Response:
     * - success: boolean
     * - from_item: item that was in from_slot
     * - to_item: item that was in to_slot
     */
    private JsonObject handleSwap(JsonObject params, LocalPlayer player,
                                   MultiPlayerGameMode interactionManager) {
        if (!params.has("from_slot") || !params.has("to_slot")) {
            throw new IllegalArgumentException("Missing required parameters: from_slot, to_slot");
        }

        int fromSlot = params.get("from_slot").getAsInt();
        int toSlot = params.get("to_slot").getAsInt();

        Inventory inv = player.getInventory();
        if (fromSlot < 0 || fromSlot >= inv.getContainerSize()) {
            throw new IllegalArgumentException("Invalid from_slot index: " + fromSlot);
        }
        if (toSlot < 0 || toSlot >= inv.getContainerSize()) {
            throw new IllegalArgumentException("Invalid to_slot index: " + toSlot);
        }

        ItemStack fromStack = inv.getItem(fromSlot).copy();
        ItemStack toStack = inv.getItem(toSlot).copy();

        // Convert to screen handler slot IDs
        int fromScreenSlot = inventoryToScreenSlot(fromSlot);
        int toScreenSlot = inventoryToScreenSlot(toSlot);

        // Pick up from source
        interactionManager.handleInventoryMouseClick(player.containerMenu.containerId, fromScreenSlot, 0, ClickType.PICKUP, player);
        // Place at destination (picks up what's there)
        interactionManager.handleInventoryMouseClick(player.containerMenu.containerId, toScreenSlot, 0, ClickType.PICKUP, player);
        // Place original destination item at source (if any)
        if (!toStack.isEmpty()) {
            interactionManager.handleInventoryMouseClick(player.containerMenu.containerId, fromScreenSlot, 0, ClickType.PICKUP, player);
        }

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.add("from_item", ItemJson.fromStack(fromStack, false));
        response.add("to_item", ItemJson.fromStack(toStack, false));
        return response;
    }

    /**
     * Select hotbar slot.
     *
     * Params:
     * - slot: hotbar slot (0-8)
     *
     * Response:
     * - slot: selected slot
     * - item: item in that slot
     */
    private JsonObject handleSelect(JsonObject params, LocalPlayer player) {
        if (!params.has("slot")) {
            throw new IllegalArgumentException("Missing required parameter: slot");
        }

        int slot = params.get("slot").getAsInt();
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("Hotbar slot must be 0-8, got: " + slot);
        }

        player.getInventory().selected = slot;

        JsonObject response = new JsonObject();
        response.addProperty("slot", slot);
        response.add("item", ItemJson.fromStack(player.getMainHandItem(), false));
        return response;
    }

    private InteractionHand getHand(JsonObject params) {
        String handStr = params.has("hand") ? params.get("hand").getAsString() : "main";
        return handStr.equalsIgnoreCase("off") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private Direction getDirection(JsonObject params) {
        String faceStr = params.has("face") ? params.get("face").getAsString() : "up";
        return switch (faceStr.toLowerCase()) {
            case "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> Direction.UP;
        };
    }

    private int inventoryToScreenSlot(int inventorySlot) {
        if (inventorySlot < 9) {
            return 36 + inventorySlot; // Hotbar
        } else if (inventorySlot < 36) {
            return inventorySlot; // Main inventory
        } else if (inventorySlot < 40) {
            // Armor: invert mapping (see handleDrop for detailed comment)
            return 8 - (inventorySlot - 36); // Armor (36->8, 37->7, 38->6, 39->5)
        } else {
            return 45; // Offhand
        }
    }
}
