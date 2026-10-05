package net.tfminecraft.advancedcrafting.managers;

import org.bukkit.Bukkit;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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

	/** After a restart every player rejoins, so this brings their carried items up to date without anyone acting. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTask(AdvancedCrafting.plugin, () -> {
			if (!player.isOnline()) {
				return;
			}
			sweep(player.getInventory());
			sweep(player.getEnderChest());
		});
	}

	/** Chests, barrels and storage entities are checked when opened; plugin menus are left alone. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onInventoryOpen(InventoryOpenEvent event) {
		Inventory inventory = event.getInventory();
		if (!isWorldStorage(inventory.getHolder(false))) {
			return;
		}
		Bukkit.getScheduler().runTask(AdvancedCrafting.plugin, () -> sweep(inventory));
	}

	public static boolean isWorldStorage(InventoryHolder holder) {
		return holder instanceof BlockInventoryHolder || holder instanceof DoubleChest || holder instanceof Entity;
	}

	private void sweep(Inventory inventory) {
		ItemStack[] contents = inventory.getContents();
		for (int slot = 0; slot < contents.length; slot++) {
			int target = slot;
			tryRefresh(contents[slot], item -> inventory.setItem(target, item));
		}
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
		java.util.function.Consumer<ItemStack> writer = switch (type) {
			case HELMET -> player.getInventory()::setHelmet;
			case CHESTPLATE -> player.getInventory()::setChestplate;
			case LEGGINGS -> player.getInventory()::setLeggings;
			case BOOTS -> player.getInventory()::setBoots;
		};
		writer.accept(item);
	}

	@FunctionalInterface
	private interface ItemConsumer {
		void accept(ItemStack item);
	}
}
