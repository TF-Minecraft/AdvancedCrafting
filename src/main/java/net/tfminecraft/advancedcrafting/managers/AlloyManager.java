package net.tfminecraft.advancedcrafting.managers;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.database.AlloyDatabase;
import net.tfminecraft.advancedcrafting.enums.StationFeedback;
import net.tfminecraft.advancedcrafting.objects.CraftStack;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.alloys.AlloyForger;
import net.tfminecraft.advancedcrafting.objects.alloys.AlloyStation;
import net.tfminecraft.advancedcrafting.objects.alloys.NamableAlloy;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.utils.ProfessionPermissions;
import net.tfminecraft.advancedcrafting.lifecycle.CraftLifecycle;

public class AlloyManager implements Listener{
	
	private HashMap<Location, AlloyStation> stations = new HashMap<>();
	private HashMap<Player, Long> cooldown = new HashMap<>();
	private static HashMap<String, Alloy> alloys = new HashMap<>();

	private HashMap<Player, NamableAlloy> naming = new HashMap<>();
	
	public static Alloy getAlloyById(String s) {
		if (s == null) return null;
		return alloys.get(s.toLowerCase());
	}

	public static java.util.Set<String> getAlloyIds() {
		return java.util.Collections.unmodifiableSet(alloys.keySet());
	}
	public static void removeAlloy(String id) {
		if(alloys.containsKey(id)) alloys.remove(id);
	}
	public static void addAlloy(Alloy a) {
		alloys.put(a.getId(), a);
	}
	public boolean hasStation(Location loc) {
		return stations.containsKey(loc);
	}
	public AlloyStation get(Location loc) {
		if(!stations.containsKey(loc)) return null;
		return stations.get(loc);
	}
	public void removeStation(AlloyStation station) {
		stations.remove(station.getLocation());
	}
	
	public boolean isAlloyStation(Block b) {
		String path = Cache.alloyStation;
		BlockAPI api = TLibs.getBlockAPI();
		return api.getChecker().checkBlock(b, path);
	}

	public boolean isValidAlloyStation(Block b) {
		if (!isAlloyStation(b)) {
			return false;
		}
		return !isAlloyStation(b.getRelative(BlockFace.DOWN));
	}

	public void start() {
		tickCycle();
	}

