package net.tfminecraft.advancedcrafting.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.data.CraftInput;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.objects.stats.MergeStatModifier;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;

public final class BucketStatAverager {
	private BucketStatAverager() {
	}

	public static StatData compute(Map<String, Integer> materials) {
		if (materials == null || materials.isEmpty()) {
			return new StatData();
		}

		Map<String, Integer> bucketUnitCounts = new HashMap<>();
		Map<String, Map<String, MergeStatModifier>> bucketAccum = new HashMap<>();

		for (Map.Entry<String, Integer> entry : materials.entrySet()) {
			String key = entry.getKey();
			int unitCount = entry.getValue();
			if (unitCount <= 0) {
				continue;
			}

			String bucket = resolveBucket(key);
			if (bucket == null) {
				continue;
			}

			StatData statData = resolveStatData(key);
			if (statData == null) {
				continue;
			}

			bucketUnitCounts.merge(bucket, unitCount, Integer::sum);
			Map<String, MergeStatModifier> accum = bucketAccum.computeIfAbsent(bucket, b -> new HashMap<>());

			for (StatModifier mod : statData.getModifiers()) {
				MergeStatModifier merge = accum.get(mod.getType());
				if (merge == null) {
					merge = new MergeStatModifier(mod.getType());
					accum.put(mod.getType(), merge);
				}
				merge.addWeighted(mod.getAmount(), unitCount);
			}
		}

		StatData result = new StatData();
		for (Map.Entry<String, Map<String, MergeStatModifier>> bucketEntry : bucketAccum.entrySet()) {
			String bucket = bucketEntry.getKey();
			int totalUnits = bucketUnitCounts.getOrDefault(bucket, 0);
			if (totalUnits <= 0) {
				continue;
			}

			StatData bucketAvg = new StatData();
			for (MergeStatModifier merge : bucketEntry.getValue().values()) {
				bucketAvg.addModifier(merge.createWithDenominator(totalUnits));
			}
			result.mergeFrom(bucketAvg);
		}
		return result;
	}

	public static StatData computeFromInputs(List<CraftInput> inputs) {
		if (inputs == null || inputs.isEmpty()) {
			return new StatData();
		}
		Map<String, Integer> materials = new HashMap<>();
		for (CraftInput input : inputs) {
			if (input == null || input.getAmount() <= 0) {
				continue;
			}
			String kind = input.getKind();
			if (kind == null) {
				continue;
			}
			String key = kind.toLowerCase() + "." + input.getId().toLowerCase();
			materials.merge(key, input.getAmount(), Integer::sum);
		}
		return compute(materials);
	}

	private static String resolveBucket(String key) {
		String[] split = key.split("\\.", 2);
		if (split.length < 2) {
			return null;
		}
		String kind = split[0].toLowerCase();
		String id = split[1].toLowerCase();

		if (kind.equals("ingredient")) {
			Ingredient ing = IngredientLoader.getByString(id);
			if (ing == null) {
				return null;
			}
			return normalizeBucketId(ing.getIngredientData().getStatMergeBucketId());
		}
		if (kind.equals("alloy")) {
			Alloy alloy = AlloyManager.getAlloyById(id);
			if (alloy == null) {
				return null;
			}
			return normalizeBucketId(alloy.getData().getStatMergeBucketId());
		}
		return null;
	}

	private static String normalizeBucketId(String bucketId) {
		if (bucketId == null || bucketId.isBlank()) {
			return null;
		}
		return bucketId.toLowerCase();
	}

	private static StatData resolveStatData(String key) {
		String[] split = key.split("\\.", 2);
		if (split.length < 2) {
			return null;
		}
		String kind = split[0].toLowerCase();
		String id = split[1].toLowerCase();

		if (kind.equals("ingredient")) {
			Ingredient ing = IngredientLoader.getByString(id);
			if (ing == null) {
				return null;
			}
			return ing.getIngredientData().getStatData();
		}
		if (kind.equals("alloy")) {
			Alloy alloy = AlloyManager.getAlloyById(id);
			if (alloy == null) {
				return null;
			}
			return alloy.getData().getStatData();
		}
		return null;
	}
}
