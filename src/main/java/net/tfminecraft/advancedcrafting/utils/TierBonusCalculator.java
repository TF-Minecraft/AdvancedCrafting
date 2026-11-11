package net.tfminecraft.advancedcrafting.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.data.AlloyData;
import net.tfminecraft.advancedcrafting.objects.data.IngredientData;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;
import net.tfminecraft.advancedcrafting.objects.stats.StatTemplate;

/**
 * Applies a stat template's {@code tier-bonus}: for each ingredient type the template names, the
 * average tier of the materials of that type (weighted by amount) sets the bonus.
 */
public final class TierBonusCalculator {
	private TierBonusCalculator() {
	}

	/** Type id and tier of one material; null-safe factory methods return null when either is missing. */
	public record TierSource(String typeId, int tier) {
		public static TierSource of(IngredientType type, int tier) {
			if (type == null || tier <= 0) {
				return null;
			}
			return new TierSource(type.getId().toLowerCase(), tier);
		}

		public static TierSource of(IngredientData data) {
			return data == null ? null : of(data.getType(), data.getTier());
		}

		public static TierSource of(AlloyData data) {
			return data == null ? null : of(data.getType(), data.getTier());
		}
	}

	/** Adds the template's tier bonuses for these materials ({@code kind.id -> amount}) to {@code stats}. */
	public static void apply(StatData stats, StatTemplate template, Map<String, Integer> materials) {
		if (template == null || !template.hasTierBonuses() || materials == null) {
			return;
		}
		Map<String, int[]> tierSums = new HashMap<>(); // type id -> {sum of tier x amount, amount}
		for (Map.Entry<String, Integer> entry : materials.entrySet()) {
			int amount = entry.getValue();
			if (amount <= 0) {
				continue;
			}
			TierSource source = resolve(entry.getKey());
			if (source == null || !template.hasTierBonus(source.typeId())) {
				continue;
			}
			int[] sum = tierSums.computeIfAbsent(source.typeId(), t -> new int[2]);
			sum[0] += source.tier() * amount;
			sum[1] += amount;
		}
		// Sum per stat first: StatData rounds to 2 decimals on every merge, so adding -0.125 twice
		// would give 3.26 instead of 3.25.
		Map<String, Double> totals = new TreeMap<>();
		for (Map.Entry<String, int[]> entry : tierSums.entrySet()) {
			double averageTier = (double) entry.getValue()[0] / entry.getValue()[1];
			StatModifier bonus = template.getTierBonus(entry.getKey(), averageTier);
			if (bonus != null) {
				totals.merge(bonus.getType(), bonus.getAmount(), Double::sum);
			}
		}
		for (Map.Entry<String, Double> total : totals.entrySet()) {
			stats.addModifier(new StatModifier(total.getKey(), total.getValue()));
		}
	}

	/** {@code source} plus the template's bonus for one material of that type and tier (preview). */
	public static StatData withBonus(StatData source, StatTemplate template, TierSource tier) {
		if (tier == null) {
			return source;
		}
		StatModifier bonus = template.getTierBonus(tier.typeId(), tier.tier());
		if (bonus == null) {
			return source;
		}
		StatData result = StatData.copyOf(source);
		result.addModifier(bonus);
		return result;
	}

	static TierSource resolve(String materialKey) {
		String[] split = materialKey.split("\\.", 2);
		if (split.length < 2) {
			return null;
		}
		String kind = split[0].toLowerCase();
		String id = split[1].toLowerCase();
		if (kind.equals("ingredient")) {
			Ingredient ingredient = IngredientLoader.getByString(id);
			return ingredient == null ? null : TierSource.of(ingredient.getIngredientData());
		}
		if (kind.equals("alloy")) {
			Alloy alloy = AlloyManager.getAlloyById(id);
			return alloy == null ? null : TierSource.of(alloy.getData());
		}
		return null;
	}
}
