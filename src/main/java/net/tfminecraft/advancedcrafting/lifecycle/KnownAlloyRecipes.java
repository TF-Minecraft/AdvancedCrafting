package net.tfminecraft.advancedcrafting.lifecycle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
	/** Accounts whose file could not be read; it is never overwritten this session. */
	private final Set<UUID> unreadable = new HashSet<>();

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
		if (known.add(comboKey) && !save(playerUuid, known)) {
			// Not on disk, so the next forge of this combo tries again.
			known.remove(comboKey);
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
							known.add(entry.toString().trim().toLowerCase(Locale.ROOT));
						}
					}
				}
			} catch (Exception ex) {
				// Leave the file for staff to repair; this session works from memory only.
				AdvancedCrafting.plugin.getLogger().warning("AC: could not read " + file.getPath() + ", leaving it unchanged: "
						+ ex.getMessage());
				unreadable.add(playerUuid);
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

	/** Writes a temp file and moves it over the old one, so a crash can't leave half a file. */
	@SuppressWarnings("unchecked")
	private boolean save(UUID playerUuid, Set<String> known) {
		if (unreadable.contains(playerUuid)) {
			return true;
		}
		List<String> sorted = new ArrayList<>(known);
		sorted.sort(null);
		JSONArray array = new JSONArray();
		array.addAll(sorted);
		Path target = file(playerUuid).toPath();
		Path temp = target.resolveSibling(target.getFileName() + ".tmp");
		try {
			Files.createDirectories(folder.toPath());
			Files.writeString(temp, array.toJSONString(), StandardCharsets.UTF_8);
			try {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException ex) {
			AdvancedCrafting.plugin.getLogger().warning("AC: could not save known alloy recipes for " + playerUuid
					+ ": " + ex.getMessage());
			return false;
		}
	}

	private File file(UUID playerUuid) {
		return new File(folder, playerUuid + ".json");
	}
}
