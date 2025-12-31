package dev.mccli.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;

import java.util.Map;

/**
 * Utility for serializing ItemStack data to JSON.
 */
public final class ItemJson {
    private ItemJson() {}

    public static JsonObject fromStack(ItemStack stack, boolean includeNbt) {
        JsonObject item = new JsonObject();

        if (stack == null || stack.isEmpty()) {
            item.addProperty("empty", true);
            return item;
        }

        item.addProperty("empty", false);
        item.addProperty("id", Registries.ITEM.getId(stack.getItem()).toString());
        item.addProperty("name", stack.getName().getString());
        item.addProperty("count", stack.getCount());
        item.addProperty("max_count", stack.getMaxCount());

        if (stack.isDamageable()) {
            item.addProperty("damage", stack.getDamage());
            item.addProperty("max_damage", stack.getMaxDamage());
            item.addProperty("durability", stack.getMaxDamage() - stack.getDamage());
        }

        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains("CustomModelData", NbtElement.NUMBER_TYPE)) {
            item.addProperty("custom_model_data", nbt.getInt("CustomModelData"));
        }

        if (includeNbt && nbt != null && !nbt.isEmpty()) {
            item.addProperty("nbt", nbt.toString());
        }

        Map<Enchantment, Integer> enchants = EnchantmentHelper.get(stack);
        if (!enchants.isEmpty()) {
            JsonArray enchantments = new JsonArray();
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                JsonObject enchant = new JsonObject();
                enchant.addProperty("id", Registries.ENCHANTMENT.getId(entry.getKey()).toString());
                enchant.addProperty("level", entry.getValue());
                enchantments.add(enchant);
            }
            item.add("enchantments", enchantments);
        }

        return item;
    }
}