	public void tickCycle() {
		new BukkitRunnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				for(Map.Entry<Player, NamableAlloy> entry : ((HashMap<Player, NamableAlloy>) naming.clone()).entrySet()) {
					if(entry.getValue().tick()) {
						entry.getKey().sendMessage("§cNaming timed out.");
						naming.remove(entry.getKey());
					}
				}
			}
		}.runTaskTimer(AdvancedCrafting.plugin, 0, 20L);
	}
	
	@EventHandler
	public void addIngredient(PlayerInteractEvent e) {
		if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
		Block b = e.getClickedBlock();
		if (!isAlloyStation(b)) {
			return;
		}
		Player p = e.getPlayer();
		if (!isValidAlloyStation(b)) {
			return;
		}
		if(cooldown.containsKey(p)) {
			if(cooldown.get(p) > System.currentTimeMillis()) {
				return;
			}
		}
		cooldown.put(p, System.currentTimeMillis() + (100));
		if(hasLava(p)) {
			forgeAlloy(e);
			return;
		}
		ItemStack i = p.getInventory().getItemInMainHand();
		CraftStack cs = new CraftStack(i);
		if(!cs.isIngredient()) {
			p.sendMessage("§cThis item is not an ingredient");
			return;
		}
		Ingredient ing = cs.getIngredient();
		if (!ProfessionPermissions.canUseIngredient(p, ing)) {
			p.sendMessage(ProfessionPermissions.missingIngredientPermissionMessage(
					ing.getIngredientData().getPermission()));
			return;
		}
		AlloyStation station = null;
		if(hasStation(b.getLocation())) {
			station = get(b.getLocation());
		} else {
			station = new AlloyStation(b.getLocation());
			stations.put(b.getLocation(), station);
		}
		StationFeedback fb = station.addIngredient(ing);
		if(fb.equals(StationFeedback.EXISTS)) {
			p.sendMessage("§cThis ingredient is already part of the recipe");
			return;
		} else if(fb.equals(StationFeedback.CAPACITY)) {
			p.sendMessage("§cThe alloy forge cannot fit any more ingredients");
			return;
		} else if(fb.equals(StationFeedback.WRONG_BASE)) {
			p.sendMessage("§cThis ingredient cannot be used as the base");
			return;
		} else if(fb.equals(StationFeedback.INCOMPATIBLE_TYPE)) {
			p.sendMessage("§cA §f"+station.getBaseItem().getIngredientData().getType().getName()+" §cbase cannot be mixed with a §f"+ing.getIngredientData().getType().getName()+" §ccatalyst");
			return;
		}
		p.getWorld().playSound(station.getLocation(), Sound.BLOCK_ANVIL_HIT, 1f, 1f);
		p.sendTitle("§aAdded "+i.getItemMeta().getDisplayName(), station.getStatus(), 5, 30, 5);
		i.setAmount(i.getAmount()-1);
	}
	public void forgeAlloy(PlayerInteractEvent e) {
		Block b = e.getClickedBlock();
		Player p = e.getPlayer();
		e.setCancelled(true);
		if(!hasStation(b.getLocation())) return;
		AlloyStation station = get(b.getLocation());
		if(station.getIngredients().size() < 2) {
			p.sendMessage("§cYou need at least 2 ingredients to make an alloy");
			return;
		}
		for (Ingredient ingredient : station.getIngredients()) {
			if (!ProfessionPermissions.canUseIngredient(p, ingredient)) {
				p.sendMessage(ProfessionPermissions.missingIngredientPermissionMessage(
						ingredient.getIngredientData().getPermission()));
				return;
			}
		}
		AlloyForger forger = new AlloyForger(station);
		NamableAlloy alloy = forger.forge(p);
		p.getWorld().playSound(station.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
		p.getInventory().getItemInMainHand().setType(Material.BUCKET);
		removeStation(station);
		if(alloy != null) {
			p.sendTitle(StringFormatter.formatHex("#d1743fNew Alloy"), StringFormatter.formatHex("#b0a996Use #36e3a4/alloy name #b0a996to name it!"), 10, 80, 10);
			p.sendMessage("§cThe naming prompt times out in 60 seconds.");
			naming.put(p, alloy);
		}
	}

	public void nameAlloy(Player p, String s) {
		if(!naming.containsKey(p)) {
			p.sendMessage("§cYou have no alloy to name");
			return;
		}
		if (!s.matches("[a-zA-Z_]+")) {
			p.sendMessage("§cName can only contain letters (A–Z) and underscores (_).");
			return;
		}
		String name = StringFormatter.formatHex(new String(s).replace("_", " "));
		NamableAlloy alloy = naming.get(p);
		String oldId = alloy.getAlloy().getId();
		removeAlloy(oldId);
		String id = StringFormatter.clean(s);
		alloy.getAlloy().setId(id);
		alloy.getAlloy().setName(name);
		ItemStack i = alloy.getItem();
		ItemStack newItem = alloy.getAlloy().build();
		for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
			ItemStack current = p.getInventory().getItem(slot);
			if (current != null && current.equals(i)) {
				p.getInventory().setItem(slot, newItem);
				break;
			}
		}
		i.setType(newItem.getType());
		i.setItemMeta(newItem.getItemMeta());
		AlloyDatabase db = new AlloyDatabase();
		p.sendMessage(alloy.getAlloy().getId());
		db.editAlloy(alloy.getAlloy(), oldId);
		p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
		p.sendMessage("§aNamed the new alloy "+name);
		addAlloy(alloy.getAlloy());
		CraftLifecycle.fireAlloyOutcome(p, alloy.getAlloy().getId());
		naming.remove(p);
	}
	
	@EventHandler
	public void breakStation(BlockBreakEvent e) {
		Block b = e.getBlock();
		if (!isValidAlloyStation(b)) {
			return;
		}
		if(!hasStation(b.getLocation())) return;
		AlloyStation station = get(b.getLocation());
		station.drop();
		stations.remove(b.getLocation());
	}
	
	private boolean hasLava(Player p) {
		ItemStack i = p.getInventory().getItemInMainHand();
		if(!i.getType().equals(Material.LAVA_BUCKET)) return false;
		if(NBTItem.get(i).hasType()) return false;
		if(CustomStack.byItemStack(i) != null) return false;
		return true;
	}
}
