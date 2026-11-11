package net.tfminecraft.advancedcrafting.objects.stats;

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

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class StatTemplate {
	private String id;
	private String name;
	private String iconPath;
	private List<String> stats = new ArrayList<>();
	private Set<String> allowedIngredientStats = new HashSet<>();
	private Map<String, Double> factors = new HashMap<>();
	private List<StatModifier> baseStats = new ArrayList<>();
	private Map<String, Double> baseStatAmounts = new HashMap<>();
	/** Ingredient type id -> stat added per tier of that type's average material tier. */
	private Map<String, StatModifier> tierBonuses = new TreeMap<>();
	private double tierBonusNeutral;
	private int revision;

	public StatTemplate(String key, ConfigurationSection config) {
		id = key;
		name = StringFormatter.formatHex(config.getString("name", key));
		iconPath = config.getString("icon", "v.paper");
		if (!iconPath.contains(".")) {
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
		loadTierBonuses(key, config);
	}

	/**
	 * {@code tier-bonus: {leather: attack_damage(0.125)}} adds 0.125 x (average leather tier -
	 * {@code tier-bonus-neutral}) to the crafted item. The stat must also be in {@code stats}.
	 */
	private void loadTierBonuses(String key, ConfigurationSection config) {
		tierBonusNeutral = config.getDouble("tier-bonus-neutral", 0);
		ConfigurationSection section = config.getConfigurationSection("tier-bonus");
		if (section == null) {
			return;
		}
		for (String typeId : section.getKeys(false)) {
			StatModifier bonus;
			try {
				bonus = new StatModifier(section.getString(typeId));
			} catch (RuntimeException e) {
				Bukkit.getLogger().warning("AC: Stat template " + key + " has an invalid tier-bonus for "
						+ typeId + ": " + section.get(typeId));
				continue;
			}
			if (!allowsIngredientStat(bonus.getType())) {
				Bukkit.getLogger().warning("AC: Stat template " + key + " tier-bonus stat " + bonus.getType()
						+ " is not in its stats list, so it has no effect");
			}
			tierBonuses.put(typeId.toLowerCase(), new StatModifier(bonus.getType().toLowerCase(), bonus.getAmount()));
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

	public boolean hasTierBonuses() {
		return !tierBonuses.isEmpty();
	}

	public boolean hasTierBonus(String typeId) {
		return typeId != null && tierBonuses.containsKey(typeId.toLowerCase());
	}

	/**
	 * The stat this template adds for materials of {@code typeId} at {@code averageTier}, or null when
	 * the type has no bonus or the bonus is zero (average tier equal to the neutral tier).
	 */
	public StatModifier getTierBonus(String typeId, double averageTier) {
		if (!hasTierBonus(typeId)) {
			return null;
		}
		StatModifier perTier = tierBonuses.get(typeId.toLowerCase());
		double amount = perTier.getAmount() * (averageTier - tierBonusNeutral);
		if (amount == 0) {
			return null;
		}
		return new StatModifier(perTier.getType(), amount);
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
		// Only templates that use tier bonuses add this part, so every other template keeps its revision.
		if (hasTierBonuses()) {
			List<String> bonusParts = tierBonuses.entrySet().stream()
					.map(e -> e.getKey() + ":" + e.getValue().getType() + "(" + e.getValue().getAmount() + ")")
					.collect(Collectors.toList());
			sb.append(";tierBonus=").append(String.join(",", bonusParts))
					.append(";tierBonusNeutral=").append(tierBonusNeutral);
		}
		return sb.toString();
	}
}
