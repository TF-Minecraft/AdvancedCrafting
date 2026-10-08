package net.tfminecraft.advancedcrafting.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.data.CraftInput;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.objects.schemes.ModelScheme;

/**
 * Picks the model scheme whose look a crafted item wears: the main material's scheme, unless that scheme
 * has no model for the recipe and a material of the recipe's model type brings its own. Alloys use their
 * base's scheme, so an alloy piece wears (and is tagged with) its base metal.
 */
public final class ModelSchemeResolver {
	private ModelSchemeResolver() {
	}

	/** The scheme of a recorded craft, or null when the recipe or inputs are unknown. */
	public static ModelScheme resolve(CraftingRecipe recipe, List<CraftInput> inputs) {
		if (recipe == null || inputs == null || inputs.isEmpty()) {
			return null;
		}
		HashMap<String, Integer> materials = new HashMap<>();
		for (CraftInput input : inputs) {
			materials.put(input.getKind().toLowerCase() + "." + input.getId().toLowerCase(), input.getAmount());
		}
		ModelScheme main = schemeOf(MajorityTierResolver.resolveMajorityKey(recipe, materials), null, null);
		return forModel(recipe, main, materials);
	}

	/** A recipe-specific model on the main material wins over the secondary model ingredient. */
	public static ModelScheme forModel(CraftingRecipe recipe, ModelScheme scheme, Map<String, Integer> materials) {
		if (scheme == null || scheme.getModel(recipe.getId()) != null
				|| recipe.getModelType().equalsIgnoreCase("none")) {
			return scheme;
		}
		for (String key : materials.keySet()) {
			scheme = schemeOf(key, recipe.getModelType(), scheme);
		}
		return scheme;
	}

	/** The scheme of one material key if it exists and (when given) has the wanted type, else the fallback. */
	private static ModelScheme schemeOf(String key, String wantedType, ModelScheme fallback) {
		String[] split = key.split("\\.");
		if (split.length < 2) {
			return fallback;
		}
		if (split[0].equalsIgnoreCase("ingredient")) {
			Ingredient ing = IngredientLoader.getByString(split[1]);
			if (ing != null && (wantedType == null
					|| ing.getIngredientData().getType().getId().equalsIgnoreCase(wantedType))) {
				return ing.getIngredientData().getModelScheme();
			}
		} else if (split[0].equalsIgnoreCase("alloy")) {
			Alloy a = AlloyManager.getAlloyById(split[1]);
			if (a != null && (wantedType == null || a.getData().getType().getId().equalsIgnoreCase(wantedType))) {
				return a.getData().getModelScheme();
			}
		}
		return fallback;
	}
}
