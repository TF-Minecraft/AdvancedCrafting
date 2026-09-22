package net.tfminecraft.advancedcrafting.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;
import net.tfminecraft.advancedcrafting.objects.stats.StatTemplate;

public final class StatTemplateMath {
	private StatTemplateMath() {
	}

	private static final String STAT_LABEL = "#b8ae61";
	private static final String VALUE_POS = "#45c46f";
	private static final String VALUE_NEG = "#d13530";
	private static final String SUFFIX_POS = "#87d65c";
	private static final String SUFFIX_NEG = "#d65c5c";

	public static double getFactor(StatTemplate template, String statId) {
		return template.getFactor(statId);
	}

	public static double applyGlobalOffset(double raw, String statId) {
		Double offset = Cache.globalStatOffsets.get(statId.toLowerCase());
		if (offset == null || offset == 0) {
			return raw;
		}
		return round(raw / offset);
	}

	public static double applyScaledValue(double raw, String statId, StatTemplate template) {
		return applyFactor(applyGlobalOffset(raw, statId), getFactor(template, statId));
	}

	/** Config-scale value for converter preview (reverses global offset division). */
	public static double applyScaledValueForPreview(double raw, String statId, StatTemplate template) {
		double scaled = applyScaledValue(raw, statId, template);
		Double offset = Cache.globalStatOffsets.get(statId.toLowerCase());
		if (offset == null || offset == 0) {
			return scaled;
		}
		return round(scaled * offset);
	}

	public static double applyFactor(double raw, double factor) {
		return round(raw * factor);
	}

	private static double round(double value) {
		BigDecimal bd = new BigDecimal(value);
		double abs = Math.abs(value);
		if (abs >= 0.01 || abs == 0) {
			return bd.setScale(2, RoundingMode.HALF_UP).doubleValue();
		}
		return bd.setScale(3, RoundingMode.HALF_UP).doubleValue();
	}

	public static String formatPercentSuffix(double factor) {
		if (factor == 1.0) {
			return "";
		}
		double percent = (factor - 1.0) * 100.0;
		BigDecimal bd = new BigDecimal(percent);
		percent = bd.setScale(0, RoundingMode.HALF_UP).doubleValue();
		String color = percent >= 0 ? SUFFIX_POS : SUFFIX_NEG;
		String sign = percent > 0 ? "+" : "";
		return StringFormatter.formatHex(" §7(" + color + sign + (int) percent + "%§7)");
	}

	private static String formatValuePart(double value) {
		if (value >= 0) {
			return VALUE_POS + "+" + value;
		}
		return VALUE_NEG + value;
	}

	private static String formatPreviewLine(String statId, double rawValue, StatTemplate template) {
		double factor = getFactor(template, statId);
		double finalValue = applyScaledValueForPreview(rawValue, statId, template);
		String suffix = formatPercentSuffix(factor);
		return StringFormatter.formatHex(
				"§f- " + STAT_LABEL + StatToString.get(statId) + " " + formatValuePart(finalValue))
				+ suffix;
	}

	public static boolean hasOverlap(StatData source, StatTemplate template) {
		for (String statId : template.getStats()) {
			if (sourceHasStat(source, statId)) {
				return true;
			}
		}
		return false;
	}

	public static List<String> getPreviewLines(StatData source, StatTemplate template) {
		List<String> lines = new ArrayList<>();
		Set<String> shown = new HashSet<>();

		for (String statId : template.getStats()) {
			if (!sourceHasStat(source, statId) && !template.hasBaseStat(statId)) {
				continue;
			}
			double raw = resolveRawValue(source, template, statId);
			lines.add(formatPreviewLine(statId, raw, template));
			shown.add(statId.toLowerCase());
		}

		for (StatModifier base : template.getBaseStats()) {
			String statId = base.getType();
			if (shown.contains(statId.toLowerCase())) {
				continue;
			}
			double raw = resolveRawValue(source, template, statId);
			lines.add(formatPreviewLine(statId, raw, template));
			shown.add(statId.toLowerCase());
		}

		return lines;
	}

	public static StatData filterAndApply(StatData source, StatTemplate template) {
		StatData result = new StatData();
		Set<String> applied = new HashSet<>();

		for (String statId : template.getStats()) {
			if (!sourceHasStat(source, statId) && !template.hasBaseStat(statId)) {
				continue;
			}
			double raw = resolveRawValue(source, template, statId);
			result.addModifier(new StatModifier(statId, applyScaledValue(raw, statId, template)));
			applied.add(statId.toLowerCase());
		}

		for (StatModifier base : template.getBaseStats()) {
			String statId = base.getType();
			if (applied.contains(statId.toLowerCase())) {
				continue;
			}
			double raw = resolveRawValue(source, template, statId);
			result.addModifier(new StatModifier(statId, applyScaledValue(raw, statId, template)));
		}

		return result;
	}

	private static double resolveRawValue(StatData source, StatTemplate template, String statId) {
		double base = template.hasBaseStat(statId) ? template.getBaseAmount(statId) : 0;
		if (!template.allowsIngredientStat(statId)) {
			return base;
		}
		double fromSource = sourceHasStat(source, statId) ? getSourceAmount(source, statId) : 0;
		return base + fromSource;
	}

	private static boolean sourceHasStat(StatData source, String statId) {
		for (StatModifier mod : source.getModifiers()) {
			if (mod.getType().equalsIgnoreCase(statId)) {
				return true;
			}
		}
		return false;
	}

	private static double getSourceAmount(StatData source, String statId) {
		for (StatModifier mod : source.getModifiers()) {
			if (mod.getType().equalsIgnoreCase(statId)) {
				return mod.getAmount();
			}
		}
		return 0;
	}
}
