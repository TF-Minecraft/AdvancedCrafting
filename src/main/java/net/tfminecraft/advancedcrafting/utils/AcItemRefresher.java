package net.tfminecraft.advancedcrafting.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.advancedcrafting.objects.CraftStack;

public final class AcItemRefresher {
	private AcItemRefresher() {
	}

	public static boolean isManaged(ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		return new CraftStack(item).isCrafted() || AcItemTags.isManaged(item);
	}

	public static boolean isOutdated(ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		CraftStack cs = new CraftStack(item);
		if (cs.isCrafted() && cs.hasOutdatedInputs()) {
			return true;
		}
		return AcItemLoreRefresher.isOutdated(item);
	}

	public static ItemStack refreshIfOutdated(ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return item;
		}
		ItemStack current = item;
		CraftStatRefresher.RefreshResult crafted = CraftStatRefresher.refreshIfOutdated(item);
		if (crafted.isChanged()) {
			current = crafted.getItem();
		} else {
			AcItemLoreRefresher.RefreshResult lore = AcItemLoreRefresher.refreshIfOutdated(item);
			if (lore.isChanged()) {
				current = lore.getItem();
			}
		}
		// After the stat refresh, which rebuilds from the item's own (possibly old) class list. A failure here keeps
		// the stat refresh already done.
		ItemStack synced;
		try {
			synced = RequiredClassSync.sync(current);
		} catch (RuntimeException e) {
			Bukkit.getLogger().warning("[AC][ClassSync] could not update the classes of an item, kept as is: " + e);
			synced = current;
		}
		return ArmourLookMigrator.migrate(synced);
	}
}
