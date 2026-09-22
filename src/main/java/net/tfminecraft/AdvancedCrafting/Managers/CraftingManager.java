package net.tfminecraft.AdvancedCrafting.Managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import io.lumine.mythic.lib.api.item.NBTItem;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.BlockAPI;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import net.tfminecraft.AdvancedCrafting.AdvancedCrafting;
import net.tfminecraft.AdvancedCrafting.Cache.Cache;
import net.tfminecraft.AdvancedCrafting.Enums.StationFeedback;
import net.tfminecraft.AdvancedCrafting.Loaders.CategoryLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.HitLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.RecipeLoader;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.CraftingRecipe;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.CraftingStation;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.RecipeCategory;
import net.tfminecraft.AdvancedCrafting.Utils.ProfessionPermissions;

public class CraftingManager implements Listener{
	private static class AdminCraftPending {
		private final double qualityPercent;
		private final long expiresAtMs;

		private AdminCraftPending(double qualityPercent, long expiresAtMs) {
			this.qualityPercent = qualityPercent;
			this.expiresAtMs = expiresAtMs;
		}
	}

	private HashMap<Player, Long> cooldown = new HashMap<>();
	private HashMap<Player, CraftingStation> currentStation = new HashMap<>();
	private HashMap<Location, CraftingStation> stations = new HashMap<>();
	private final Map<UUID, AdminCraftPending> adminCraftPending = new HashMap<>();

	private ItemAPI api = TLibs.getItemAPI();

	private boolean isCraftingStation(Block b) {
		if (b == null || Cache.craftingStation == null) {
			return false;
		}
		BlockAPI blockApi = (BlockAPI) TLibs.getApiInstance(APIType.BLOCK_API);
		return blockApi.getChecker().checkBlock(b, Cache.craftingStation);
	}

	private boolean isIaFurnitureStationConfig() {
		return Cache.craftingStation != null
				&& Cache.craftingStation.trim().toLowerCase().startsWith("iaf(");
	}

	private String getConfiguredIaFurnitureId() {
		if (Cache.craftingStation == null) {
			return null;
		}
		String trimmed = Cache.craftingStation.trim();
		int open = trimmed.indexOf('(');
		int close = trimmed.indexOf(')', open + 1);
		if (open < 0 || close <= open) {
			return null;
		}
		return trimmed.substring(open + 1, close);
	}

	private boolean matchesConfiguredIaFurniture(String namespacedId) {
		if (namespacedId == null) {
			return false;
		}
		String configured = getConfiguredIaFurnitureId();
		return configured != null && configured.equalsIgnoreCase(namespacedId);
	}

