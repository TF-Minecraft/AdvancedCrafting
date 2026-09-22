package net.tfminecraft.AdvancedCrafting.lifecycle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public final class PlayerAlloyForgeTracker {

	private static final JSONParser PARSER = new JSONParser();
	private static final ConcurrentHashMap<UUID, Set<String>> CACHE = new ConcurrentHashMap<>();

	private final File dataFolder;

	public PlayerAlloyForgeTracker(File dataFolder) {
		this.dataFolder = new File(dataFolder, "forged-alloys");
		if (!this.dataFolder.exists()) {
			this.dataFolder.mkdirs();
		}
	}

	public boolean recordForge(UUID playerUuid, String alloyId) {
		if (playerUuid == null || alloyId == null || alloyId.isBlank()) {
			return false;
		}

		String normalizedId = alloyId.toLowerCase(Locale.ROOT);
		Set<String> forged = loadForgedIds(playerUuid);
		if (forged.contains(normalizedId)) {
			return false;
		}

		forged.add(normalizedId);
		saveForgedIds(playerUuid, forged);
		return true;
	}

	private Set<String> loadForgedIds(UUID playerUuid) {
		Set<String> cached = CACHE.get(playerUuid);
		if (cached != null) {
			return cached;
		}

		Set<String> forged = new HashSet<>();
		File file = playerFile(playerUuid);
		if (!file.exists()) {
			CACHE.put(playerUuid, forged);
			return forged;
		}

		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
			Object parsed = PARSER.parse(reader);
			if (parsed instanceof JSONArray array) {
				for (Object entry : array) {
					if (entry != null) {
						String id = entry.toString().trim().toLowerCase(Locale.ROOT);
						if (!id.isEmpty()) {
							forged.add(id);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		CACHE.put(playerUuid, forged);
		return forged;
	}

	private void saveForgedIds(UUID playerUuid, Set<String> forged) {
		CACHE.put(playerUuid, forged);

		File file = playerFile(playerUuid);
		JSONArray array = new JSONArray();
		for (String id : forged) {
			array.add(id);
		}

		try (PrintWriter writer = new PrintWriter(new FileWriter(file, false))) {
			writer.print(array.toJSONString());
			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private File playerFile(UUID playerUuid) {
		return new File(dataFolder, playerUuid.toString() + ".json");
	}
}
