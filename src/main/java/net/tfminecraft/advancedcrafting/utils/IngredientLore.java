package net.tfminecraft.advancedcrafting.utils;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.objects.data.AlloyRecipe;
import net.tfminecraft.advancedcrafting.objects.data.IngredientData;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;

public final class IngredientLore {
	private IngredientLore() {
	}

	public static final class Block {
		public final int start;
		public final int length;

		public Block(int start, int length) {
			this.start = start;
			this.length = length;
		}
	}

	public static String formatTypeLine(IngredientData data) {
		return formatTypeLine(data.getType());
	}

	public static String formatTypeLine(IngredientType type) {
		return StringFormatter.formatHex("#cf7c72Type: #d9bb93" + type.getName());
	}

	public static String formatTierLine(int tier) {
		return StringFormatter.formatHex("§e[#ebd05bTier " + toRoman(tier) + "§e]");
	}

	public static String formatCatalystLine() {
		return StringFormatter.formatHex("§e[#d190deCatalyst§e]");
	}

	public static Block applyTypeAndRole(List<String> lore, IngredientData data) {
		return applyBlock(lore, formatTypeLine(data), roleLine(data), data.getStatData());
	}

	public static Block spliceTypeAndRole(List<String> lore, int start, int oldLen, IngredientData data) {
		return spliceBlock(lore, start, oldLen, formatTypeLine(data), roleLine(data), data.getStatData());
	}

	public static Block applyAlloyLore(List<String> lore, IngredientType type, int tier, StatData stats) {
		return applyBlock(lore, formatTypeLine(type), formatTierLine(tier), stats);
	}

	public static Block spliceAlloyLore(List<String> lore, int start, int oldLen, IngredientType type, int tier,
			StatData stats) {
		return spliceBlock(lore, start, oldLen, formatTypeLine(type), formatTierLine(tier), stats);
	}

	public static void appendTypeAndRole(List<String> lore, IngredientData data) {
		applyTypeAndRole(lore, data);
	}

	private static Block applyBlock(List<String> lore, String typeLine, String roleLine, StatData stats) {
		List<String> block = buildLines(typeLine, roleLine, stats);
		int start;
		if (lore.isEmpty() || isBlankLoreLine(lore.get(0))) {
			start = 0;
			if (lore.isEmpty()) {
				lore.addAll(block);
			} else {
				lore.set(0, block.get(0));
				lore.addAll(1, block.subList(1, block.size()));
			}
		} else {
			lore.add(" ");
			start = lore.size();
			lore.addAll(block);
		}
		return new Block(start, block.size());
	}

	private static Block spliceBlock(List<String> lore, int start, int oldLen, String typeLine, String roleLine,
			StatData stats) {
		int safeStart = Math.max(0, start);
		int removeCount = Math.max(0, oldLen);
		if (safeStart > lore.size()) {
			safeStart = lore.size();
		}
		int end = Math.min(lore.size(), safeStart + removeCount);
		for (int i = end - 1; i >= safeStart; i--) {
			lore.remove(i);
		}
		List<String> block = buildLines(typeLine, roleLine, stats);
		lore.addAll(safeStart, block);
		return new Block(safeStart, block.size());
	}

	private static List<String> buildLines(String typeLine, String roleLine, StatData stats) {
		List<String> lines = new ArrayList<>();
		lines.add(typeLine);
		lines.add(roleLine);
		if (Cache.showIngredientStats && stats != null && stats.hasModifiers()) {
			for (StatModifier modifier : stats.getModifiers()) {
				lines.add(StatToString.getFullString(modifier));
			}
		}
		return lines;
	}

	private static String roleLine(IngredientData data) {
		if (data.canBeBase() && data.hasTier()) {
			return formatTierLine(data.getTier());
		}
		return formatCatalystLine();
	}

	public static int resolveAlloyTier(AlloyRecipe recipe, String alloyId) {
		if (recipe == null) {
			org.bukkit.Bukkit.getLogger().warning(
					"AC: Alloy " + alloyId + " has no recipe; defaulting tier to I.");
			return 1;
		}
		Ingredient base = IngredientLoader.getByString(recipe.getBaseId());
		if (base == null || !base.getIngredientData().hasTier()) {
			org.bukkit.Bukkit.getLogger().warning(
					"AC: Alloy " + alloyId + " base '" + recipe.getBaseId()
							+ "' has no tier; defaulting tier to I.");
			return 1;
		}
		return base.getIngredientData().getTier();
	}

	private static String toRoman(int tier) {
		return switch (tier) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> String.valueOf(tier);
		};
	}

	private static boolean isBlankLoreLine(String line) {
		if (line == null) {
			return true;
		}
		return line.replaceAll("§.", "").trim().isEmpty();
	}
}