	private boolean isStationTool(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR)) {
			return false;
		}
		if (Cache.brandingTool != null && api.getChecker().checkItemWithPath(item, Cache.brandingTool)) {
			return true;
		}
		NBTItem nbt = NBTItem.get(item);
		if (!nbt.hasType()) {
			return false;
		}
		return HitLoader.getByTool(nbt.getType() + "." + nbt.getString("MMOITEMS_ITEM_ID")) != null;
	}
	
	public boolean hasStation(Location loc) {
		if(stations.containsKey(loc)) return true;
		return false;
	}
	
	public CraftingStation get(Location loc) {
		if(stations.containsKey(loc)) return stations.get(loc);
		return null;
	}
	public void set(HashMap<Location, CraftingStation> map) {
		stations = map;
	}
	public List<CraftingStation> getStations() {
		List<CraftingStation> list = new ArrayList<>();
		for(Location l : stations.keySet()) {
			list.add(stations.get(l));
		}
		return list;
	}

	public void setAdminCraftPending(Player player, double qualityPercent) {
		long expiresAt = System.currentTimeMillis() + 30_000L;
		adminCraftPending.put(player.getUniqueId(), new AdminCraftPending(qualityPercent, expiresAt));
		new BukkitRunnable() {
			@Override
			public void run() {
				AdminCraftPending pending = adminCraftPending.remove(player.getUniqueId());
				if (pending != null && player.isOnline()) {
					player.sendMessage("§cAdmin craft timed out. Run §f/ac craft <percent>§c again.");
				}
			}
		}.runTaskLater(AdvancedCrafting.plugin, 600L);
	}

	private AdminCraftPending getValidAdminCraftPending(Player player) {
		AdminCraftPending pending = adminCraftPending.get(player.getUniqueId());
		if (pending == null) {
			return null;
		}
		if (System.currentTimeMillis() > pending.expiresAtMs) {
			adminCraftPending.remove(player.getUniqueId());
			return null;
		}
		return pending;
	}

	private boolean tryCompleteAdminCraft(Player p, Block b) {
		AdminCraftPending pending = getValidAdminCraftPending(p);
		if (pending == null) {
			return false;
		}
		if (!hasStation(b.getLocation())) {
			p.sendMessage("§cNo recipe on this station. Set up a craft first.");
			return true;
		}
		CraftingStation station = get(b.getLocation());
		if (!station.hasRecipe()) {
			p.sendMessage("§cNo recipe on this station. Select a recipe first.");
			return true;
		}
		if (!station.hasAllMaterials(p)) {
			return true;
		}
		StationFeedback f = station.craft(p, pending.qualityPercent);
		adminCraftPending.remove(p.getUniqueId());
		if (f.equals(StationFeedback.SUCCESS)) {
			p.getWorld().playSound(station.getLoc(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			p.getWorld().playSound(station.getLoc(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
			p.spawnParticle(Particle.LAVA, station.getLoc().clone().add(0.5, 1, 0.5), 50, 0.1, 0.2, 0.1);
			stations.remove(b.getLocation());
			currentStation.remove(p);
		} else {
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			if (f.equals(StationFeedback.LACKING_ITEMS)) {
				p.sendMessage("§cYou have to add all the items before smithing");
			}
		}
		return true;
	}

	@EventHandler
	public void openStation(PlayerInteractEvent e) {
		if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
		Block b = e.getClickedBlock();
		if(!isCraftingStation(b)) return;
		e.setCancelled(true);
		Player p = e.getPlayer();
		if(cooldown.containsKey(p)) {
			if(cooldown.get(p) > System.currentTimeMillis()) {
				return;
			}
		}
		cooldown.put(p, System.currentTimeMillis() + (100));
		if (tryCompleteAdminCraft(p, b)) {
			return;
		}
		ItemStack i = p.getInventory().getItemInMainHand();
		if(hasStation(b.getLocation())) {
			CraftingStation station = stations.get(b.getLocation());
			if(!station.hasRecipe()) {
				currentStation.put(p, station);
				InventoryManager inv = new InventoryManager();
				inv.categoryView(p);
				return;
			}
			if(Cache.brandingTool != null) {
				if(i == null) return;
				if(i.getType().equals(Material.AIR)) return;
				if(api.getChecker().checkItemWithPath(i, Cache.brandingTool) && !station.hasRecipe()) {
					currentStation.put(p, station);
					InventoryManager inv = new InventoryManager();
					inv.categoryView(p);
					return;
				}
			}
			if(i == null) return;
			if(i.getType().equals(Material.AIR)) return;
			StationFeedback f = station.addMaterial(p, i);
			switch (f) {
				case NOT_INGREDIENT:
					p.sendMessage("§cThis item cannot be used for crafting");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					break;
				case WRONG_TYPE:
					p.sendMessage("§cThis item type is not needed for the recipe");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					break;
				case CAPACITY:
					p.sendMessage("§cYou already have the needed amount of this type");
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
					break;
				case NO_PERMS:
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					break;
				default:
					p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 2f);
					p.spawnParticle(org.bukkit.Particle.ENCHANTED_HIT, station.getLoc().clone().add(0.5, 1, 0.5), 10, 0.01, 0.01, 0.01);
					break;
			}
			return;
		}
		CraftingStation station = new CraftingStation(b.getLocation());
		stations.put(b.getLocation(), station);
		currentStation.put(p, station);
		InventoryManager inv = new InventoryManager();
		inv.categoryView(p);
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onIaFurnitureBreak(FurnitureBreakEvent e) {
		if (!isIaFurnitureStationConfig()) {
			return;
		}
		if (!matchesConfiguredIaFurniture(e.getNamespacedID())) {
			return;
		}
		Player breaker = e.getPlayer();
		if (breaker != null && isStationTool(breaker.getInventory().getItemInMainHand())) {
			e.setCancelled(true);
			return;
		}
		if (e.getBukkitEntity() == null) {
			return;
		}
		Location loc = e.getBukkitEntity().getLocation().getBlock().getLocation();
		discardBrokenStation(loc);
	}

	private void discardBrokenStation(Location loc) {
		if (!hasStation(loc)) {
			return;
		}
		CraftingStation station = get(loc);
		World world = loc.getWorld();
		if (world != null) {
			world.playSound(loc, Sound.ENTITY_ARMOR_STAND_BREAK, 1f, 1f);
			world.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0.5, 1, 0.5), 30, 0.3, 0.3, 0.3);
		}
		station.drop();
		stations.remove(loc);
		currentStation.entrySet().removeIf(entry -> {
			CraftingStation open = entry.getValue();
			if (open != station && (open.getLoc() == null || !loc.equals(open.getLoc()))) {
				return false;
			}
			Player viewer = entry.getKey();
			String title = viewer.getOpenInventory().getTitle();
			if (title.equalsIgnoreCase("§7Select Category") || title.equalsIgnoreCase("§7Select Recipe")) {
				viewer.closeInventory();
			}
			return true;
		});
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void applyHit(PlayerInteractEvent e) {
		if (!e.getAction().equals(Action.LEFT_CLICK_BLOCK)) return;
		Block b = e.getClickedBlock();
		if (!isCraftingStation(b)) return;
		Player p = e.getPlayer();
		ItemStack i = p.getInventory().getItemInMainHand();
		if (isStationTool(i)) {
			e.setCancelled(true);
		}
		if (!hasStation(b.getLocation())) return;
		if (i == null || i.getType().equals(Material.AIR)) return;

		CraftingStation station = get(b.getLocation());

		if (Cache.brandingTool != null && api.getChecker().checkItemWithPath(i, Cache.brandingTool)) {
			if (p.isSneaking()) {
				station.cancel();
				p.sendMessage("§cProject cancelled");
				stations.remove(station.getLoc());
				p.getWorld().playSound(station.getLoc(), Sound.BLOCK_ANVIL_PLACE, 1f, 0.5f);
				p.spawnParticle(
					Particle.BLOCK,
					station.getLoc().clone().add(0.5, 1, 0.5),
					20,  // amount
					0.1, 0.2, 0.1,  // spread X,Y,Z
					Bukkit.createBlockData(Material.IRON_BLOCK)
				);
				return;
			} else {
				StationFeedback f = station.craft(p);
				if (f.equals(StationFeedback.SUCCESS)) {
					p.getWorld().playSound(station.getLoc(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
					p.getWorld().playSound(station.getLoc(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
					p.spawnParticle(org.bukkit.Particle.LAVA, station.getLoc().clone().add(0.5, 1, 0.5), 50, 0.1, 0.2, 0.1);
					stations.remove(b.getLocation());
					currentStation.remove(p);
				} else {
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					p.sendMessage("§c" + (f.equals(StationFeedback.LACKING_HITS) ? "You need to complete all the hits before finishing" :
							f.equals(StationFeedback.LACKING_ITEMS) ? "You have to add all the items before smithing" : ""));
				}
				return;
			}
		}

		StationFeedback f = station.hit(p, i);
		switch (f) {
			case LACKING_ITEMS:
				p.sendMessage("§cYou have to add all the items before smithing");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			case WRONG_TYPE:
				p.sendMessage("§cThis item cannot be used for crafting hits");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			case NONE:
				p.sendMessage("§cThis tool is not needed for this craft");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			case CAPACITY:
				p.sendMessage("§cYou dont need more hits with this tool");
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
				break;
			default:
				// Successful hit
				p.getWorld().playSound(station.getLoc(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
				p.spawnParticle(
					Particle.BLOCK,
					station.getLoc().clone().add(0.5, 1, 0.5),
					20,  // amount
					0.1, 0.2, 0.1,  // spread X,Y,Z
					Bukkit.createBlockData(Material.IRON_BLOCK)
				);
				break;
		}
	}

	
	@EventHandler
	public void invenClick(InventoryClickEvent e) {
		Player p = (Player) e.getWhoClicked();
		if(e.getView().getTitle().equalsIgnoreCase(InventoryManager.STAT_PREVIEW_TITLE)) {
			e.setCancelled(true);
			return;
		}
		if(e.getView().getTitle().equalsIgnoreCase("§7Select Category")) {
			e.setCancelled(true);
			ItemStack i = e.getCurrentItem();
			if(i == null) return;
			ItemMeta m = i.getItemMeta();
			NamespacedKey key = new NamespacedKey(AdvancedCrafting.plugin, "ac_category");
			if(m.getPersistentDataContainer().get(key, PersistentDataType.STRING) == null) return;
			RecipeCategory c = CategoryLoader.getByString(m.getPersistentDataContainer().get(key, PersistentDataType.STRING));
			InventoryManager inv = new InventoryManager();
			inv.recipeView(p, c);
			return;
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Select Recipe")) {
			e.setCancelled(true);
			ItemStack i = e.getCurrentItem();
			if(i == null) return;
			ItemMeta m = i.getItemMeta();
			NamespacedKey key = new NamespacedKey(AdvancedCrafting.plugin, "ac_recipe");
			if(m.getPersistentDataContainer().get(key, PersistentDataType.STRING) == null) return;
			CraftingRecipe recipe = RecipeLoader.getByString(m.getPersistentDataContainer().get(key, PersistentDataType.STRING));
			if (recipe.hasPermissionNamespace()
					&& !ProfessionPermissions.hasAnyNamespacePerm(p, recipe.getPermissionNamespace())) {
				p.sendMessage(ProfessionPermissions.missingNamespaceMessage(recipe.getPermissionNamespace()));
				return;
			}
			CraftingStation station = currentStation.get(p);
			p.closeInventory();
			if(station.hasRecipe()) {
				p.sendMessage("§cStation already has a recipe selected");
				return;
			}
			station.setRecipe(recipe);
			p.sendMessage("§aRecipe "+recipe.getCleanedName()+ " §aselected!");
			p.getWorld().playSound(station.getLoc(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			return;
		}
		
	}
	
	@EventHandler(priority = EventPriority.HIGH)
	public void breakStation(BlockBreakEvent e) {
		Block b = e.getBlock();
		Player p = e.getPlayer();
		if (p != null
				&& isCraftingStation(b)
				&& isStationTool(p.getInventory().getItemInMainHand())) {
			e.setCancelled(true);
			return;
		}
		if(!hasStation(b.getLocation())) return;
		if(p != null) {
			p.getWorld().playSound(b.getLocation(), Sound.ENTITY_ARMOR_STAND_BREAK, 1f, 1f);
			p.getWorld().spawnParticle(Particle.LARGE_SMOKE, b.getLocation().add(0.5, 1, 0.5), 30, 0.3, 0.3, 0.3);
		}
		CraftingStation station = get(b.getLocation());
		station.drop();
		stations.remove(b.getLocation());
	}
}
