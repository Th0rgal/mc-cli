package dev.mccli.commands;

import com.google.gson.JsonObject;
import dev.mccli.util.MainThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            ClientLevel world = client.level;

            if (player == null || world == null) {
                throw new IllegalStateException("Player not in game");
            }

            HitResult hit = client.hitResult;
            if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
                JsonObject miss = new JsonObject();
                miss.addProperty("hit", false);
                return miss;
            }

            EntityHitResult entityHit = (EntityHitResult) hit;
            Entity entity = entityHit.getEntity();
            Vec3 playerPos = player.position();
            Vec3 entityPos = entity.position();

            if (playerPos.distanceTo(entityPos) > maxDistance) {
                JsonObject miss = new JsonObject();
                miss.addProperty("hit", false);
                return miss;
            }

            JsonObject result = new JsonObject();
            result.addProperty("hit", true);
            result.addProperty("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            result.addProperty("uuid", entity.getStringUUID());
            result.addProperty("name", entity.getDisplayName().getString());

            JsonObject pos = new JsonObject();
            pos.addProperty("x", entityPos.x);
            pos.addProperty("y", entityPos.y);
            pos.addProperty("z", entityPos.z);
            result.add("pos", pos);

            if (includeNbt) {
                // Note: Full NBT export not available on client side in 1.21.11
                // Provide basic entity info instead
                result.addProperty("nbt_error", "Entity NBT not available on client side in 1.21.11+");
            }

            return result;
        });
    }
}
