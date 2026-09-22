package net.tfminecraft.AdvancedCrafting.Utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.AdvancedCrafting.AdvancedCrafting;

public class RevisionTracker {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Object LOCK = new Object();

	private final Map<String, Entry> ingredients = new HashMap<>();
	private final Map<String, Entry> alloys = new HashMap<>();
	private final Map<String, Entry> statTemplates = new HashMap<>();
	private File file;
	private boolean dirty;

	private static class Entry {
		int revision;
		String hash;

		Entry(int revision, String hash) {
			this.revision = revision;
			this.hash = hash;
		}
	}

	public void load(File dataFolder) {
		synchronized (LOCK) {
			ingredients.clear();
			alloys.clear();
			statTemplates.clear();
			dirty = false;
			file = new File(dataFolder, "data/revisions.json");
			if (!file.exists()) {
				return;
			}
			try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				loadSection(root, "ingredients", ingredients);
				loadSection(root, "alloys", alloys);
				loadSection(root, "statTemplates", statTemplates);
			} catch (Exception ex) {
				AdvancedCrafting.plugin.getLogger().warning("AC: Failed to load revisions.json: " + ex.getMessage());
			}
		}
	}

	private void loadSection(JsonObject root, String key, Map<String, Entry> target) {
		if (!root.has(key) || !root.get(key).isJsonObject()) {
			return;
		}
		JsonObject section = root.getAsJsonObject(key);
		for (String id : section.keySet()) {
			JsonObject entry = section.getAsJsonObject(id);
			int revision = entry.has("revision") ? entry.get("revision").getAsInt() : 1;
			String hash = entry.has("hash") ? entry.get("hash").getAsString() : "";
			target.put(id.toLowerCase(), new Entry(revision, hash));
		}
	}

	public void flush() {
		synchronized (LOCK) {
			if (!dirty || file == null) {
				return;
			}
			try {
				file.getParentFile().mkdirs();
				JsonObject root = new JsonObject();
				root.add("ingredients", sectionToJson(ingredients));
				root.add("alloys", sectionToJson(alloys));
				root.add("statTemplates", sectionToJson(statTemplates));
				try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
					writer.write(GSON.toJson(root));
				}
				dirty = false;
			} catch (Exception ex) {
				AdvancedCrafting.plugin.getLogger().warning("AC: Failed to save revisions.json: " + ex.getMessage());
			}
		}
	}

	private JsonObject sectionToJson(Map<String, Entry> source) {
		TreeMap<String, Entry> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		sorted.putAll(source);
		JsonObject section = new JsonObject();
		for (Map.Entry<String, Entry> e : sorted.entrySet()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("revision", e.getValue().revision);
			entry.addProperty("hash", e.getValue().hash);
			section.add(e.getKey().toLowerCase(), entry);
		}
		return section;
	}

	public int resolveIngredient(String id, String contentHash) {
		return resolve(id.toLowerCase(), contentHash, ingredients);
	}

	public int resolveAlloy(String id, String contentHash) {
		return resolve(id.toLowerCase(), contentHash, alloys);
	}

	public int resolveStatTemplate(String id, String contentHash) {
		return resolve(id.toLowerCase(), contentHash, statTemplates);
	}

	private int resolve(String id, String contentHash, Map<String, Entry> map) {
		synchronized (LOCK) {
			Entry existing = map.get(id);
			if (existing == null) {
				map.put(id, new Entry(1, contentHash));
				dirty = true;
				return 1;
			}
			if (contentHash.equals(existing.hash)) {
				return existing.revision;
			}
			existing.revision++;
			existing.hash = contentHash;
			dirty = true;
			return existing.revision;
		}
	}

	public static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
