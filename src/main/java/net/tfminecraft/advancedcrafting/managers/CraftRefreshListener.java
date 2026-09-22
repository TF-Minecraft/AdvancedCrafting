package net.tfminecraft.advancedcrafting.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.tlibs.armour.ArmorEquipEvent;
import net.tfminecraft.tlibs.armour.ArmorType;
import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.utils.AcItemRefresher;

public class CraftRefreshListener implements Listener {

	public void start(JavaPlugin plugin) {
	}

	public void stop() {
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event) {
		Bukkit.getScheduler().runTask(AdvancedCrafting.plugin, () -> {
			ItemStack dropped = event.getItemDrop().getItemStack();
			if (!AcItemRefresher.isManaged(dropped)) {
				return;
			}
			ItemStack refreshed = AcItemRefresher.refreshIfOutdated(dropped);
			if (refreshed == dropped) {
				return;
			}
			event.getItemDrop().setItemStack(refreshed);
		});
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		Inventory clicked = event.getClickedInventory();
		int slot = event.getSlot();
		Bukkit.getScheduler().runTask(AdvancedCrafting.plugin, () -> {
			if (clicked != null) {
				tryRefresh(clicked.getItem(slot), item -> clicked.setItem(slot, item));
			}
			tryRefresh(player.getItemOnCursor(), player::setItemOnCursor);
		});
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHotbarSelect(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		int slot = event.getNewSlot();
		Bukkit.getScheduler().runTask(AdvancedCrafting.plugin, () -> {
			ItemStack held = player.getInventory().getItem(slot);
			tryRefresh(held, item -> player.getInventory().setItem(slot, item));
		});
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onArmorEquip(ArmorEquipEvent event) {
		if (event.getNewArmorPiece() == null) {
			return;
		}
		Player player = event.getPlayer();
		ArmorType type = event.getType();
		Bukkit.getScheduler().runTask(AdvancedCrafting.plugin, () -> {
			ItemStack piece = getArmorPiece(player, type);
			tryRefresh(piece, item -> setArmorPiece(player, type, item));
		});
	}

	private void tryRefresh(ItemStack item, ItemConsumer writer) {
		if (item == null || item.getType().isAir() || !AcItemRefresher.isManaged(item)) {
			return;
		}
		ItemStack refreshed = AcItemRefresher.refreshIfOutdated(item);
		if (refreshed == item) {
			return;
		}
		writer.accept(refreshed);
	}

	private ItemStack getArmorPiece(Player player, ArmorType type) {
		return switch (type) {
			case HELMET -> player.getInventory().getHelmet();
			case CHESTPLATE -> player.getInventory().getChestplate();
			case LEGGINGS -> player.getInventory().getLeggings();
			case BOOTS -> player.getInventory().getBoots();
		};
	}

	private void setArmorPiece(Player player, ArmorType type, ItemStack item) {
		switch (type) {
			case HELMET -> player.getInventory().setHelmet(item);
			case CHESTPLATE -> player.getInventory().setChestplate(item);
			case LEGGINGS -> player.getInventory().setLeggings(item);
			case BOOTS -> player.getInventory().setBoots(item);
			default -> {
			}
		}
	}

	@FunctionalInterface
	private interface ItemConsumer {
		void accept(ItemStack item);
	}
}
