package net.tfminecraft.advancedcrafting.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;

import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.data.CraftInput;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;
import net.tfminecraft.advancedcrafting.objects.stats.StatTemplate;

public final class CraftStatCalculator {
	private CraftStatCalculator() {
	}

	public static StatData compute(CraftingRecipe recipe, List<CraftInput> inputs) {
		if (recipe == null) {
			return new StatData();
		}
		return compute(recipe, BucketStatAverager.toMaterials(inputs));
	}

	public static StatData compute(CraftingRecipe recipe, Map<String, Integer> materials) {
		if (recipe == null) {
			return new StatData();
		}
		StatData stats = BucketStatAverager.compute(materials);
		TierBonusCalculator.apply(stats, recipe.getStatTemplate(), materials);
		return applyTemplate(stats, recipe);
	}

	public static Set<String> collectManagedStatIds(CraftingRecipe recipe) {
		Set<String> ids = new HashSet<>();
		if (recipe == null) {
			return ids;
		}
		StatTemplate template = recipe.getStatTemplate();
		if (template != null) {
			for (String statId : template.getStats()) {
				ids.add(statId.toLowerCase());
			}
			for (StatModifier base : template.getBaseStats()) {
				ids.add(base.getType().toLowerCase());
			}
		}
		return ids;
	}

	private static StatData applyTemplate(StatData stats, CraftingRecipe recipe) {
		StatTemplate template = recipe.getStatTemplate();
		if (template == null) {
			Bukkit.getLogger().warning("AC: Recipe " + recipe.getId() + " has no valid stat template; crafting with no input stats");
			return new StatData();
		}
		return StatTemplateMath.filterAndApply(stats, template);
	}

}
