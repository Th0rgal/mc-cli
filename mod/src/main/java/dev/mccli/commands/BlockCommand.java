package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Block probe command.
 *
 * Params:
 * - action: "target" | "at" (default: "target")
 * - max_distance: max raycast distance (default: 5.0)
 * - x, y, z: block position (for "at")
 * - include_nbt: include block entity NBT (default: false)
 *
 * Response:
 * - hit: boolean (for target)
 * - id: block id
 * - pos: {x, y, z}
 * - properties: state properties
 * - block_entity: {id, nbt} (if present and include_nbt)
 */
public class BlockCommand implements Command {
    @Override
    public String getName() {
        return "block";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "target";
        double maxDistance = params.has("max_distance") ? params.get("max_distance").getAsDouble() : 5.0;
        boolean includeNbt = params.has("include_nbt") && params.get("include_nbt").getAsBoolean();

        if (!List.of("target", "at").contains(action)) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
            return future;
        }

        if (action.equals("at") && (!params.has("x") || !params.has("y") || !params.has("z"))) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Missing required parameters: x, y, z"));
            return future;
        }

        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            ClientWorld world = client.world;

            if (player == null || world == null) {
                throw new IllegalStateException("Player not in game");
            }

            BlockPos pos;
            boolean hit = true;

            if (action.equals("target")) {
                HitResult hitResult = player.raycast(maxDistance, 0.0f, false);
                if (hitResult.getType() != HitResult.Type.BLOCK) {
                    JsonObject miss = new JsonObject();
                    miss.addProperty("hit", false);
                    return miss;
                }
                BlockHitResult blockHit = (BlockHitResult) hitResult;
                pos = blockHit.getBlockPos();
            } else {
                pos = new BlockPos(params.get("x").getAsInt(), params.get("y").getAsInt(), params.get("z").getAsInt());
            }

            BlockState state = world.getBlockState(pos);
            JsonObject result = new JsonObject();
            if (action.equals("target")) {
                result.addProperty("hit", hit);
            }
            result.addProperty("id", Registries.BLOCK.getId(state.getBlock()).toString());

            JsonObject posJson = new JsonObject();
            posJson.addProperty("x", pos.getX());
            posJson.addProperty("y", pos.getY());
            posJson.addProperty("z", pos.getZ());
            result.add("pos", posJson);

            JsonObject props = new JsonObject();
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
                Property<?> property = entry.getKey();
                props.addProperty(property.getName(), entry.getValue().toString());
            }
            result.add("properties", props);

            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                JsonObject be = new JsonObject();
                var blockEntityTypeId = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
                be.addProperty("id", blockEntityTypeId != null ? blockEntityTypeId.toString() : "unknown");
                if (includeNbt) {
                    try {
                        NbtCompound nbt = blockEntity.createNbtWithIdentifyingData(world.getRegistryManager());
                        be.addProperty("nbt", nbt.toString());
                    } catch (Exception e) {
                        be.addProperty("nbt_error", e.getMessage());
                    }
                }
                result.add("block_entity", be);
            }

            return result;
        });
    }
}
