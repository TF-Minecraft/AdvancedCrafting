package net.tfminecraft.advancedcrafting.managers;

import java.util.HashMap;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.enums.APIType;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.CraftStack;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;

public class IngredientManager implements Listener{
	private HashMap<String, Ingredient> ingredients = new HashMap<>();
	
	public void set(HashMap<String, Ingredient> map) {
		ingredients = map;
	}
	
	public Ingredient get(String path) {
		if(!ingredients.containsKey(path)) return null;
		return ingredients.get(path);
	}

	public boolean isIngredientStation(Block b) {
		String path = Cache.ingredientStation;
		BlockAPI api = (BlockAPI) TLibs.getApiInstance(APIType.BLOCK_API);
		return api.getChecker().checkBlock(b, path);
	}
	
	@EventHandler
	public void convertItem(PlayerInteractEvent e) {
		if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
		Block b = e.getClickedBlock();
		if(!isIngredientStation(b)) return;
		Player p = e.getPlayer();
		ItemStack i = p.getInventory().getItemInMainHand();
		if(i == null || i.getType().isAir()) return;

		CraftStack cs = new CraftStack(i);
		StatData source = resolveStatData(cs, i);
		if(source == null) return;

		e.setCancelled(true);

		if(!cs.isIngredient() && !cs.isAlloy()) {
			Ingredient ing = getFromItem(i);
			if(ing == null) return;
			ing.buildTo(i);
		}

		InventoryManager inv = new InventoryManager();
		inv.templatePreviewView(p, source);
	}

	private StatData resolveStatData(CraftStack cs, ItemStack item) {
		if(cs.isIngredient()) {
			Ingredient ing = cs.getIngredient();
			if(ing != null) {
				return ing.getIngredientData().getStatData();
			}
		}
		if(cs.isAlloy()) {
			Alloy alloy = cs.getAlloy();
			if(alloy != null) {
				return alloy.getData().getStatData();
			}
		}
		Ingredient ing = getFromItem(item);
		if(ing != null) {
			return ing.getIngredientData().getStatData();
		}
		return null;
	}
	
	public Ingredient getFromItem(ItemStack i) {
		String path = "";
		NBTItem nbt = NBTItem.get(i);
		if(nbt.hasType()) {
			path = "m."+nbt.getType().toLowerCase()+"."+nbt.getString("MMOITEMS_ITEM_ID").toLowerCase();
			return get(path);
		}
		if(CustomStack.byItemStack(i) != null) {
			CustomStack c = CustomStack.byItemStack(i);
			return get("ia."+c.getNamespacedID());
		}
		return get("v."+i.getType().toString().toLowerCase());
	}
}
