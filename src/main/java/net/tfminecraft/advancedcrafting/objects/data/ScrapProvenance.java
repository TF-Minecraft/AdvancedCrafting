package net.tfminecraft.advancedcrafting.objects.data;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.advancedcrafting.utils.PDCKeys;

/**
 * Records which base ingredient a failed alloy forge consumed, so scrap can be recycled into it.
 */
public final class ScrapProvenance {
	private ScrapProvenance() {
	}

	public static void applyTo(ItemStack item, String baseId) {
		if (item == null || baseId == null || baseId.isBlank()) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return;
		}
		meta.getPersistentDataContainer().set(PDCKeys.scrapBase(), PersistentDataType.STRING, baseId.toLowerCase());
		item.setItemMeta(meta);
	}

	/** Base ingredient id the scrap came from, or null for untagged items (including scrap forged before tagging). */
	public static String readBaseId(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		return item.getItemMeta().getPersistentDataContainer().get(PDCKeys.scrapBase(), PersistentDataType.STRING);
	}
}
