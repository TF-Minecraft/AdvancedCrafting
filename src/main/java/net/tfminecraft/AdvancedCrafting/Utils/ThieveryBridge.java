package net.tfminecraft.AdvancedCrafting.Utils;

import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.AdvancedCrafting.AdvancedCrafting;
import net.tfminecraft.AdvancedCrafting.Loaders.CategoryLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.IngredientLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.QualityLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.RecipeLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.StatTemplateLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.TypeLoader;
import net.tfminecraft.AdvancedCrafting.Managers.AlloyManager;
import net.tfminecraft.AdvancedCrafting.Objects.Alloys.Alloy;
import net.tfminecraft.AdvancedCrafting.Objects.CraftStack;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.CraftingRecipe;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.Quality;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.RecipeCategory;
import net.tfminecraft.AdvancedCrafting.Objects.Data.AlloyRecipe;
import net.tfminecraft.AdvancedCrafting.Objects.Data.CraftInput;
import net.tfminecraft.AdvancedCrafting.Objects.Data.CraftProvenance;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.IngredientType;
import net.tfminecraft.AdvancedCrafting.Objects.Stats.StatTemplate;

public final class ThieveryBridge {

	private ThieveryBridge() {
	}

	public static boolean isPluginReady() {
		Plugin plugin = Bukkit.getPluginManager().getPlugin("AdvancedCrafting");
		return plugin instanceof AdvancedCrafting && plugin.isEnabled()
				&& AdvancedCrafting.plugin != null
				&& AdvancedCrafting.plugin.getIngredientManager() != null;
	}

	public static Ingredient resolveIngredient(ItemStack item) {
		if (!isPluginReady() || item == null || item.getType().isAir()) {
			return null;
		}
		CraftStack cs = new CraftStack(item);
		if (cs.isIngredient()) {
			return cs.getIngredient();
		}
		return AdvancedCrafting.plugin.getIngredientManager().getFromItem(item);
	}

	public static Alloy resolveAlloy(ItemStack item) {
		if (!isPluginReady() || item == null) {
			return null;
		}
		return new CraftStack(item).getAlloy();
	}

	public static CraftProvenance readProvenance(ItemStack item) {
		if (!isPluginReady() || item == null) {
			return null;
		}
		return CraftProvenance.readFrom(item);
	}

	public static Ingredient getIngredientById(String id) {
		return IngredientLoader.getByString(id);
	}

	public static Quality getQualityById(String id) {
		return QualityLoader.getByString(id);
	}

	public static CraftingRecipe getRecipeById(String id) {
		return RecipeLoader.getByString(id);
	}

	public static List<Ingredient> getAllIngredients() {
		return IngredientLoader.get();
	}

	public static HashMap<String, RecipeCategory> getRecipeCategories() {
		return CategoryLoader.get();
	}

	public static IngredientType getIngredientType(String id) {
		return TypeLoader.getIngredientTypeByString(id);
	}

	public static StatTemplate getStatTemplate(String id) {
		return StatTemplateLoader.getByString(id);
	}

	public static int resolveMajorityTier(CraftingRecipe recipe, List<CraftInput> inputs) {
		return MajorityTierResolver.resolveTier(recipe, inputs);
	}

	public static int sumForgeInputValues(AlloyRecipe recipe) {
		if (recipe == null) {
			return 0;
		}
		int total = 0;
		Ingredient base = IngredientLoader.getByString(recipe.getBaseId());
		if (base != null) {
			total += base.getIngredientData().getValue();
		}
		for (String catalystId : recipe.getCatalystIds()) {
			Ingredient catalyst = IngredientLoader.getByString(catalystId);
			if (catalyst != null) {
				total += catalyst.getIngredientData().getValue();
			}
		}
		return total;
	}

	public static int sumAlloyIngredientValues(Alloy alloy) {
		if (alloy == null || alloy.getData() == null) {
			return 0;
		}
		return sumForgeInputValues(alloy.getData().getRecipe());
	}

	public static int sumProvenanceInputValues(List<CraftInput> inputs) {
		if (inputs == null || inputs.isEmpty()) {
			return 0;
		}
		int total = 0;
		for (CraftInput input : inputs) {
			String kind = input.getKind().toLowerCase();
			if (kind.equals("ingredient")) {
				Ingredient ing = IngredientLoader.getByString(input.getId());
				if (ing != null) {
					total += ing.getIngredientData().getValue() * input.getAmount();
				}
			} else if (kind.equals("alloy")) {
				Alloy alloy = AlloyManager.getAlloyById(input.getId());
				if (alloy != null) {
					total += sumAlloyIngredientValues(alloy) * input.getAmount();
				}
			}
		}
		return total;
	}

	public static CraftingRecipe findRecipeByStatTemplate(String statTemplateId) {
		if (statTemplateId == null || statTemplateId.isBlank()) {
			return null;
		}
		for (CraftingRecipe recipe : RecipeLoader.get().values()) {
			if (recipe.getStatTemplateId() != null
					&& recipe.getStatTemplateId().equalsIgnoreCase(statTemplateId)) {
				return recipe;
			}
		}
		return null;
	}

	public static boolean hasBaseIngredientForType(String typeId, int tier) {
		if (typeId == null || typeId.isBlank() || tier <= 0) {
			return false;
		}
		for (Ingredient ingredient : getAllIngredients()) {
			if (!ingredient.getIngredientData().getType().getId().equalsIgnoreCase(typeId)) {
				continue;
			}
			if (!ingredient.getIngredientData().canBeBase()) {
				continue;
			}
			if (ingredient.getIngredientData().hasTier()
					&& ingredient.getIngredientData().getTier() == tier) {
				return true;
			}
		}
		return false;
	}

	public static String normalizeCraftCategoryId(String thieveryCraftId) {
		if (thieveryCraftId == null) {
			return "";
		}
		return switch (thieveryCraftId.toLowerCase()) {
			case "armour" -> "armor";
			default -> thieveryCraftId.toLowerCase();
		};
	}
}
