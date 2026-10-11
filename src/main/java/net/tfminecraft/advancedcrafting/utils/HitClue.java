package net.tfminecraft.advancedcrafting.utils;

import java.util.Comparator;
import java.util.Map;

import net.tfminecraft.advancedcrafting.objects.crafting.hits.CraftingHit;
import net.tfminecraft.tlibs.objects.utils.IntCounter;

/**
 * The hint a crafter may get after finishing an imperfect piece. It names the hit that was closest to right
 * without being right, and says only which way it was off and roughly how far, never the needed count.
 */
public final class HitClue {

	public static final String PREFIX = "§7Looking the piece over, ";
	public static final String NO_CLUE = PREFIX + "you can't tell what went wrong with it.";

	private HitClue() {
	}

	/** The clue line, or null when every hit was done exactly. */
	public static String clueFor(Map<CraftingHit, IntCounter> hits) {
		Map.Entry<CraftingHit, IntCounter> closest = hits.entrySet().stream()
				.filter(e -> e.getValue().getCurrent() != e.getValue().getNeeded())
				.min(Comparator.<Map.Entry<CraftingHit, IntCounter>>comparingInt(e -> difference(e.getValue()))
						.thenComparingDouble(e -> relativeError(e.getValue()))
						.thenComparing(e -> e.getKey().getId()))
				.orElse(null);
		if (closest == null) return null;
		IntCounter c = closest.getValue();
		String name = closest.getKey().getName() + "§7";
		if (c.getCurrent() == 0) return PREFIX + "you sense it needed some " + name + " work.";
		if (c.getNeeded() == 0) return PREFIX + "you sense it took " + name + " work it didn't need.";
		boolean little = difference(c) == 1 || relativeError(c) <= 0.25;
		boolean far = relativeError(c) > 0.75;
		if (c.getCurrent() < c.getNeeded()) {
			String amount = little ? "a little more" : far ? "much more" : "more";
			return PREFIX + "you sense it needed " + amount + " " + name + " work.";
		}
		String amount = little ? "a little too much" : far ? "far too much" : "too much";
		return PREFIX + "you sense it took " + amount + " " + name + " work.";
	}

	private static int difference(IntCounter c) {
		return Math.abs(c.getCurrent() - c.getNeeded());
	}

	/** How far off a hit is for its size; any use of a hit the piece did not need is fully wrong. */
	private static double relativeError(IntCounter c) {
		if (c.getNeeded() <= 0) return Double.POSITIVE_INFINITY;
		return (double) difference(c) / c.getNeeded();
	}
}
