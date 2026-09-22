package net.tfminecraft.advancedcrafting.managers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.tlibs.event.MMOItemRebuildEvent;
import net.tfminecraft.advancedcrafting.utils.CraftTierLore;
import net.tfminecraft.advancedcrafting.utils.PDCKeys;

public class MMOItemRebuildListener implements Listener {

	@EventHandler(priority = EventPriority.NORMAL)
	public void onRebuild(MMOItemRebuildEvent event) {
		ItemStack result = event.getNewItem();
		Integer tier = readMajorityTier(result);
		if (tier == null) {
			tier = readMajorityTier(event.getOldItem());
		}
		if (tier == null || tier <= 0) {
			return;
		}

		Integer loreIndex = readTierLoreStart(result);
		if (loreIndex == null) {
			loreIndex = readTierLoreStart(event.getOldItem());
		}
		if (loreIndex != null) {
			CraftTierLore.refreshTierLine(result, tier);
		} else {
			CraftTierLore.applyTierLine(result, tier);
		}
		event.setNewItem(result);
	}

	private Integer readMajorityTier(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		return meta.getPersistentDataContainer().get(PDCKeys.craftMajorityTier(), PersistentDataType.INTEGER);
	}

	private Integer readTierLoreStart(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		return meta.getPersistentDataContainer().get(PDCKeys.craftTierLoreStart(), PersistentDataType.INTEGER);
	}
}
