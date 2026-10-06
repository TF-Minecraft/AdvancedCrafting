package net.tfminecraft.advancedcrafting.database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.loaders.HitLoader;
import net.tfminecraft.advancedcrafting.loaders.SchemeLoader;
import net.tfminecraft.advancedcrafting.loaders.TypeLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.alloys.AlloyStation;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.CraftingHit;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.objects.data.AlloyData;
import net.tfminecraft.advancedcrafting.objects.data.AlloyRecipe;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;
import net.tfminecraft.advancedcrafting.objects.schemes.ColourScheme;
import net.tfminecraft.advancedcrafting.objects.schemes.ModelScheme;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;
import net.tfminecraft.advancedcrafting.utils.AlloyRebaser;
import net.tfminecraft.advancedcrafting.utils.IngredientLore;
import net.tfminecraft.advancedcrafting.utils.RevisionTracker;

public class AlloyDatabase {
	private static final String BACKUP_STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
	private JSONObject json;
	private final JSONParser parser = new JSONParser();

	private File alloysFolder() {
		return new File(AdvancedCrafting.plugin.getDataFolder(), "data/alloys");
	}

	/** Closes the file again; an open reader keeps Windows from deleting or replacing it. */
	private JSONObject read(File file) throws Exception {
		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
			return (JSONObject) parser.parse(reader);
		}
	}

	public Alloy loadAlloy(String result) {
		File file = new File(alloysFolder(), result.toLowerCase() + ".json");
		if (!file.exists()) {
			return null;
		}
		try {
			json = read(file);
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
					json = read(file);
					Alloy alloy = parseAlloyFromJson(json);
					followBase(file, alloy);
					AlloyManager.addAlloy(finishAlloy(alloy));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	/**
	 * Startup only: moves the alloy's stats by how much its base ingredient changed since it was
	 * forged, then records the live base. The file is backed up before it is rewritten.
	 */
	void followBase(File file, Alloy alloy) {
		AlloyData data = alloy.getData();
		AlloyRecipe recipe = data.getRecipe();
		if (recipe == null) {
			return;
		}
		Ingredient base = IngredientLoader.getByString(recipe.getBaseId());
		if (base == null) {
			return;
		}
		StatData live = base.getIngredientData().getStatData();
		StatData snapshot = AlloyRebaser.snapshotFor(data.getBaseStats(), recipe.getBaseId(), live, file.lastModified());
		StatData moved = AlloyRebaser.rebase(data.getStatData(), snapshot, live, Cache.maxFactor);
		if (moved == null && data.getBaseStats() != null) {
			return;
		}
		if (!backup(file)) {
			return;
		}
		StatData oldStats = data.getStatData();
		StatData oldBase = data.getBaseStats();
		if (moved != null) {
			data.setStatData(moved);
		}
		data.setBaseStats(StatData.copyOf(live));
		if (!saveAlloy(alloy)) {
			data.setStatData(oldStats);
			data.setBaseStats(oldBase);
			restore(file);
			return;
		}
		if (moved != null) {
			AdvancedCrafting.plugin.getLogger().info("AC: alloy " + alloy.getId() + " follows " + recipe.getBaseId() + ": "
					+ AlloyRebaser.describe(oldStats, moved));
		}
	}

	/** Puts the backed-up file back after a failed rewrite, so the file matches the alloy kept in memory. */
	private void restore(File file) {
		File copy = new File(AdvancedCrafting.plugin.getDataFolder(), "data/alloy-backups/" + BACKUP_STAMP + "/" + file.getName());
		try {
			Files.copy(copy.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			AdvancedCrafting.plugin.getLogger().warning("AC: could not rewrite " + file.getName() + "; restored it unchanged.");
		} catch (IOException ex) {
			AdvancedCrafting.plugin.getLogger().severe("AC: could not rewrite or restore " + file.getName()
					+ "; the original is in " + copy.getPath() + ": " + ex.getMessage());
		}
	}

	/** False when the copy failed; the alloy is then left exactly as it is. */
	private boolean backup(File file) {
		File folder = new File(AdvancedCrafting.plugin.getDataFolder(), "data/alloy-backups/" + BACKUP_STAMP);
		folder.mkdirs();
		try {
			Files.copy(file.toPath(), new File(folder, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.COPY_ATTRIBUTES);
			return true;
		} catch (IOException ex) {
			AdvancedCrafting.plugin.getLogger().warning("AC: could not back up " + file.getName()
					+ ", leaving it unchanged: " + ex.getMessage());
			return false;
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

	/** False when the file could not be written. */
	@SuppressWarnings("unchecked")
	public boolean saveAlloy(Alloy a) {
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
			json = read(file);
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
			if (a.getData().getBaseStats() != null) {
				defaults.put("baseStats", toArray(a.getData().getBaseStats()));
			}
			JSONArray hitArray = new JSONArray();
			for (CraftingHit h : a.getData().getHits().keySet()) {
				String hit = h.getId() + "." + a.getData().getHits().get(h);
				hitArray.add(hit);
			}
			defaults.put("hits", hitArray);
			boolean saved = save(file, defaults);
			if (recipe != null) {
				AdvancedCrafting.getAlloyRecipeStore().upsert(recipe, a.getId());
			}
			return saved;
		} catch (Throwable ex) {
			ex.printStackTrace();
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private static JSONArray toArray(StatData stats) {
		JSONArray array = new JSONArray();
		for (StatModifier m : stats.getModifiers()) {
			array.add(m.getType() + "(" + m.getAmount() + ")");
		}
		return array;
	}

	/** {@code merge} adds repeated stat types together (alloy stats); otherwise each entry stays separate. */
	private static StatData parseStats(JSONArray array, boolean merge) {
		StatData stats = new StatData();
		for (Object entry : array) {
			String s = entry.toString();
			String st = s.split("\\(")[0];
			double amount = Double.parseDouble(s.split("\\(")[1].replace(")", ""));
			if (merge) {
				stats.addModifier(new StatModifier(st, amount));
			} else {
				stats.getModifiers().add(new StatModifier(st, amount));
			}
		}
		return stats;
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
		StatData stats = parseStats((JSONArray) json.get("stats"), true);
		HashMap<CraftingHit, Integer> hits = new HashMap<>();
		int i = 0;
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
		AlloyData data = new AlloyData(colourScheme, model, type, scheme, stats, hits, xp, recipe, tier, statMergeBucketId);
		if (json.get("baseStats") instanceof JSONArray recorded) {
			data.setBaseStats(parseStats(recorded, false));
		}
		return new Alloy(id, name, data);
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

			try (FileWriter fw = new FileWriter(file)) {
				fw.write(prettyJsonString);
			}

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
