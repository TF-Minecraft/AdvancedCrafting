package net.tfminecraft.AdvancedCrafting.Utils;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.AdvancedCrafting.Objects.CraftStack;

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
		CraftStatRefresher.RefreshResult crafted = CraftStatRefresher.refreshIfOutdated(item);
		if (crafted.isChanged()) {
			return crafted.getItem();
		}
		AcItemLoreRefresher.RefreshResult lore = AcItemLoreRefresher.refreshIfOutdated(item);
		if (lore.isChanged()) {
			return lore.getItem();
		}
		return item;
	}
}
