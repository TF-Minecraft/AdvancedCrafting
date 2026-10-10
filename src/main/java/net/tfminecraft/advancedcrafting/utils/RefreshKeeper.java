package net.tfminecraft.advancedcrafting.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.tfminecraft.advancedcrafting.util.LegacyModelData;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;

/**
 * A stat refresh rebuilds the item from its MMOItems data, which only covers stats. This puts back what the
 * player's item had besides those: its wear, skin or model, display name (ArmourShop names) and other
 * plugins' data such as gem socket rarities.
 */
public final class RefreshKeeper {
	private static final String DURABILITY = "MMOITEMS_DURABILITY";
	private static final String MAX_DURABILITY = "MMOITEMS_MAX_DURABILITY";

	private RefreshKeeper() {
	}

	/**
	 * Before the rebuild: keep the damage the item has taken, so a refresh neither repairs nor breaks it.
	 * MMOItems builds a full item unless its current durability is set.
	 */
	public static void keepWear(NBTItem old, MMOItem mmo) {
		if (!old.hasTag(DURABILITY) || !old.hasTag(MAX_DURABILITY) || !mmo.hasData(ItemStats.MAX_DURABILITY)) {
			return;
		}
		int current = old.getInteger(DURABILITY);
		int taken = old.getInteger(MAX_DURABILITY) - current;
		int max = (int) ((DoubleData) mmo.getData(ItemStats.MAX_DURABILITY)).getValue();
		int kept = Math.min(max, Math.max(current > 0 ? 1 : 0, max - taken));
		mmo.setData(ItemStats.CUSTOM_DURABILITY, new DoubleData(kept));
	}

	/** After the rebuild: the old look, name and foreign data on the rebuilt item. */
	public static ItemStack keepAppearance(ItemStack old, ItemStack rebuilt) {
		ItemStack kept = ItemSkinPreserver.apply(old, rebuilt);
		ItemMeta oldMeta = old.getItemMeta();
		if (kept.getType() != old.getType()) {
			kept.setType(old.getType());
		}
		ItemMeta meta = kept.getItemMeta();
		if (oldMeta == null || meta == null) {
			return kept;
		}
		if (LegacyModelData.has(oldMeta)) {
			LegacyModelData.set(meta, LegacyModelData.get(oldMeta));
		}
		if (oldMeta instanceof LeatherArmorMeta oldLeather && meta instanceof LeatherArmorMeta leather) {
			leather.setColor(oldLeather.getColor());
		}
		if (oldMeta.hasDisplayName()) {
			meta.displayName(oldMeta.displayName());
		}
		oldMeta.getPersistentDataContainer().copyTo(meta.getPersistentDataContainer(), false);
		kept.setItemMeta(meta);
		if (IaAutoUpdate.isExposed(kept)) {
			IaAutoUpdate.protect(kept);
		}
		return kept;
	}
}
