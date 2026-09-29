package dev.mccli.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;

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

        // Custom model data (1.21.11: record of floats/flags/strings/colors - use toString)
        CustomModelDataComponent customModelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (customModelData != null) {
            item.addProperty("custom_model_data", customModelData.toString());
        }

        // Include full component string if requested
        if (includeNbt) {
            item.addProperty("components", stack.getComponents().toString());
        }

        // Enchantments
        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            JsonArray enchantmentsArray = new JsonArray();
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                JsonObject enchant = new JsonObject();
                // Handles both Reference (has ID) and Direct (no ID) registry entries
                enchant.addProperty("id", entry.getIdAsString());
                enchant.addProperty("level", enchantments.getLevel(entry));
                enchantmentsArray.add(enchant);
            }
            item.add("enchantments", enchantmentsArray);
        }

        return item;
    }
}
