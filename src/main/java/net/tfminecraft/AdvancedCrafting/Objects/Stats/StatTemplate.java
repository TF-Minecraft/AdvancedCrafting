package net.tfminecraft.AdvancedCrafting.Objects.Stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class StatTemplate {
	private String id;
	private String name;
	private String iconPath;
	private List<String> stats = new ArrayList<>();
	private Set<String> allowedIngredientStats = new HashSet<>();
	private Map<String, Double> factors = new HashMap<>();
	private List<StatModifier> baseStats = new ArrayList<>();
	private Map<String, Double> baseStatAmounts = new HashMap<>();
	private int revision;

	public StatTemplate(String key, ConfigurationSection config) {
		id = key;
		name = StringFormatter.formatHex(config.getString("name", key));
		iconPath = config.getString("icon", "v.paper");
		if (iconPath == null || !iconPath.contains(".")) {
			Bukkit.getLogger().warning("AC: Stat template " + key
					+ " icon must be a TLibs path (e.g. v.iron_sword), got: " + iconPath);
		}
		stats = config.getStringList("stats");
		for (String statId : stats) {
			allowedIngredientStats.add(statId.toLowerCase());
		}
		if (config.isConfigurationSection("factors")) {
			for (String statKey : config.getConfigurationSection("factors").getKeys(false)) {
				factors.put(statKey.toUpperCase(), config.getDouble("factors." + statKey));
			}
		}
		if (config.contains("base-stats")) {
			for (String entry : config.getStringList("base-stats")) {
				StatModifier base = new StatModifier(entry);
				baseStats.add(base);
				baseStatAmounts.put(base.getType().toLowerCase(), base.getAmount());
			}
		}
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public ItemStack getIcon() {
		return TLibs.getItemAPI().getCreator().getItemFromPath(iconPath);
	}

	public List<String> getStats() {
		return stats;
	}

	public boolean allowsIngredientStat(String statId) {
		return allowedIngredientStats.contains(statId.toLowerCase());
	}

	public double getFactor(String statId) {
		Double factor = factors.get(statId.toUpperCase());
		return factor != null ? factor : 1.0;
	}

	public List<StatModifier> getBaseStats() {
		return baseStats;
	}

	public boolean hasBaseStat(String statId) {
		return baseStatAmounts.containsKey(statId.toLowerCase());
	}

	public double getBaseAmount(String statId) {
		Double amount = baseStatAmounts.get(statId.toLowerCase());
		return amount != null ? amount : 0;
	}

	public int getRevision() {
		return revision;
	}

	public void setRevision(int revision) {
		this.revision = revision;
	}

	public String buildRevisionContent() {
		StringBuilder sb = new StringBuilder();
		List<String> statIds = new ArrayList<>(stats);
		Collections.sort(statIds, String.CASE_INSENSITIVE_ORDER);
		sb.append("stats=").append(String.join(",", statIds)).append(';');
		List<String> baseParts = baseStats.stream()
				.map(m -> m.getType() + "(" + m.getAmount() + ")")
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.collect(Collectors.toList());
		sb.append("base=").append(String.join(",", baseParts)).append(';');
		TreeMap<String, Double> sortedFactors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		sortedFactors.putAll(factors);
		List<String> factorParts = sortedFactors.entrySet().stream()
				.map(e -> e.getKey().toLowerCase() + "(" + e.getValue() + ")")
				.collect(Collectors.toList());
		sb.append("factors=").append(String.join(",", factorParts));
		return sb.toString();
	}
}
