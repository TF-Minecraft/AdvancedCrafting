package net.tfminecraft.AdvancedCrafting.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;

import net.tfminecraft.AdvancedCrafting.Objects.Crafting.CraftingRecipe;
import net.tfminecraft.AdvancedCrafting.Objects.Data.CraftInput;
import net.tfminecraft.AdvancedCrafting.Objects.Data.StatData;
import net.tfminecraft.AdvancedCrafting.Objects.Stats.StatModifier;
import net.tfminecraft.AdvancedCrafting.Objects.Stats.StatTemplate;

public final class CraftStatCalculator {
	private CraftStatCalculator() {
	}

	public static StatData compute(CraftingRecipe recipe, List<CraftInput> inputs) {
		if (recipe == null) {
			return new StatData();
		}
		return applyPipeline(BucketStatAverager.computeFromInputs(inputs), recipe);
	}

	public static StatData compute(CraftingRecipe recipe, Map<String, Integer> materials) {
		if (recipe == null) {
			return new StatData();
		}
		return applyPipeline(BucketStatAverager.compute(materials), recipe);
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

	private static StatData applyPipeline(StatData stats, CraftingRecipe recipe) {
		return applyTemplate(stats, recipe);
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
