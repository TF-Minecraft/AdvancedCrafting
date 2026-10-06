package net.tfminecraft.advancedcrafting.utils;

import java.util.Set;
import java.util.TreeSet;

import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;

/**
 * Keeps alloys in line with later changes to their base ingredient. An alloy keeps its own
 * difference from the base (what the catalysts did); only the base's share moves.
 */
public final class AlloyRebaser {
	private static final double EPSILON = 1e-9;

	private AlloyRebaser() {
	}

	/**
	 * The base stats an alloy was forged with. Alloys that predate the record use the live base,
	 * overridden by {@code alloy-legacy-base.stats} when their file is older than the cutoff.
	 */
	public static StatData snapshotFor(StatData recorded, String baseId, StatData liveBase, long fileModified) {
		if (recorded != null) {
			return recorded;
		}
		StatData snapshot = StatData.copyOf(liveBase);
		StatData legacy = Cache.alloyLegacyBaseStats.get(baseId.toLowerCase());
		if (legacy == null || Cache.alloyLegacyForgedBefore <= 0 || fileModified >= Cache.alloyLegacyForgedBefore) {
			return snapshot;
		}
		for (StatModifier m : legacy.getModifiers()) {
			set(snapshot, m.getType(), m.getAmount());
		}
		return snapshot;
	}

	/**
	 * Moves each stat by how much the base changed since forging, clamped to 0 and to the
	 * forge cap (live base x max-factor). Returns null when the base did not change.
	 */
	public static StatData rebase(StatData alloy, StatData snapshot, StatData liveBase, double maxFactor) {
		Set<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (StatModifier m : snapshot.getModifiers()) {
			types.add(m.getType());
		}
		for (StatModifier m : liveBase.getModifiers()) {
			types.add(m.getType());
		}
		StatData result = StatData.copyOf(alloy);
		boolean changed = false;
		for (String type : types) {
			double now = amount(liveBase, type);
			double delta = now - amount(snapshot, type);
			if (Math.abs(delta) < EPSILON) {
				continue;
			}
			double old = amount(result, type);
			double value = old + delta;
			if (old >= 0 && value < 0) {
				value = 0;
			}
			if (now > 0) {
				value = Math.min(value, now * maxFactor);
			}
			value = Math.round(value * 100.0) / 100.0;
			if (Math.abs(value) < EPSILON && Math.abs(now) < EPSILON) {
				result.getModifiers().removeIf(m -> m.getType().equalsIgnoreCase(type));
			} else {
				set(result, type, value);
			}
			changed = true;
		}
		return changed ? result : null;
	}

	/** One line per stat that differs, for the startup log. */
	public static String describe(StatData before, StatData after) {
		Set<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (StatModifier m : before.getModifiers()) {
			types.add(m.getType());
		}
		for (StatModifier m : after.getModifiers()) {
			types.add(m.getType());
		}
		StringBuilder sb = new StringBuilder();
		for (String type : types) {
			double was = amount(before, type);
			double now = amount(after, type);
			if (Math.abs(was - now) < EPSILON) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(type).append(' ').append(was).append(" -> ").append(now);
		}
		return sb.toString();
	}

	private static double amount(StatData data, String type) {
		return data.getAmount(new StatModifier(type, 0));
	}

	private static void set(StatData data, String type, double value) {
		for (StatModifier m : data.getModifiers()) {
			if (m.getType().equalsIgnoreCase(type)) {
				m.setAmount(value);
				return;
			}
		}
		data.getModifiers().add(new StatModifier(type, value));
	}
}
