package net.tfminecraft.advancedcrafting.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.advancedcrafting.objects.CraftStack;

/**
 * A crafted piece keeps the required-class list MMOItems stamped on it when it was made, and every refresh
 * rebuilds it from its own data. This makes the list follow the item's current MMOItems template, so a class
 * added to or removed from a weapon or armour template reaches pieces that already exist. Only the class stat
 * changes; the rebuild keeps everything else the same way a stat refresh does.
 */
public final class RequiredClassSync {
	private static final String TAG = "MMOITEMS_REQUIRED_CLASS";
	private static final String HISTORY_TAG = "HSTRY_REQUIRED_CLASS";

	private RequiredClassSync() {
	}

	/** The same item when its classes already match its template or it has no template, otherwise a rebuilt copy. */
	public static ItemStack sync(ItemStack item) {
		if (item == null || item.getType().isAir() || !new CraftStack(item).isCrafted()) {
			return item;
		}
		NBTItem nbt = NBTItem.get(item);
		if (!nbt.hasType()) {
			return item;
		}
		MMOItemTemplate template = MMOItems.plugin.getTemplates().getTemplate(nbt);
		if (template == null) {
			return item;
		}
		List<String> wanted = new ArrayList<>(classSet(listOf(template.getBaseItemData().get(ItemStats.REQUIRED_CLASS))));
		// The cheap check covers almost every item; only a stat history needs the full MMOItems read.
		if (!nbt.hasTag(HISTORY_TAG) && classSet(Arrays.asList(nbt.getString(TAG).split(","))).equals(Set.copyOf(wanted))) {
			return item;
		}
		LiveMMOItem mmo = new LiveMMOItem(nbt);
		List<String> before = update(mmo, wanted);
		if (before == null) {
			return item;
		}
		RefreshKeeper.keepWear(nbt, mmo);
		ItemStack rebuilt = RefreshKeeper.keepAppearance(item, mmo.newBuilder().build());
		rebuilt.setAmount(item.getAmount());
		Integer tier = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.craftMajorityTier(),
				PersistentDataType.INTEGER);
		if (tier != null) {
			CraftTierLore.applyTierLine(rebuilt, tier);
		}
		Bukkit.getLogger().info("[AC][ClassSync] " + nbt.getType() + "." + nbt.getString("MMOITEMS_ITEM_ID") + ": "
				+ before + " -> " + wanted);
		return rebuilt;
	}

	/**
	 * Gives the item the wanted classes, in that order, and touches no other stat. With a stat history only its
	 * original layer changes, so gem and external layers stay. Returns the classes it had, or null when they
	 * already matched (as sets: order, spacing and repeats do not count).
	 */
	public static List<String> update(MMOItem mmo, List<String> wanted) {
		StatHistory history = mmo.getStatHistory(ItemStats.REQUIRED_CLASS);
		List<String> before = listOf(history != null ? history.getOriginalData() : mmo.getData(ItemStats.REQUIRED_CLASS));
		if (classSet(before).equals(classSet(wanted))) {
			return null;
		}
		StringListData classes = new StringListData(wanted);
		if (history != null) {
			history.setOriginalData(classes.clone());
		}
		if (wanted.isEmpty()) {
			mmo.removeData(ItemStats.REQUIRED_CLASS);
		} else {
			mmo.setData(ItemStats.REQUIRED_CLASS, classes);
		}
		return before;
	}

	/** Trimmed names without blanks or repeats, in their first order. */
	public static Set<String> classSet(Collection<String> names) {
		Set<String> result = new LinkedHashSet<>();
		for (String name : names) {
			String trimmed = name.trim();
			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}
		return result;
	}

	private static List<String> listOf(Object data) {
		return data instanceof StringListData list ? list.getList() : List.of();
	}
}
