package net.tfminecraft.advancedcrafting.utils;

import org.bukkit.NamespacedKey;

import net.tfminecraft.advancedcrafting.AdvancedCrafting;

public final class PDCKeys {
	private PDCKeys() {
	}

	public static NamespacedKey craftRecipe() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_craft_recipe");
	}

	public static NamespacedKey craftQuality() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_craft_quality");
	}

	public static NamespacedKey craftInputs() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_craft_inputs");
	}

	public static NamespacedKey craftStatTemplateRevision() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_craft_stat_template_revision");
	}

	public static NamespacedKey ingredientId() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_ingredient_id");
	}

	public static NamespacedKey alloyId() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_alloy_id");
	}

	public static NamespacedKey itemRevision() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_item_revision");
	}

	public static NamespacedKey loreStart() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_lore_start");
	}

	public static NamespacedKey loreLen() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_lore_len");
	}

	public static NamespacedKey statsLore() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_stats_lore");
	}

	public static NamespacedKey craftTierLoreStart() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_craft_tier_lore_start");
	}

	public static NamespacedKey craftMajorityTier() {
		return new NamespacedKey(AdvancedCrafting.plugin, "ac_craft_majority_tier");
	}
}
