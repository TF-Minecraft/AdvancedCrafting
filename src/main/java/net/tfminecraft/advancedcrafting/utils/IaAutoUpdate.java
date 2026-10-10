package net.tfminecraft.advancedcrafting.utils;

import org.bukkit.inventory.ItemStack;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;

/**
 * ItemsAdder's auto_update rebuilds an item from its definition when that definition changes (or a player's
 * slot hashes no longer match), keeping only amount, durability, persistent data, trim, name and enchantments.
 * On a crafted MMOItems piece wearing an ItemsAdder look that wipes its stats, gems and lore (the Forj
 * incident). ItemsAdder skips items whose {@code itemsadder} compound has {@code override_auto_update: true}.
 */
public final class IaAutoUpdate {
	private static final String COMPOUND = "itemsadder";
	private static final String OVERRIDE = "override_auto_update";

	private IaAutoUpdate() {
	}

	/** Whether the item wears an ItemsAdder look that ItemsAdder may still rebuild. */
	public static boolean isExposed(ItemStack item) {
		try {
			return NBT.get(item, nbt -> {
				if (!nbt.hasTag(COMPOUND)) {
					return false;
				}
				ReadableNBT compound = nbt.getCompound(COMPOUND);
				return compound != null && !Boolean.TRUE.equals(compound.getBoolean(OVERRIDE));
			});
		} catch (LinkageError missingNbtApi) {
			// NBTAPI is a soft dependency; without it there is no opt-out to set.
			return false;
		}
	}

	/** Opts the item out of ItemsAdder's auto_update. */
	public static void protect(ItemStack item) {
		try {
			NBT.modify(item, nbt -> {
				nbt.getOrCreateCompound(COMPOUND).setBoolean(OVERRIDE, true);
			});
		} catch (LinkageError missingNbtApi) {
			// NBTAPI is a soft dependency; without it the item simply stays as it is.
		}
	}
}
