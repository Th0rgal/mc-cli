package dev.mccli.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;

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
        item.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        item.addProperty("name", stack.getHoverName().getString());
        item.addProperty("count", stack.getCount());
        item.addProperty("max_count", stack.getMaxStackSize());

        if (stack.isDamageableItem()) {
            item.addProperty("damage", stack.getDamageValue());
            item.addProperty("max_damage", stack.getMaxDamage());
            item.addProperty("durability", stack.getMaxDamage() - stack.getDamageValue());
        }

        // Custom model data (1.21.1: simple integer value)
        CustomModelData customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (customModelData != null) {
            item.addProperty("custom_model_data", customModelData.value());
        }

        // Include full component string if requested
        if (includeNbt) {
            item.addProperty("components", stack.getComponents().toString());
        }

        // Enchantments
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            JsonArray enchantmentsArray = new JsonArray();
            for (Holder<Enchantment> entry : enchantments.keySet()) {
                JsonObject enchant = new JsonObject();
                // Handle both Reference (has ID) and Direct (no ID) registry entries
                String enchantId = entry.unwrapKey()
                    .map(key -> key.location().toString())
                    .orElseGet(() -> "unknown");
                enchant.addProperty("id", enchantId);
                enchant.addProperty("level", enchantments.getLevel(entry));
                enchantmentsArray.add(enchant);
            }
            item.add("enchantments", enchantmentsArray);
        }

        return item;
    }
}
