package net.tfminecraft.advancedcrafting.objects.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.advancedcrafting.loaders.HitLoader;
import net.tfminecraft.advancedcrafting.loaders.SchemeLoader;
import net.tfminecraft.advancedcrafting.loaders.TypeLoader;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.CraftingHit;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;
import net.tfminecraft.advancedcrafting.objects.schemes.ModelScheme;
import net.tfminecraft.advancedcrafting.objects.schemes.NamingScheme;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;

public class IngredientData {
	private int weight;
	private int value;
	private boolean base;
	private int tier;
	private String permission;

	private IngredientType type;
	private NamingScheme scheme;
	private ModelScheme modelScheme;
	private StatData statData;
	
	private HashMap<CraftingHit, Integer> hits = new HashMap<>();
	private List<String> protectedStats = new ArrayList<>();

	private String xp;
	private String statMergeKey;

	public IngredientData(ConfigurationSection config) {
		if(config.contains("weight")) {
			weight = config.getInt("weight");
		} else {
			weight = 1;
		}
		if(config.contains("value")) {
			value = config.getInt("value");
		} else {
			value = 1;
		}
		if(config.contains("base")) {
			base = config.getBoolean("base");
		} else {
			base = false;
		}
		if (config.contains("tier")) {
			tier = config.getInt("tier");
		} else {
			tier = 0;
		}
		String rawPermission = config.getString("permission", null);
		if (rawPermission == null || rawPermission.isBlank()) {
			rawPermission = config.getString("permission-namespace", null);
			if (rawPermission != null && !rawPermission.isBlank()) {
				Bukkit.getLogger().warning("AC: Ingredient uses deprecated permission-namespace; use permission instead.");
			}
		}
		if (rawPermission != null && !rawPermission.isBlank()) {
			permission = rawPermission.trim().toLowerCase();
		}
		type = TypeLoader.getIngredientTypeByString(config.getString("type"));
		scheme = SchemeLoader.getNamingSchemeByString(config.getString("scheme", "default"));
		modelScheme = SchemeLoader.getModelSchemeByString(config.getString("model-scheme", "default"));
		if(modelScheme == null) {
			Bukkit.getLogger().warning("AC: Error the model scheme "+config.getString("model-scheme") + " does not exist");
			modelScheme = SchemeLoader.getModelSchemeByString("default");
		}
		statData = new StatData(config.getStringList("stats"));
		xp = config.getString("xp", null);
		for(String s : config.getStringList("hits")) {
			String hit = s.split("\\.")[0];
			int a = Integer.parseInt(s.split("\\.")[1]);
			hits.put(HitLoader.getByString(hit), a);
		}
		if(config.contains("protected-stats")) {
			protectedStats = config.getStringList("protected-stats");
		}
		String rawMergeKey = config.getString("stat-merge-key", null);
		if (rawMergeKey != null && !rawMergeKey.isBlank()) {
			statMergeKey = rawMergeKey.trim().toLowerCase();
		}
	}

	public boolean hasXP() {
		return xp != null;
	}

	public String getXP() {
		return xp;
	}

	public boolean statIsProtected(StatModifier mod) {
		return protectedStats.contains(mod.getType());
	}
	
	public boolean canBeBase() {
		return base;
	}

	public int getTier() {
		return tier;
	}

	public boolean hasTier() {
		return tier > 0;
	}

	public String getPermission() {
		return permission;
	}

	public boolean hasPermission() {
		return permission != null && !permission.isBlank();
	}

	public IngredientType getType() {
		return type;
	}

	/** Bucket id for stat averaging; falls back to ingredient type id. */
	public String getStatMergeBucketId() {
		if (statMergeKey != null) {
			return statMergeKey;
		}
		if (type == null) {
			return null;
		}
		return type.getId().toLowerCase();
	}

	public int getWeight() {
		return weight;
	}
	public int getValue() {
		return value;
	}
	public NamingScheme getScheme() {
		return scheme;
	}
	public ModelScheme getModelScheme() {
		return modelScheme;
	}

	public StatData getStatData() {
		return statData;
	}

	public HashMap<CraftingHit, Integer> getHits() {
		return hits;
	}

	public String buildRevisionContent() {
		StringBuilder sb = new StringBuilder();
		sb.append("weight=").append(weight).append(';');
		sb.append("value=").append(value).append(';');
		sb.append("tier=").append(tier).append(';');
		sb.append("permission=").append(permission != null ? permission : "").append(';');
		sb.append("statMergeKey=").append(statMergeKey != null ? statMergeKey : "").append(';');
		sb.append("xp=").append(xp != null ? xp : "").append(';');
		List<String> stats = statData.getModifiers().stream()
				.map(m -> m.getType() + "(" + m.getAmount() + ")")
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.collect(Collectors.toList());
		sb.append("stats=").append(String.join(",", stats)).append(';');
		List<String> hitParts = hits.entrySet().stream()
				.map(e -> e.getKey().getId() + "." + e.getValue())
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.collect(Collectors.toList());
		sb.append("hits=").append(String.join(",", hitParts)).append(';');
		List<String> protectedCopy = new ArrayList<>(protectedStats);
		Collections.sort(protectedCopy, String.CASE_INSENSITIVE_ORDER);
		sb.append("protected=").append(String.join(",", protectedCopy));
		return sb.toString();
	}
	
	
}
