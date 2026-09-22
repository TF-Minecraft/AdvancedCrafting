package net.tfminecraft.advancedcrafting.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.bukkit.ChatColor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.loaders.HitLoader;
import net.tfminecraft.advancedcrafting.loaders.SchemeLoader;
import net.tfminecraft.advancedcrafting.loaders.TypeLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.alloys.AlloyStation;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.CraftingHit;
import net.tfminecraft.advancedcrafting.objects.data.AlloyData;
import net.tfminecraft.advancedcrafting.objects.data.AlloyRecipe;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;
import net.tfminecraft.advancedcrafting.objects.schemes.ColourScheme;
import net.tfminecraft.advancedcrafting.objects.schemes.ModelScheme;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;
import net.tfminecraft.advancedcrafting.utils.IngredientLore;
import net.tfminecraft.advancedcrafting.utils.RevisionTracker;

public class AlloyDatabase {
	private JSONObject json;
	private final JSONParser parser = new JSONParser();

	private File alloysFolder() {
		return new File(AdvancedCrafting.plugin.getDataFolder(), "data/alloys");
	}

	public Alloy loadAlloy(String result) {
		File file = new File(alloysFolder(), result.toLowerCase() + ".json");
		if (!file.exists()) {
			return null;
		}
		try {
			json = (JSONObject) parser.parse(new InputStreamReader(new FileInputStream(file), "UTF-8"));
			return finishAlloy(parseAlloyFromJson(json));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public void loadAlloys() {
		File folder = alloysFolder();
		File[] files = folder.listFiles();
		if (files == null) {
			return;
		}
		for (final File file : files) {
			if (!file.isDirectory()) {
				try {
					json = (JSONObject) parser.parse(new InputStreamReader(new FileInputStream(file), "UTF-8"));
					AlloyManager.addAlloy(finishAlloy(parseAlloyFromJson(json)));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public String getResult(AlloyStation station) {
		return AdvancedCrafting.getAlloyRecipeStore().getResult(station);
	}

	public void saveRecipe(AlloyStation station, String result) {
		AlloyRecipe recipe = AlloyRecipe.fromStation(station);
		if (recipe == null) {
			return;
		}
		AdvancedCrafting.getAlloyRecipeStore().upsert(recipe, result);
	}

	public void deleteRecipe(AlloyStation station) {
		AdvancedCrafting.getAlloyRecipeStore().deleteByStation(station);
	}

	public void editAlloy(Alloy newAlloy, String oldId) {
		try {
			File oldFile = new File(alloysFolder(), oldId.toLowerCase() + ".json");
			if (oldFile.exists()) {
				oldFile.delete();
			}
			AdvancedCrafting.getAlloyRecipeStore().updateResultId(oldId, newAlloy.getId());
			saveAlloy(newAlloy);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public void saveAlloy(Alloy a) {
		String hash = RevisionTracker.sha256(a.getData().buildRevisionContent());
		int revision = AdvancedCrafting.getRevisionTracker().resolveAlloy(a.getId(), hash);
		a.setRevision(revision);
		try {
			File file = new File(alloysFolder(), a.getId().toLowerCase() + ".json");
			File parentDir = file.getParentFile();
			if (!parentDir.exists()) {
				parentDir.mkdirs();
			}
			if (file.exists()) {
				file.delete();
			}
			file.createNewFile();
			PrintWriter pw = new PrintWriter(file, "UTF-8");
			pw.print("{");
			pw.print("}");
			pw.flush();
			pw.close();
			HashMap<String, Object> defaults = new HashMap<>();
			json = (JSONObject) parser.parse(new InputStreamReader(new FileInputStream(file), "UTF-8"));
			defaults.put("id", a.getId().toLowerCase());
			defaults.put("name", a.getName());
			defaults.put("model", a.getData().getModel());
			defaults.put("type", a.getData().getType().getId());
			defaults.put("statMergeBucketId", a.getData().getStatMergeBucketId());
			defaults.put("scheme", a.getData().getModelScheme().getId());
			defaults.put("colour scheme", a.getData().getColourScheme().getId());
			if (a.getData().hasXP()) {
				defaults.put("xp", a.getData().getXP());
			}
			AlloyRecipe recipe = a.getData().getRecipe();
			if (recipe != null) {
				defaults.put("recipe", recipeToJson(recipe));
			}
			int i = 0;
			JSONArray statArray = new JSONArray();
			while (i < a.getData().getStatData().getModifiers().size()) {
				String stat = a.getData().getStatData().getModifiers().get(i).getType() + "("
						+ a.getData().getStatData().getModifiers().get(i).getAmount() + ")";
				statArray.add(stat);
				i++;
			}
			defaults.put("stats", statArray);
			JSONArray hitArray = new JSONArray();
			for (CraftingHit h : a.getData().getHits().keySet()) {
				String hit = h.getId() + "." + a.getData().getHits().get(h);
				hitArray.add(hit);
			}
			defaults.put("hits", hitArray);
			save(file, defaults);
			if (recipe != null) {
				AdvancedCrafting.getAlloyRecipeStore().upsert(recipe, a.getId());
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private Alloy parseAlloyFromJson(JSONObject json) throws Exception {
		String id = ((String) json.get("id")).toLowerCase();
		String name = (String) json.get("name");
		int model = (int) Math.round((Double) json.get("model"));
		String xp = json.containsKey("xp") ? (String) json.get("xp") : null;
		ColourScheme colourScheme = SchemeLoader.getColourSchemeByString((String) json.get("colour scheme"));
		IngredientType type = TypeLoader.getIngredientTypeByString((String) json.get("type"));
		ModelScheme scheme = SchemeLoader.getModelSchemeByString((String) json.get("scheme"));
		StatData stats = new StatData();
		int i = 0;
		JSONArray statArray = (JSONArray) json.get("stats");
		while (i < statArray.size()) {
			String s = statArray.get(i).toString();
			String st = s.split("\\(")[0];
			double amount = Double.parseDouble(s.split("\\(")[1].replace(")", ""));
			stats.addModifier(new StatModifier(st, amount));
			i++;
		}
		HashMap<CraftingHit, Integer> hits = new HashMap<>();
		i = 0;
		JSONArray hitArray = (JSONArray) json.get("hits");
		while (i < hitArray.size()) {
			String s = hitArray.get(i).toString();
			String hit = s.split("\\.")[0];
			int amount = Integer.parseInt(s.split("\\.")[1]);
			hits.put(HitLoader.getByString(hit), amount);
			i++;
		}
		AlloyRecipe recipe = parseRecipe(json);
		int tier = IngredientLore.resolveAlloyTier(recipe, id);
		String statMergeBucketId = json.containsKey("statMergeBucketId")
				? (String) json.get("statMergeBucketId")
				: null;
		if (statMergeBucketId == null || statMergeBucketId.isBlank()) {
			statMergeBucketId = type != null ? type.getId() : null;
		}
		return new Alloy(id, name,
				new AlloyData(colourScheme, model, type, scheme, stats, hits, xp, recipe, tier, statMergeBucketId));
	}

	@SuppressWarnings("unchecked")
	private AlloyRecipe parseRecipe(JSONObject json) {
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

	@SuppressWarnings("unchecked")
	private JSONObject recipeToJson(AlloyRecipe recipe) {
		JSONObject recipeObj = new JSONObject();
		recipeObj.put("base", recipe.getBaseId());
		JSONArray catalystArray = new JSONArray();
		for (String catalyst : recipe.getCatalystIds()) {
			catalystArray.add(catalyst);
		}
		recipeObj.put("catalysts", catalystArray);
		return recipeObj;
	}

	@SuppressWarnings("unchecked")
	public boolean save(File file, HashMap<String, Object> defaults) {
		try {
			JSONObject toSave = new JSONObject();

			for (String s : defaults.keySet()) {
				Object o = defaults.get(s);
				if (o instanceof String) {
					toSave.put(s, getString(s, defaults));
				} else if (o instanceof Double) {
					toSave.put(s, getDouble(s, defaults));
				} else if (o instanceof Integer) {
					toSave.put(s, getInteger(s, defaults));
				} else if (o instanceof JSONObject) {
					toSave.put(s, getObject(s, defaults));
				} else if (o instanceof JSONArray) {
					toSave.put(s, getArray(s, defaults));
				}
			}

			TreeMap<String, Object> treeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			treeMap.putAll(toSave);

			Gson g = new GsonBuilder().setPrettyPrinting().create();
			String prettyJsonString = g.toJson(treeMap);

			FileWriter fw = new FileWriter(file);
			fw.write(prettyJsonString);
			fw.flush();
			fw.close();

			return true;
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}

	public String getRawData(String key, HashMap<String, Object> defaults) {
		return json.containsKey(key) ? json.get(key).toString()
				: (defaults.containsKey(key) ? defaults.get(key).toString() : key);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public String getString(String key, HashMap<String, Object> defaults) {
		return ChatColor.translateAlternateColorCodes('&', getRawData(key, defaults));
	}

	public boolean getBoolean(String key, HashMap<String, Object> defaults) {
		return Boolean.valueOf(getRawData(key, defaults));
	}

	public double getDouble(String key, HashMap<String, Object> defaults) {
		try {
			return Double.parseDouble(getRawData(key, defaults));
		} catch (Exception ex) {
		}
		return -1;
	}

	public double getInteger(String key, HashMap<String, Object> defaults) {
		try {
			return Integer.parseInt(getRawData(key, defaults));
		} catch (Exception ex) {
		}
		return -1;
	}

	public JSONObject getObject(String key, HashMap<String, Object> defaults) {
		return json.containsKey(key) ? (JSONObject) json.get(key)
				: (defaults.containsKey(key) ? (JSONObject) defaults.get(key) : new JSONObject());
	}

	public JSONArray getArray(String key, HashMap<String, Object> defaults) {
		return json.containsKey(key) ? (JSONArray) json.get(key)
				: (defaults.containsKey(key) ? (JSONArray) defaults.get(key) : new JSONArray());
	}

	private Alloy finishAlloy(Alloy alloy) {
		String hash = RevisionTracker.sha256(alloy.getData().buildRevisionContent());
		int revision = AdvancedCrafting.getRevisionTracker().resolveAlloy(alloy.getId(), hash);
		alloy.setRevision(revision);
		return alloy;
	}
}
