package net.tfminecraft.AdvancedCrafting.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import net.tfminecraft.AdvancedCrafting.AdvancedCrafting;
import net.tfminecraft.AdvancedCrafting.Database.AlloyRecipeStore;
import net.tfminecraft.AdvancedCrafting.Objects.Data.AlloyRecipe;

public final class AlloyRecipeSync {
	private AlloyRecipeSync() {
	}

	public static void run(CommandSender sender, boolean repair) {
		AlloyRecipeStore recipeDb = AdvancedCrafting.getAlloyRecipeStore();
		File alloysFolder = new File(AdvancedCrafting.plugin.getDataFolder(), "data/alloys");
		if (!alloysFolder.exists()) {
			sender.sendMessage("§cAlloys folder not found.");
			return;
		}

		JSONParser parser = new JSONParser();
		Map<String, String> jsonComboToAlloy = new HashMap<>();
		Map<String, AlloyRecipe> jsonAlloyToRecipe = new HashMap<>();
		List<String> warnings = new ArrayList<>();
		int repaired = 0;
		int checked = 0;

		File[] files = alloysFolder.listFiles();
		if (files == null) {
			sender.sendMessage("§cCould not read alloys folder.");
			return;
		}

		for (File file : files) {
			if (file.isDirectory() || !file.getName().endsWith(".json")) {
				continue;
			}
			try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
				JSONObject json = (JSONObject) parser.parse(reader);
				String alloyId = ((String) json.get("id")).toLowerCase();
				AlloyRecipe recipe = parseRecipe(json);
				if (recipe == null) {
					warnings.add("Alloy §f" + alloyId + "§c has no recipe block in JSON.");
					continue;
				}
				checked++;
				String comboKey = recipe.comboKey();

				String existingAlloy = jsonComboToAlloy.get(comboKey);
				if (existingAlloy != null && !existingAlloy.equals(alloyId)) {
					warnings.add("Combo collision in JSON: §f" + comboKey + "§c is used by §f"
							+ existingAlloy + "§c and §f" + alloyId + "§c.");
				}
				jsonComboToAlloy.put(comboKey, alloyId);

				if (!alloyId.equals("scrap")) {
					AlloyRecipe previous = jsonAlloyToRecipe.get(alloyId);
					if (previous != null && !previous.matches(recipe)) {
						warnings.add("Alloy §f" + alloyId + "§c has multiple different recipes across JSON files.");
					}
					jsonAlloyToRecipe.put(alloyId, recipe);
				}

				String dbResult = recipeDb.getResultByCombo(comboKey);
				if (dbResult == null) {
					warnings.add("Missing DB row for §f" + alloyId + "§c (combo §f" + comboKey + "§c).");
					if (repair) {
						recipeDb.upsert(recipe, alloyId);
						repaired++;
					}
				} else if (!dbResult.equalsIgnoreCase(alloyId)) {
					warnings.add("DB mismatch for combo §f" + comboKey + "§c: JSON says §f" + alloyId
							+ "§c, DB says §f" + dbResult + "§c.");
					if (repair) {
						recipeDb.upsert(recipe, alloyId);
						repaired++;
					}
				}

				AlloyRecipe dbRecipe = recipeDb.getRecipeByResult(alloyId);
				if (dbRecipe != null && !dbRecipe.matches(recipe) && !alloyId.equals("scrap")) {
					warnings.add("DB recipe for §f" + alloyId + "§c does not match JSON recipe.");
				}
			} catch (Exception ex) {
				warnings.add("Failed to read §f" + file.getName() + "§c: " + ex.getMessage());
			}
		}

		for (Map.Entry<String, Integer> entry : recipeDb.findDuplicateResultsExcludingScrap().entrySet()) {
			warnings.add("DB has §f" + entry.getValue() + "§c recipes pointing to alloy §f" + entry.getKey() + "§c.");
		}

		for (Map.Entry<String, String> dbEntry : recipeDb.loadAllComboResults().entrySet()) {
			String comboKey = dbEntry.getKey();
			String resultId = dbEntry.getValue();
			if (resultId.equalsIgnoreCase("scrap")) {
				continue;
			}
			String jsonAlloy = jsonComboToAlloy.get(comboKey);
			if (jsonAlloy == null) {
				warnings.add("DB combo §f" + comboKey + "§c → §f" + resultId
						+ "§c has no matching recipe in any alloy JSON.");
			}
		}

		sender.sendMessage("§e[AC] Alloy recipe sync: checked §f" + checked + "§e JSON recipe(s).");
		if (repair) {
			sender.sendMessage("§e[AC] Repaired §f" + repaired + "§e DB row(s) from JSON.");
		}
		if (warnings.isEmpty()) {
			sender.sendMessage("§a[AC] No recipe issues found.");
			return;
		}
		sender.sendMessage("§c[AC] " + warnings.size() + " issue(s):");
		for (String warning : warnings) {
			sender.sendMessage("§c- " + warning);
		}
		if (!repair) {
			sender.sendMessage("§7Run §f/ac sync recipes repair§7 to upsert JSON recipes into the DB.");
		}
	}

	@SuppressWarnings("unchecked")
	private static AlloyRecipe parseRecipe(JSONObject json) {
		if (!json.containsKey("recipe")) {
			return null;
		}
		JSONObject recipeObj = (JSONObject) json.get("recipe");
		if (recipeObj == null || !recipeObj.containsKey("base")) {
			return null;
		}
		String base = ((String) recipeObj.get("base")).toLowerCase();
		List<String> catalysts = new ArrayList<>();
		if (recipeObj.containsKey("catalysts")) {
			JSONArray array = (JSONArray) recipeObj.get("catalysts");
			for (Object entry : array) {
				catalysts.add(entry.toString().toLowerCase());
			}
		}
		return new AlloyRecipe(base, catalysts);
	}
}
