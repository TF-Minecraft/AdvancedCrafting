package net.tfminecraft.AdvancedCrafting.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import net.tfminecraft.AdvancedCrafting.AdvancedCrafting;
import net.tfminecraft.AdvancedCrafting.Database.AlloyDatabase;
import net.tfminecraft.AdvancedCrafting.Loaders.RecipeLoader;
import net.tfminecraft.AdvancedCrafting.Managers.AlloyManager;
import net.tfminecraft.AdvancedCrafting.Objects.Alloys.Alloy;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.CraftingRecipe;
import net.tfminecraft.AdvancedCrafting.Objects.Data.AlloyRecipe;

public final class AlloyInfoFormatter {
	private static final int RECIPE_LIST_CAP = 20;

	private AlloyInfoFormatter() {
	}

	public static void send(CommandSender sender, String alloyId) {
		alloyId = alloyId.toLowerCase();
		Alloy alloy = AlloyManager.getAlloyById(alloyId);
		if (alloy == null) {
			AlloyDatabase db = new AlloyDatabase();
			alloy = db.loadAlloy(alloyId);
		}
		if (alloy == null) {
			sender.sendMessage("§cUnknown alloy: §f" + alloyId);
			return;
		}

		sender.sendMessage("§e--- Alloy info: §f" + alloy.getId() + " §e---");
		sender.sendMessage("§7Name: §f" + alloy.getName());
		sender.sendMessage("§7Type: §f" + alloy.getData().getType().getName()
				+ " §7(" + alloy.getData().getType().getId() + ")");
		sender.sendMessage("§7Stats: §f" + alloy.getData().getStatData().getModifiers().size()
				+ " §7| Hits: §f" + alloy.getData().getHits().size());

		AlloyRecipe recipe = alloy.getData().getRecipe();
		if (recipe == null) {
			recipe = loadRecipeFromJson(alloyId);
		}
		if (recipe == null) {
			sender.sendMessage("§cForge recipe: not set in JSON");
		} else {
			sender.sendMessage("§7Forge: §f" + recipe.getBaseId()
					+ " §7+ catalysts §f" + String.join(", ", recipe.getCatalystIds()));
			String dbResult = AdvancedCrafting.getAlloyRecipeStore().getResultByCombo(recipe.comboKey());
			if (dbResult == null) {
				sender.sendMessage("§cDB: no row for combo §f" + recipe.comboKey());
			} else if (!dbResult.equalsIgnoreCase(alloyId)) {
				sender.sendMessage("§cDB mismatch: combo maps to §f" + dbResult + "§c, expected §f" + alloyId);
			} else {
				sender.sendMessage("§aDB: combo §f" + recipe.comboKey() + " §a→ this alloy");
			}
		}

		List<String> craftingRecipes = findCraftingRecipes(alloy.getData().getType().getId());
		sender.sendMessage("§7Crafting recipes using §f" + alloy.getData().getType().getId()
				+ " §7bucket: §f" + craftingRecipes.size());
		int shown = Math.min(craftingRecipes.size(), RECIPE_LIST_CAP);
		for (int i = 0; i < shown; i++) {
			sender.sendMessage("§7  - §f" + craftingRecipes.get(i));
		}
		if (craftingRecipes.size() > RECIPE_LIST_CAP) {
			sender.sendMessage("§7  ... and §f" + (craftingRecipes.size() - RECIPE_LIST_CAP) + " §7more");
		}
	}

	private static List<String> findCraftingRecipes(String bucketId) {
		List<String> ids = new ArrayList<>();
		for (CraftingRecipe recipe : RecipeLoader.get().values()) {
			if (recipe.getRecipe().containsKey(bucketId)) {
				ids.add(recipe.getId());
			}
		}
		return ids;
	}

	@SuppressWarnings("unchecked")
	private static AlloyRecipe loadRecipeFromJson(String alloyId) {
		File file = new File(AdvancedCrafting.plugin.getDataFolder(), "data/alloys/" + alloyId + ".json");
		if (!file.exists()) {
			return null;
		}
		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
			JSONObject json = (JSONObject) new JSONParser().parse(reader);
			if (!json.containsKey("recipe")) {
				return null;
			}
			JSONObject recipeObj = (JSONObject) json.get("recipe");
			String base = ((String) recipeObj.get("base")).toLowerCase();
			List<String> catalysts = new ArrayList<>();
			if (recipeObj.containsKey("catalysts")) {
				JSONArray array = (JSONArray) recipeObj.get("catalysts");
				for (Object entry : array) {
					catalysts.add(entry.toString().toLowerCase());
				}
			}
			return new AlloyRecipe(base, catalysts);
		} catch (Exception ex) {
			return null;
		}
	}
}
