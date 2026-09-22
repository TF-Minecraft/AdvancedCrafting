package net.tfminecraft.AdvancedCrafting.Utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.AdvancedCrafting.Cache.Cache;

public final class AcItemTags {
	public enum Kind {
		INGREDIENT,
		ALLOY
	}

	private AcItemTags() {
	}

	public static boolean isManaged(ItemStack item) {
		return getKind(item) != null;
	}

	public static Kind getKind(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta.getPersistentDataContainer().has(PDCKeys.ingredientId(), PersistentDataType.STRING)) {
			return Kind.INGREDIENT;
		}
		if (meta.getPersistentDataContainer().has(PDCKeys.alloyId(), PersistentDataType.STRING)) {
			return Kind.ALLOY;
		}
		return null;
	}

	public static String getId(ItemStack item) {
		Kind kind = getKind(item);
		if (kind == null || !item.hasItemMeta()) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		if (kind == Kind.INGREDIENT) {
			return meta.getPersistentDataContainer().get(PDCKeys.ingredientId(), PersistentDataType.STRING);
		}
		return meta.getPersistentDataContainer().get(PDCKeys.alloyId(), PersistentDataType.STRING);
	}

	public static int getStoredRevision(ItemStack item) {
		if (!item.hasItemMeta()) {
			return 0;
		}
		Integer revision = item.getItemMeta().getPersistentDataContainer()
				.get(PDCKeys.itemRevision(), PersistentDataType.INTEGER);
		return revision != null ? revision : 0;
	}

	public static int getLoreStart(ItemStack item) {
		if (!item.hasItemMeta()) {
			return -1;
		}
		Integer start = item.getItemMeta().getPersistentDataContainer()
				.get(PDCKeys.loreStart(), PersistentDataType.INTEGER);
		return start != null ? start : -1;
	}

	public static int getLoreLen(ItemStack item) {
		if (!item.hasItemMeta()) {
			return -1;
		}
		Integer len = item.getItemMeta().getPersistentDataContainer()
				.get(PDCKeys.loreLen(), PersistentDataType.INTEGER);
		return len != null ? len : -1;
	}

	public static boolean hasStatsLoreFlag(ItemStack item) {
		return item != null && item.hasItemMeta()
				&& item.getItemMeta().getPersistentDataContainer().has(PDCKeys.statsLore(), PersistentDataType.INTEGER);
	}

	public static boolean getStatsLore(ItemStack item) {
		if (!item.hasItemMeta()) {
			return false;
		}
		Integer flag = item.getItemMeta().getPersistentDataContainer()
				.get(PDCKeys.statsLore(), PersistentDataType.INTEGER);
		return flag != null && flag == 1;
	}

	public static void write(ItemStack item, int revision, IngredientLore.Block block) {
		if (item == null) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return;
		}
		write(meta, revision, block);
		item.setItemMeta(meta);
	}

	public static void write(ItemMeta meta, int revision, IngredientLore.Block block) {
		write(meta, revision, block.start, block.length, Cache.showIngredientStats);
	}

	public static void write(ItemMeta meta, int revision, int loreStart, int loreLen, boolean statsLore) {
		meta.getPersistentDataContainer().set(PDCKeys.itemRevision(), PersistentDataType.INTEGER, revision);
		meta.getPersistentDataContainer().set(PDCKeys.loreStart(), PersistentDataType.INTEGER, loreStart);
		meta.getPersistentDataContainer().set(PDCKeys.loreLen(), PersistentDataType.INTEGER, loreLen);
		meta.getPersistentDataContainer().set(PDCKeys.statsLore(), PersistentDataType.INTEGER, statsLore ? 1 : 0);
	}
}
