package net.tfminecraft.advancedcrafting.objects.data;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.NamespacedKey;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.advancedcrafting.utils.PDCKeys;
import net.tfminecraft.advancedcrafting.AdvancedCrafting;

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
		meta.getPersistentDataContainer().set(PDCKeys.scrapBase(), PersistentDataType.STRING, baseId.toLowerCase(Locale.ROOT));
		item.setItemMeta(meta);
	}

	/** Base ingredient id the scrap came from, or null for untagged items (including scrap forged before tagging). */
	public static String readBaseId(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		return item.getItemMeta().getPersistentDataContainer().get(PDCKeys.scrapBase(), PersistentDataType.STRING);
	}

	/** Records consumed ingredient quantities per scrap, independently of the legacy base tag. */
	public static void applyInputs(ItemStack item, Map<String, Integer> amounts) {
		ItemMeta meta = item.getItemMeta();
		var container = meta.getPersistentDataContainer();
		var inputs = container.getAdapterContext().newPersistentDataContainer();
		amounts.forEach((id, amount) -> inputs.set(
				new NamespacedKey(AdvancedCrafting.plugin, id.toLowerCase(Locale.ROOT)),
				PersistentDataType.INTEGER, amount));
		container.set(PDCKeys.scrapInputs(), PersistentDataType.TAG_CONTAINER, inputs);
		item.setItemMeta(meta);
	}

	/** Legacy scrap contains only a base tag; its catalysts cannot be reconstructed. */
	public static Map<String, Integer> readInputs(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return Map.of();
		var inputs = item.getItemMeta().getPersistentDataContainer().get(
				PDCKeys.scrapInputs(), PersistentDataType.TAG_CONTAINER);
		if (inputs == null) {
			String base = readBaseId(item);
			return base == null || base.isBlank() ? Map.of() : Map.of(base, 1);
		}
		Map<String, Integer> amounts = new LinkedHashMap<>();
		for (var key : inputs.getKeys()) {
			Integer amount = inputs.get(key, PersistentDataType.INTEGER);
			if (amount != null && amount > 0) amounts.put(key.getKey(), amount);
		}
		return amounts;
	}
}
