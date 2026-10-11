package net.tfminecraft.advancedcrafting.utils;

import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.Indyuce.mmocore.api.player.PlayerData;

/**
 * The forging craft roll, made when an imperfect piece is finished: a d20 plus a modifier from the player's base
 * attribute, like RPCharacters' /roll. Meeting the DC earns a clue about the hits; the roll never changes the piece.
 * A natural 20 always succeeds and a natural 1 always fails, whatever the modifier.
 */
public final class D20Roll {

	/** Rolls the die. Swapped by tests; {@code force-natural} in the config pins it for Dev checks. */
	public static IntSupplier die = D20Roll::rollDie;

	private static TreeMap<Integer, Integer> modifiers = defaultModifiers();
	private static int forcedNatural;
	public static String attribute = "strength";
	public static int clueDc = 12;

	private final int natural;
	private final int modifier;

	public D20Roll(int natural, int modifier) {
		this.natural = natural;
		this.modifier = modifier;
	}

	public static D20Roll roll(Player player) {
		return new D20Roll(die.getAsInt(), modifierFor(player, attribute));
	}

	public int getNatural() {
		return natural;
	}

	public int getModifier() {
		return modifier;
	}

	public boolean isCritical() {
		return natural >= 20;
	}

	public boolean isFumble() {
		return natural <= 1;
	}

	/** A natural 1 ignores the modifier. */
	public int getTotal() {
		return isFumble() ? natural : natural + modifier;
	}

	public boolean meets(int dc) {
		return isCritical() || (!isFumble() && getTotal() >= dc);
	}

	/** How the roll reads in chat, e.g. "15 (+2) = 17". */
	public String describe() {
		if (isCritical()) return "§6Natural 20!";
		if (isFumble()) return "§cNatural 1!";
		return "§e" + natural + " §7(" + (modifier >= 0 ? "+" : "") + modifier + ") = §e" + getTotal();
	}

	public static int modifierFor(Player player, String attributeId) {
		Integer value = readBase(player, attributeId);
		return value == null ? 0 : modifier(value);
	}

	/** Values outside the table use its nearest end, so 25 reads like 20 and -3 like 0. */
	public static int modifier(int attributeValue) {
		int key = Math.max(modifiers.firstKey(), Math.min(modifiers.lastKey(), attributeValue));
		return modifiers.floorEntry(key).getValue();
	}

	/** The base attribute, without gear, as /roll reads it. Null when MMOCore cannot tell. */
	static Integer readBase(Player player, String attributeId) {
		if (player == null || attributeId == null || attributeId.isBlank()) return null;
		if (Bukkit.getPluginManager().getPlugin("MMOCore") == null) return null;
		try {
			return PlayerData.get(player).getAttributes().getInstance(attributeId).getBase();
		} catch (Exception ex) {
			return null;
		}
	}

	public static void load(ConfigurationSection section) {
		modifiers = defaultModifiers();
		forcedNatural = 0;
		attribute = "strength";
		clueDc = 12;
		if (section != null) {
			ConfigurationSection table = section.getConfigurationSection("attribute-modifiers");
			if (table != null) {
				TreeMap<Integer, Integer> loaded = new TreeMap<>();
				for (String key : table.getKeys(false)) {
					try {
						loaded.put(Integer.parseInt(key.trim()), table.getInt(key));
					} catch (NumberFormatException ignored) {
						// Not an attribute value; skip it.
					}
				}
				if (!loaded.isEmpty()) modifiers = loaded;
			}
			forcedNatural = section.getInt("force-natural", 0);
			attribute = section.getString("attribute", attribute);
			clueDc = section.getInt("clue-dc", clueDc);
		}
	}

	private static int rollDie() {
		if (forcedNatural >= 1 && forcedNatural <= 20) return forcedNatural;
		return ThreadLocalRandom.current().nextInt(1, 21);
	}

	/** The /roll table: (attribute - 10) / 2, rounded down, for 0 to 20. */
	private static TreeMap<Integer, Integer> defaultModifiers() {
		TreeMap<Integer, Integer> table = new TreeMap<>();
		for (int value = 0; value <= 20; value++) {
			table.put(value, Math.floorDiv(value - 10, 2));
		}
		return table;
	}
}
