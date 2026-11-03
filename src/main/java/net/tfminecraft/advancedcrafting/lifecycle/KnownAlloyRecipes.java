package net.tfminecraft.advancedcrafting.lifecycle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.data.AlloyRecipe;

/**
 * The alloy recipes each account has forged, as combo keys ({@code base|catalyst,catalyst}).
 * A combo never changes when its alloy is named or renamed and is never reused, unlike alloy ids.
 * Files live in {@code data/known-alloy-recipes/<uuid>.json}. A player without a file yet starts
 * from the alloys {@link PlayerAlloyForgeTracker} has on record for them.
 */
public final class KnownAlloyRecipes {
	private static KnownAlloyRecipes instance;

	private final File folder;
	private final Map<UUID, Set<String>> cache = new HashMap<>();

	public KnownAlloyRecipes(File dataFolder) {
		this.folder = new File(dataFolder, "known-alloy-recipes");
		if (!folder.exists()) {
			folder.mkdirs();
		}
	}

	public static void init(KnownAlloyRecipes known) {
		instance = known;
	}

	public static KnownAlloyRecipes get() {
		return instance;
	}

	/** Called for every successful forge (not scrap). No-op before the plugin has set an instance. */
	public static void learn(UUID playerUuid, AlloyRecipe recipe) {
		if (instance != null && playerUuid != null && recipe != null) {
			instance.add(playerUuid, recipe.comboKey());
		}
	}

	public boolean knows(UUID playerUuid, Alloy alloy) {
		if (playerUuid == null || alloy == null || alloy.getData() == null) {
			return false;
		}
		AlloyRecipe recipe = alloy.getData().getRecipe();
		return recipe != null && load(playerUuid).contains(recipe.comboKey());
	}

	private void add(UUID playerUuid, String comboKey) {
		Set<String> known = load(playerUuid);
		if (known.add(comboKey)) {
			save(playerUuid, known);
		}
	}

	private Set<String> load(UUID playerUuid) {
		Set<String> known = cache.get(playerUuid);
		if (known != null) {
			return known;
		}
		known = new HashSet<>();
		File file = file(playerUuid);
		if (file.exists()) {
			try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
				Object parsed = new JSONParser().parse(reader);
				if (parsed instanceof JSONArray array) {
					for (Object entry : array) {
						if (entry != null && !entry.toString().isBlank()) {
							known.add(entry.toString().trim().toLowerCase());
						}
					}
				}
			} catch (Exception ex) {
				// Keep the unreadable file untouched; this session just starts from what was read.
				AdvancedCrafting.plugin.getLogger().warning("AC: could not read " + file.getName() + ": " + ex.getMessage());
				cache.put(playerUuid, known);
				return known;
			}
		} else if (seedFromForgeHistory(playerUuid, known)) {
			save(playerUuid, known);
		}
		cache.put(playerUuid, known);
		return known;
	}

	/** Recipes of the alloys this player forged before recipes were recorded. */
	private boolean seedFromForgeHistory(UUID playerUuid, Set<String> known) {
		PlayerAlloyForgeTracker tracker = CraftLifecycle.getTracker();
		if (tracker == null) {
			return false;
		}
		for (String alloyId : tracker.getForgedIds(playerUuid)) {
			Alloy alloy = AlloyManager.getAlloyById(alloyId);
			if (alloy != null && alloy.getData() != null && alloy.getData().getRecipe() != null) {
				known.add(alloy.getData().getRecipe().comboKey());
			}
		}
		return !known.isEmpty();
	}

	@SuppressWarnings("unchecked")
	private void save(UUID playerUuid, Set<String> known) {
		List<String> sorted = new ArrayList<>(known);
		sorted.sort(null);
		JSONArray array = new JSONArray();
		array.addAll(sorted);
		try (PrintWriter writer = new PrintWriter(file(playerUuid), StandardCharsets.UTF_8)) {
			writer.print(array.toJSONString());
		} catch (IOException ex) {
			AdvancedCrafting.plugin.getLogger().warning("AC: could not save known alloy recipes for " + playerUuid
					+ ": " + ex.getMessage());
		}
	}

	private File file(UUID playerUuid) {
		return new File(folder, playerUuid + ".json");
	}
}
