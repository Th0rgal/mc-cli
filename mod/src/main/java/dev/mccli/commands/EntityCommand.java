package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Entity probe command.
 *
 * Params:
 * - action: "target" (default: "target")
 * - max_distance: max allowed distance (default: 5.0)
 * - include_nbt: include entity NBT (default: false)
 *
 * Response:
 * - hit: boolean
 * - id: entity type id
 * - uuid: entity UUID
 * - name: display name
 * - pos: {x, y, z}
 * - nbt: SNBT string (if include_nbt)
 */
public class EntityCommand implements Command {
    @Override
    public String getName() {
        return "entity";
    }

    @Override
    public CompletableFuture<JsonObject> execute(JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString() : "target";
        double maxDistance = params.has("max_distance") ? params.get("max_distance").getAsDouble() : 5.0;
        boolean includeNbt = params.has("include_nbt") && params.get("include_nbt").getAsBoolean();

        if (!List.of("target").contains(action)) {
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Unknown action: " + action));
            return future;
        }

        return MainThreadExecutor.submit(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            ClientWorld world = client.world;

            if (player == null || world == null) {
                throw new IllegalStateException("Player not in game");
            }

            HitResult hit = client.crosshairTarget;
            if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
                JsonObject miss = new JsonObject();
                miss.addProperty("hit", false);
                return miss;
            }

            EntityHitResult entityHit = (EntityHitResult) hit;
            Entity entity = entityHit.getEntity();
            Vec3d playerPos = player.getPos();
            Vec3d entityPos = entity.getPos();

            if (playerPos.distanceTo(entityPos) > maxDistance) {
                JsonObject miss = new JsonObject();
                miss.addProperty("hit", false);
                return miss;
            }

            JsonObject result = new JsonObject();
            result.addProperty("hit", true);
            result.addProperty("id", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
            result.addProperty("uuid", entity.getUuidAsString());
            result.addProperty("name", entity.getDisplayName().getString());

            JsonObject pos = new JsonObject();
            pos.addProperty("x", entityPos.x);
            pos.addProperty("y", entityPos.y);
            pos.addProperty("z", entityPos.z);
            result.add("pos", pos);

            if (includeNbt) {
                NbtCompound nbt = new NbtCompound();
                entity.writeNbt(nbt);
                result.addProperty("nbt", nbt.toString());
            }

            return result;
        });
    }
}
