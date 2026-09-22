package net.tfminecraft.advancedcrafting.utils;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;

import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;

public final class MMOStatApplicator {
	private MMOStatApplicator() {
	}

	public static void applyExternalLayer(MMOItem mmo, StatData stats, Set<String> managedStatIds) {
		applyExternalLayer(mmo, stats, managedStatIds, false);
	}

	public static void applyExternalLayer(MMOItem mmo, StatData stats, Set<String> managedStatIds,
			boolean clearExistingExternal) {
		if (clearExistingExternal && managedStatIds != null) {
			clearExternalLayer(mmo, managedStatIds);
			zeroOriginalLayer(mmo, managedStatIds);
		}
		Set<String> applied = new HashSet<>();
		if (stats != null) {
			for (StatModifier mod : stats.getModifiers()) {
				String statId = mod.getType().toLowerCase();
				if (applied.contains(statId)) {
					continue;
				}
				if (isDurabilityStat(statId)) {
					applyDurability(mmo, mod.getAmount());
					applied.add("max_item_damage");
					applied.add("durability");
				} else {
					applyDoubleStat(mmo, statId, mod.getAmount());
					applied.add(statId);
				}
			}
		}

		if (managedStatIds == null) {
			return;
		}
		for (String managedId : managedStatIds) {
			String lower = managedId.toLowerCase();
			if (applied.contains(lower)) {
				continue;
			}
			if (isDurabilityStat(lower)) {
				applyDurability(mmo, 0);
			} else {
				applyDoubleStat(mmo, lower, 0);
			}
		}
	}

	public static boolean isDurabilityStat(String statId) {
		return statId.equalsIgnoreCase("max_item_damage") || statId.equalsIgnoreCase("durability");
	}

	private static void applyDurability(MMOItem mmo, double value) {
		DoubleData data = new DoubleData(value);
		mmo.setData(ItemStats.MAX_DURABILITY, data);
		mmo.setData(ItemStats.CUSTOM_DURABILITY, data);
	}

	private static void applyDoubleStat(MMOItem mmo, String statId, double value) {
		ItemStat<?, ?> itemStat = MMOItems.plugin.getStats().get(statId.toUpperCase());
		if (itemStat == null) {
			Bukkit.getLogger().warning("AC: Unknown MMOItems stat: " + statId);
			return;
		}
		DoubleData data = new DoubleData(value);
		mmo.setData(itemStat, data);
		registerExternal(mmo, itemStat, data);
	}

	private static void clearExternalLayer(MMOItem mmo, Set<String> managedStatIds) {
		for (String statId : managedStatIds) {
			if (isDurabilityStat(statId)) {
				continue;
			}
			ItemStat<?, ?> itemStat = MMOItems.plugin.getStats().get(statId.toUpperCase());
			if (itemStat == null) {
				continue;
			}
			StatHistory hist = mmo.computeStatHistory(itemStat);
			if (hist != null) {
				hist.clearExternalData();
				mmo.setStatHistory(itemStat, hist);
			}
		}
	}

	/** AC owns managed stats entirely via the external layer; MMO template OG values must not stack on top. */
	private static void zeroOriginalLayer(MMOItem mmo, Set<String> managedStatIds) {
		for (String statId : managedStatIds) {
			if (isDurabilityStat(statId)) {
				continue;
			}
			ItemStat<?, ?> itemStat = MMOItems.plugin.getStats().get(statId.toUpperCase());
			if (itemStat == null) {
				continue;
			}
			StatHistory hist = mmo.computeStatHistory(itemStat);
			if (hist != null) {
				Object og = hist.getOriginalData();
				if (og instanceof DoubleData doubleOg) {
					doubleOg.setValue(0);
					mmo.setStatHistory(itemStat, hist);
				}
			}
			mmo.setData(itemStat, new DoubleData(0));
		}
	}

	@SuppressWarnings("deprecation")
	private static void registerExternal(MMOItem mmo, ItemStat<?, ?> itemStat, DoubleData data) {
		StatHistory hist = mmo.computeStatHistory(itemStat);
		if (hist != null) {
			hist.registerExternalData(data);
			mmo.setStatHistory(itemStat, hist);
		}
	}
}
