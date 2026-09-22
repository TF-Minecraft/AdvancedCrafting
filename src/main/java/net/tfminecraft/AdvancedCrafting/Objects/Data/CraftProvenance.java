package net.tfminecraft.AdvancedCrafting.Objects.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.tfminecraft.AdvancedCrafting.Loaders.IngredientLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.RecipeLoader;
import net.tfminecraft.AdvancedCrafting.Managers.AlloyManager;
import net.tfminecraft.AdvancedCrafting.Objects.Alloys.Alloy;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.CraftingRecipe;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.Quality;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;
import net.tfminecraft.AdvancedCrafting.Objects.Stats.StatTemplate;
import net.tfminecraft.AdvancedCrafting.Utils.PDCKeys;

public class CraftProvenance {
	private static final Gson GSON = new Gson();

	private String recipeId;
	private String qualityId;
	private List<CraftInput> inputs = new ArrayList<>();
	private int statTemplateRevision;

	public CraftProvenance() {
	}

	public CraftProvenance(String recipeId, String qualityId, List<CraftInput> inputs, int statTemplateRevision) {
		this.recipeId = recipeId;
		this.qualityId = qualityId;
		this.inputs = inputs;
		this.statTemplateRevision = statTemplateRevision;
	}

	public String getRecipeId() {
		return recipeId;
	}

	public String getQualityId() {
		return qualityId;
	}

	public List<CraftInput> getInputs() {
		return inputs;
	}

	public int getStatTemplateRevision() {
		return statTemplateRevision;
	}

	public static CraftProvenance from(CraftingRecipe recipe, HashMap<String, Integer> materials, Quality quality) {
		List<CraftInput> inputList = new ArrayList<>();
		for (String key : materials.keySet()) {
			String[] split = key.split("\\.");
			if (split.length < 2) {
				continue;
			}
			String kind = split[0].toLowerCase();
			String id = split[1].toLowerCase();
			int amount = materials.get(key);
			int revision = 0;
			if (kind.equals("ingredient")) {
				Ingredient ing = IngredientLoader.getByString(id);
				if (ing != null) {
					revision = ing.getRevision();
				}
			} else if (kind.equals("alloy")) {
				Alloy alloy = AlloyManager.getAlloyById(id);
				if (alloy != null) {
					revision = alloy.getRevision();
				}
			}
			inputList.add(new CraftInput(kind, id, amount, revision));
		}
		String qualityId = quality != null ? quality.getId() : "";
		int templateRevision = 0;
		StatTemplate template = recipe.getStatTemplate();
		if (template != null) {
			templateRevision = template.getRevision();
		}
		return new CraftProvenance(recipe.getId(), qualityId, inputList, templateRevision);
	}

	public void applyTo(ItemStack item) {
		if (item == null) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return;
		}
		meta.getPersistentDataContainer().set(PDCKeys.craftRecipe(), PersistentDataType.STRING, recipeId);
		meta.getPersistentDataContainer().set(PDCKeys.craftQuality(), PersistentDataType.STRING, qualityId);
		meta.getPersistentDataContainer().set(PDCKeys.craftInputs(), PersistentDataType.STRING, GSON.toJson(inputs));
		meta.getPersistentDataContainer().set(PDCKeys.craftStatTemplateRevision(), PersistentDataType.INTEGER,
				statTemplateRevision);
		item.setItemMeta(meta);
	}

	public static CraftProvenance readFrom(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		String recipeId = meta.getPersistentDataContainer().get(PDCKeys.craftRecipe(), PersistentDataType.STRING);
		if (recipeId == null) {
			return null;
		}
		String qualityId = meta.getPersistentDataContainer().get(PDCKeys.craftQuality(), PersistentDataType.STRING);
		if (qualityId == null) {
			qualityId = "";
		}
		String inputsJson = meta.getPersistentDataContainer().get(PDCKeys.craftInputs(), PersistentDataType.STRING);
		List<CraftInput> inputs = new ArrayList<>();
		if (inputsJson != null) {
			List<CraftInput> parsed = GSON.fromJson(inputsJson, new TypeToken<List<CraftInput>>() {}.getType());
			if (parsed != null) {
				inputs = parsed;
			}
		}
		Integer templateRevision = meta.getPersistentDataContainer().get(PDCKeys.craftStatTemplateRevision(),
				PersistentDataType.INTEGER);
		int statTemplateRevision = templateRevision != null ? templateRevision : 0;
		return new CraftProvenance(recipeId, qualityId, inputs, statTemplateRevision);
	}

	public boolean isOutdated() {
		if (!getOutdatedInputs().isEmpty()) {
			return true;
		}
		return isStatTemplateOutdated();
	}

	public List<CraftInput> getOutdatedInputs() {
		List<CraftInput> outdated = new ArrayList<>();
		for (CraftInput input : inputs) {
			int liveRevision = getLiveRevision(input.getKind(), input.getId());
			if (liveRevision > input.getRevision()) {
				outdated.add(input);
			}
		}
		return outdated;
	}

	public boolean isStatTemplateOutdated() {
		StatTemplate live = getLiveStatTemplate();
		if (live == null) {
			return false;
		}
		return live.getRevision() > statTemplateRevision;
	}

	public void syncInputRevisions() {
		for (CraftInput input : inputs) {
			input.setRevision(getLiveRevision(input.getKind(), input.getId()));
		}
	}

	public void syncStatTemplateRevision() {
		StatTemplate live = getLiveStatTemplate();
		statTemplateRevision = live != null ? live.getRevision() : 0;
	}

	public void syncRevisions() {
		syncInputRevisions();
		syncStatTemplateRevision();
	}

	private StatTemplate getLiveStatTemplate() {
		CraftingRecipe recipe = RecipeLoader.getByString(recipeId);
		if (recipe == null) {
			return null;
		}
		return recipe.getStatTemplate();
	}

	private int getLiveRevision(String kind, String id) {
		if (kind.equalsIgnoreCase("ingredient")) {
			Ingredient ing = IngredientLoader.getByString(id);
			return ing != null ? ing.getRevision() : 0;
		}
		if (kind.equalsIgnoreCase("alloy")) {
			Alloy alloy = AlloyManager.getAlloyById(id);
			return alloy != null ? alloy.getRevision() : 0;
		}
		return 0;
	}
}
