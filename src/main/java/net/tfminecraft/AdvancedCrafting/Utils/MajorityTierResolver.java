package net.tfminecraft.AdvancedCrafting.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.AdvancedCrafting.Loaders.IngredientLoader;
import net.tfminecraft.AdvancedCrafting.Managers.AlloyManager;
import net.tfminecraft.AdvancedCrafting.Objects.Alloys.Alloy;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.CraftingRecipe;
import net.tfminecraft.AdvancedCrafting.Objects.Data.CraftInput;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;

public final class MajorityTierResolver {
	private MajorityTierResolver() {
	}

	public static String resolveMajorityKey(CraftingRecipe recipe, Map<String, Integer> materials) {
		if (recipe == null || materials == null || materials.isEmpty()) {
			return "";
		}
		String max = "";
		int prev = 0;
		for (String key : materials.keySet()) {
			if (!isMainTypeMaterial(recipe, key)) {
				continue;
			}
			int amount = materials.get(key);
			if (amount > prev) {
				prev = amount;
				max = key;
			}
		}
		if (!max.isEmpty()) {
			return max;
		}
		for (String key : materials.keySet()) {
			return key;
		}
		return "";
	}

	public static int resolveTier(CraftingRecipe recipe, Map<String, Integer> materials) {
		return resolveTierFromKey(resolveMajorityKey(recipe, materials));
	}

	public static int resolveTier(CraftingRecipe recipe, List<CraftInput> inputs) {
		if (recipe == null || inputs == null || inputs.isEmpty()) {
			return 0;
		}
		HashMap<String, Integer> materials = new HashMap<>();
		for (CraftInput input : inputs) {
			String key = input.getKind().toLowerCase() + "." + input.getId().toLowerCase();
			materials.put(key, input.getAmount());
		}
		return resolveTier(recipe, materials);
	}

	public static int resolveTierFromKey(String materialKey) {
		if (materialKey == null || materialKey.isBlank()) {
			return 0;
		}
		String[] split = materialKey.split("\\.");
		if (split.length < 2) {
			return 0;
		}
		String kind = split[0].toLowerCase();
		String id = split[1].toLowerCase();
		if (kind.equals("ingredient")) {
			return ProfessionPermissions.resolveTier(id);
		}
		if (kind.equals("alloy")) {
			return ProfessionPermissions.resolveAlloyTier(id);
		}
		return 0;
	}

	private static boolean isMainTypeMaterial(CraftingRecipe recipe, String key) {
		String[] split = key.split("\\.");
		if (split.length < 2) {
			return false;
		}
		String kind = split[0].toLowerCase();
		String id = split[1].toLowerCase();
		if (kind.equals("ingredient")) {
			Ingredient ing = IngredientLoader.getByString(id);
			return ing != null
					&& ing.getIngredientData().getType().getId().equalsIgnoreCase(recipe.getMainType());
		}
		if (kind.equals("alloy")) {
			Alloy alloy = AlloyManager.getAlloyById(id);
			return alloy != null
					&& alloy.getData().getType().getId().equalsIgnoreCase(recipe.getMainType());
		}
		return false;
	}
}
