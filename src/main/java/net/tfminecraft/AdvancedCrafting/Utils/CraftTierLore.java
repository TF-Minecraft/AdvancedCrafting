package net.tfminecraft.AdvancedCrafting.Utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class CraftTierLore {
	private CraftTierLore() {
	}

	public static int insertTierLine(List<String> lore, int tier) {
		if (tier <= 0) {
			return -1;
		}
		String tierLine = IngredientLore.formatTierLine(tier);
		int index = findFirstFreeLine(lore);
		if (index < lore.size()) {
			lore.set(index, tierLine);
		} else {
			lore.add(tierLine);
		}
		return index;
	}

	public static void updateTierLine(List<String> lore, int index, int tier) {
		if (index < 0 || tier <= 0) {
			return;
		}
		while (lore.size() <= index) {
			lore.add("");
		}
		lore.set(index, IngredientLore.formatTierLine(tier));
	}

	public static void applyPdc(ItemStack item, int loreIndex, int tier) {
		if (item == null || !item.hasItemMeta() || loreIndex < 0 || tier <= 0) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(PDCKeys.craftTierLoreStart(), PersistentDataType.INTEGER, loreIndex);
		meta.getPersistentDataContainer().set(PDCKeys.craftMajorityTier(), PersistentDataType.INTEGER, tier);
		item.setItemMeta(meta);
	}

	public static void applyTierLine(ItemStack item, int tier) {
		if (item == null || !item.hasItemMeta() || tier <= 0) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
		int tierIndex = insertTierLine(lore, tier);
		meta.setLore(lore);
		item.setItemMeta(meta);
		applyPdc(item, tierIndex, tier);
	}

	public static void refreshTierLine(ItemStack item, int tier) {
		if (item == null || !item.hasItemMeta() || tier <= 0) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		Integer index = meta.getPersistentDataContainer().get(PDCKeys.craftTierLoreStart(), PersistentDataType.INTEGER);
		if (index == null) {
			return;
		}
		List<String> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
		updateTierLine(lore, index, tier);
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(PDCKeys.craftMajorityTier(), PersistentDataType.INTEGER, tier);
		item.setItemMeta(meta);
	}

	private static int findFirstFreeLine(List<String> lore) {
		if (lore.isEmpty()) {
			return 0;
		}
		if (isBlankLoreLine(lore.get(0))) {
			return 0;
		}
		for (int i = 0; i < lore.size(); i++) {
			if (isBlankLoreLine(lore.get(i))) {
				return i;
			}
		}
		lore.add(0, "");
		return 0;
	}

	private static boolean isBlankLoreLine(String line) {
		if (line == null) {
			return true;
		}
		return line.replaceAll("§.", "").trim().isEmpty();
	}
}
