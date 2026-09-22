package net.tfminecraft.AdvancedCrafting.Objects.Crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.lumine.mythic.lib.api.item.NBTItem;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import me.Plugins.TLibs.Objects.Utils.IntCounter;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.GemSocketsData;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.type.NameData;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.AdvancedCrafting.Cache.Cache;
import net.tfminecraft.AdvancedCrafting.Enums.StationFeedback;
import net.tfminecraft.AdvancedCrafting.Loaders.HitLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.IngredientLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.QualityLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.SocketGroupLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.TypeLoader;
import net.tfminecraft.AdvancedCrafting.Managers.AlloyManager;
import net.tfminecraft.AdvancedCrafting.Objects.CraftStack;
import net.tfminecraft.AdvancedCrafting.Objects.Alloys.Alloy;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.Hits.CraftingHit;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.Hits.HitType;
import net.tfminecraft.AdvancedCrafting.Objects.Data.CraftProvenance;
import net.tfminecraft.AdvancedCrafting.Objects.Data.StatData;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.IngredientType;
import net.tfminecraft.AdvancedCrafting.Objects.Schemes.ModelScheme;
import net.tfminecraft.AdvancedCrafting.Objects.Stats.StatModifier;
import net.tfminecraft.AdvancedCrafting.Utils.CraftStatCalculator;
import net.tfminecraft.AdvancedCrafting.Utils.CraftTierLore;
import net.tfminecraft.AdvancedCrafting.Utils.MMOStatApplicator;
import net.tfminecraft.AdvancedCrafting.Utils.MajorityTierResolver;
import net.tfminecraft.AdvancedCrafting.Utils.ProfessionPermissions;
import net.tfminecraft.AdvancedCrafting.lifecycle.CraftLifecycle;

public class CraftingStation {
	private Location loc;
	
	private ItemStack result;
	
	private CraftingRecipe recipe;
	private HashMap<String, Integer> currentMaterials = new HashMap<>();
	private HashMap<IngredientType, IntCounter> types = new HashMap<>();
	
	private HashMap<CraftingHit, IntCounter> hits = new HashMap<>();
	private HashMap<HitType, IntCounter> hitTypes = new HashMap<>();
	
	private StatData stats;
	
	public CraftingStation(Location loc) {
		this.loc = loc;
	}
	
	public CraftingStation(Location loc, CraftingRecipe recipe, HashMap<String, Integer> materials, HashMap<CraftingHit, Integer> hits) {
		this.loc = loc;
		this.recipe = recipe;
		this.currentMaterials = materials;
		calculateTypes();
		calculateHitTypes();
		for(CraftingHit h : hits.keySet()) {
			if(this.hits.containsKey(h)) {
				this.hits.get(h).increaseCurrent(hits.get(h));
			}
			if(this.hitTypes.containsKey(h.getType())) {
				this.hitTypes.get(h.getType()).increaseCurrent(hits.get(h));;
			}
		}
	}
	
	private void calculateTypes() {
		for(String s : recipe.getRecipe().keySet()) {
			IntCounter c = new IntCounter();
			c.setNeeded(recipe.getRecipe().get(s));
			types.put(TypeLoader.getIngredientTypeByString(s), c);
		}
		if(currentMaterials.keySet().size() > 0) {
			for(String s : currentMaterials.keySet()) {
				String st = s.split("\\.")[0];
				String sId = s.split("\\.")[1];
				if(st.equalsIgnoreCase("ingredient")) {
					Ingredient ing = IngredientLoader.getByString(sId);
					if(types.containsKey(ing.getIngredientData().getType())) {
						types.get(ing.getIngredientData().getType()).increaseCurrent(currentMaterials.get(s));
					}
				} else if(st.equalsIgnoreCase("alloy")) {
					Alloy a = AlloyManager.getAlloyById(sId);
					if(types.containsKey(a.getData().getType())) {
						types.get(a.getData().getType()).increaseCurrent(currentMaterials.get(s));
					}
				}
			}
		}
	}
	
	private void calculateHitTypes() {
		if(currentMaterials.keySet().size() > 0) {
			for(String s : currentMaterials.keySet()) {
				String st = s.split("\\.")[0];
				String sId = s.split("\\.")[1];
				HashMap<CraftingHit, Integer> hitMap = new HashMap<>();
				if(st.equalsIgnoreCase("ingredient")) {
					Ingredient ing = IngredientLoader.getByString(sId);
					hitMap = ing.getIngredientData().getHits();
				} else if(st.equalsIgnoreCase("alloy")) {
					Alloy a = AlloyManager.getAlloyById(sId);
					hitMap = a.getData().getHits();
				}
				for(CraftingHit h : hitMap.keySet()) {
					if(hits.containsKey(h)) {
						hits.get(h).increaseNeeded(hitMap.get(h)*currentMaterials.get(s));
					} else {
						IntCounter counter = new IntCounter();
						counter.setNeeded(hitMap.get(h)*currentMaterials.get(s));
						hits.put(h, counter);
					}
					if(hitTypes.containsKey(h.getType())) {
						hitTypes.get(h.getType()).increaseNeeded(hitMap.get(h)*currentMaterials.get(s));
					} else {
						IntCounter counter = new IntCounter();
						counter.setNeeded(hitMap.get(h)*currentMaterials.get(s));
						hitTypes.put(h.getType(), counter);
					}
				}
			}
		}
	}
	
	public boolean hasRecipe() {
		if(recipe == null) return false;
		return true;
	}

	public Location getLoc() {
		return loc;
	}

	public CraftingRecipe getRecipe() {
		return recipe;
	}
	public void setRecipe(CraftingRecipe recipe) {
		this.recipe = recipe;
		calculateTypes();
	}
	public HashMap<String, Integer> getCurrentMaterials() {
		return currentMaterials;
	}

	public HashMap<CraftingHit, IntCounter> getHits() {
		return hits;
	}

	public HashMap<IngredientType, IntCounter> getTypes() {
		return types;
	}

	public StationFeedback addMaterial(Player p, ItemStack i) {
		CraftStack c = new CraftStack(i);
		if(!c.isAlloy() && !c.isIngredient()) {
			return StationFeedback.NOT_INGREDIENT;
		}
		HashMap<CraftingHit, Integer> mergeHits = null;
		String key = "";
		IngredientType type = null;
		String name = i.getItemMeta().getDisplayName();
		int materialTier = 0;
		if(c.isIngredient()) {
			Ingredient ing = c.getIngredient();
			if (!ProfessionPermissions.canUseIngredient(p, ing)) {
				p.sendMessage(ProfessionPermissions.missingIngredientPermissionMessage(
						ing.getIngredientData().getPermission()));
				return StationFeedback.NO_PERMS;
			}
			key = "ingredient."+ing.getId();
			type = ing.getIngredientData().getType();
			mergeHits = ing.getIngredientData().getHits();
			materialTier = ProfessionPermissions.resolveTier(ing);
		}
		if(c.isAlloy()) {
			Alloy a = c.getAlloy();
			key = "alloy."+a.getId();
			type = a.getData().getType();
			mergeHits = a.getData().getHits();
			materialTier = ProfessionPermissions.resolveTier(a);
		}
		if (materialTier > 0 && recipe.hasPermissionNamespace()
				&& !ProfessionPermissions.hasExactTierPerm(p, recipe.getPermissionNamespace(), materialTier)) {
			p.sendMessage(ProfessionPermissions.missingExactTierMessage(recipe.getPermissionNamespace(), materialTier));
			return StationFeedback.NO_PERMS;
		}
		if(!recipe.getRecipe().containsKey(type.getId())) {
			return StationFeedback.WRONG_TYPE;
		}
		if(types.get(type).isEqual()) {
			return StationFeedback.CAPACITY;
		}
		if(currentMaterials.containsKey(key)) {
			currentMaterials.put(key, currentMaterials.get(key)+1);
		} else {
			currentMaterials.put(key, 1);
		}
		types.get(type).increaseCurrent(1);
		for(CraftingHit h : mergeHits.keySet()) {
			if(hits.containsKey(h)) {
				hits.get(h).increaseNeeded(mergeHits.get(h));
			} else {
				IntCounter counter = new IntCounter();
				counter.setNeeded(mergeHits.get(h));
				hits.put(h, counter);
			}
			if(hitTypes.containsKey(h.getType())) {
				hitTypes.get(h.getType()).increaseNeeded(mergeHits.get(h));
			} else {
				IntCounter counter = new IntCounter();
				counter.setNeeded(mergeHits.get(h));
				hitTypes.put(h.getType(), counter);
			}
		}
		//This is debug
		/*
		p.sendMessage("§e==========================");
		for(IngredientType t : types.keySet()) {
			p.sendMessage(t.getName()+": "+types.get(t).getCurrent()+"/"+types.get(t).getNeeded());
		}
		p.sendMessage("§e==========================");
		for(CraftingHit h : hits.keySet()) {
			p.sendMessage(h.getName()+": "+hits.get(h).getCurrent()+"/"+hits.get(h).getNeeded());
		}
		p.sendMessage("§e==========================");
		for(HitType h : hitTypes.keySet()) {
			p.sendMessage(h.getName()+" hits: "+hitTypes.get(h).getCurrent()+"/"+hitTypes.get(h).getNeeded());
		}
		p.sendMessage("§e==========================");
		*/
		p.sendTitle("§aAdded "+name, type.getName() + " §e"+types.get(type).getCurrent()+"/"+types.get(type).getNeeded(), 5, 20, 5);
		p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount()-1);
		return StationFeedback.SUCCESS;
	}
	
	public boolean hasAllMaterials(Player p) {
		return checkItems(p);
	}

	public StationFeedback craft(Player p) {
		return craft(p, null);
	}

	public StationFeedback craft(Player p, Double forcedQualityPercent) {
		stats = CraftStatCalculator.compute(recipe, currentMaterials);
		giveXP(p);
		return createItem(p, forcedQualityPercent);
	}

	private void giveXP(Player p) {
		// Map of skill name -> total XP to give
		Map<String, Double> xpBySkill = new HashMap<>();

		for (String s : currentMaterials.keySet()) {
			String[] split = s.split("\\.");
			String type = split[0];
			String mId = split[1];
			int amount = currentMaterials.get(s);

			double xpPerUnit = 0.0;
			String skill = null;

			if (type.equalsIgnoreCase("ingredient")) {
				Ingredient ingredient = IngredientLoader.getByString(mId); // Assuming you have a method like this
				if (ingredient != null && ingredient.getIngredientData().hasXP()) {
					String raw = ingredient.getIngredientData().getXP();
					xpPerUnit = Double.parseDouble(raw.split("\\(")[1].replace(")", ""));
					skill = raw.split("\\(")[0]; // Assuming you store "agriculturist" here
				}
			} else if (type.equalsIgnoreCase("alloy")) {
				Alloy alloy = AlloyManager.getAlloyById(mId); // Likewise for alloy
				if (alloy != null && alloy.getData().hasXP()) {
					String raw = alloy.getData().getXP();
					xpPerUnit = Double.parseDouble(raw.split("\\(")[1].replace(")", ""));
					skill = raw.split("\\(")[0]; // Assuming you store "agriculturist" here
				}
			}

			if (skill != null && xpPerUnit > 0) {
				double totalXP = xpPerUnit * amount;
				xpBySkill.put(skill, xpBySkill.getOrDefault(skill, 0.0) + totalXP);
			}
		}

		// Dispatch XP commands
		ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
		for (Map.Entry<String, Double> entry : xpBySkill.entrySet()) {
			String skill = entry.getKey();
			double xp = Math.round(entry.getValue() * 100) / 100.0; // round to 2 decimals
			String command = "mmocore admin exp give " + p.getName() + " " + skill + " " + xp;
			Bukkit.dispatchCommand(console, command);
		}
	}

	
	private boolean checkItems(Player p) {
		boolean complete = true;
		for(IngredientType t : types.keySet()) {
			IntCounter c = types.get(t);
			if(!c.isEqual()) {
				complete = false;
				p.sendMessage("§cYou only have "+c.getCurrent()+" out of "+c.getNeeded() + " " +t.getName()+ "§c items.");
			}
		}
		return complete;
	}
	
	private boolean checkHits(Player p) {
		boolean complete = true;
		for(HitType t : hitTypes.keySet()) {
			IntCounter c = hitTypes.get(t);
			if(!c.isEqual()) {
				complete = false;
				p.sendMessage("§cYou only have "+c.getCurrent()+" out of "+c.getNeeded() + " " +t.getName()+ "§c hits.");
			}
		}
		return complete;
	}
	
	private Quality getQuality(double d) {
		return QualityLoader.getByAmount(d);
	}
	private double calculatePercentage() {
		int counter = 0;
		double amount = 0.0;
		for(CraftingHit h : hits.keySet()) {
			counter++;
			double d = hits.get(h).getPercentage();
			if(d >= 200.0) continue;
			if(d <= 100.0) amount = amount+d;
			if(d > 100.0 && d <= 200.0) amount = amount+(200.0-d);
		}
		return Math.round((amount/counter));
	}

	private void warnOvershootHits(Player p) {
		if (Cache.hitOvershootWarnPercent <= 0) {
			return;
		}
		String template = Cache.hitOvershootWarnMessage;
		if (template == null || template.isBlank()) {
			return;
		}
		for (CraftingHit hit : hits.keySet()) {
			IntCounter counter = hits.get(hit);
			int needed = counter.getNeeded();
			int current = counter.getCurrent();
			if (needed <= 0 || current <= needed) {
				continue;
			}
			double overshoot = ((double) (current - needed) / needed) * 100.0;
			if (overshoot < Cache.hitOvershootWarnPercent) {
				continue;
			}
			p.sendMessage(template.replace("%hit%", hit.getName()));
		}
	}

	private StationFeedback createItem(Player p, Double forcedQualityPercent) {
		if (!checkItems(p)) {
			return StationFeedback.LACKING_ITEMS;
		}
		if (forcedQualityPercent == null && !checkHits(p)) {
			return StationFeedback.LACKING_HITS;
		}
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		result = api.getCreator().getItemFromPath("m."+recipe.getTemplate());
		MMOItem mmo = new LiveMMOItem(NBTItem.get(result));
		MMOStatApplicator.applyExternalLayer(mmo, stats, CraftStatCalculator.collectManagedStatIds(recipe), true);
		String max = MajorityTierResolver.resolveMajorityKey(recipe, currentMaterials);
		StringData itemName = (StringData) mmo.getData(ItemStats.NAME);
		ModelScheme scheme = null;
		String type = max.split("\\.")[0];
		String mId = max.split("\\.")[1];
		String name = "";
		if (type.equalsIgnoreCase("ingredient")) {
			Ingredient ing = IngredientLoader.getByString(mId);
			ItemStack i = ing.build();
			scheme = ing.getIngredientData().getModelScheme();
			if (i.getItemMeta().hasDisplayName()) {
				name = new String(i.getItemMeta().getDisplayName()).replace(" Ingot", "");
			} else {
				name = new String(WordUtils.capitalize(i.getType().toString().toLowerCase().replace("_ingot", "")));
			}

		} else if (type.equalsIgnoreCase("alloy")) {
			Alloy a = AlloyManager.getAlloyById(mId);
			name = a.getName();
			scheme = a.getData().getModelScheme();
		}

		// ✅ New logic starts here — replaces the old itemName.setString(...) line

		// Extract hex color code from the beginning of 'name'
		String colorPrefix = "";
		String recipeName = new String(recipe.getName());
		if (name.startsWith("§x") && name.length() >= 14) {
			colorPrefix = name.substring(0, 14); // e.g. §x§5§6§c§7§d§6
		}

		// Strip color code from name for re-use
		String strippedName = name;
		if (!colorPrefix.isEmpty()) {
			strippedName = name.substring(14);
		}

		// Replace %material% in the recipe name
		String result = recipeName.replace("%material%", strippedName);

		// Ensure the rest of the string inherits the color
		if (!colorPrefix.isEmpty() && !result.startsWith(colorPrefix)) {
			result = colorPrefix + result;
		}

		itemName.setString(result);


		mmo.replaceData(ItemStats.NAME, itemName);
		StatHistory hist = StatHistory.from(mmo, ItemStats.NAME);
		if (hist != null) {
            NameData og = (NameData) hist.getOriginalData();
            og.setString(result);
            mmo.setStatHistory(ItemStats.NAME, hist);
        }
		double percentage = forcedQualityPercent != null ? forcedQualityPercent : calculatePercentage();
		Quality q = getQuality(percentage);
		p.sendMessage("Quality: "+q.getName());
		p.sendMessage("Hit Percenage: §e"+percentage+"%");
		if (forcedQualityPercent == null) {
			warnOvershootHits(p);
		}
		List<String> sockets = new ArrayList<String>();
		SocketGroup socketGroup = SocketGroupLoader.getByString(recipe.getSocketGroupId());
		if(socketGroup == null) {
			Bukkit.getLogger().warning("AC: Recipe " + recipe.getId() + " has unknown socket-group: " + recipe.getSocketGroupId());
		} else {
			sockets.addAll(socketGroup.getSlots(q.getId()));
		}
		GemSocketsData gemData = new GemSocketsData(sockets);
		net.Indyuce.mmoitems.stat.data.type.StatData finalStat = gemData;
		mmo.setData(ItemStats.GEM_SOCKETS, finalStat);
		List<String> loreList = new ArrayList<String>();
		loreList.add("§fQuality: "+q.getName());
		StringListData lore = new StringListData(loreList);
		mmo.setData(ItemStats.LORE, lore);
		ItemStack finalItem = mmo.newBuilder().build();
		if(scheme != null) {
			if(!recipe.getModelType().equalsIgnoreCase("none")) {
				for(String s : currentMaterials.keySet()) {
					String modeltype = s.split("\\.")[0];
					String modelId = s.split("\\.")[1];
					if(modeltype.equalsIgnoreCase("ingredient")) {
						Ingredient ing = IngredientLoader.getByString(modelId);
						if(ing.getIngredientData().getType().getId().equalsIgnoreCase(recipe.getModelType())) scheme = ing.getIngredientData().getModelScheme();
					} else if(modeltype.equalsIgnoreCase("alloy")) {
						Alloy a = AlloyManager.getAlloyById(modelId);
						if(a.getData().getType().getId().equalsIgnoreCase(recipe.getModelType())) scheme = a.getData().getModelScheme();
					}
				}
			}
			finalItem = applyModel(finalItem, scheme);
		}
		CraftProvenance provenance = CraftProvenance.from(recipe, currentMaterials, q);
		provenance.applyTo(finalItem);
		int majorityTier = MajorityTierResolver.resolveTier(recipe, currentMaterials);
		if (majorityTier > 0) {
			CraftTierLore.applyTierLine(finalItem, majorityTier);
		}
		Location dropLoc = loc.clone().add(0, 1, 0);
		dropLoc.getWorld().dropItem(dropLoc, finalItem);
		if (forcedQualityPercent == null) {
			CraftLifecycle.fireItemCrafted(p, recipe.getId(), recipe.getCategoryId());
		}
		return StationFeedback.SUCCESS;
	}
	
	private ItemStack applyModel(ItemStack i, ModelScheme scheme) {
		String path = scheme.getModel(recipe.getType());
		if(path == null) {
			Bukkit.getLogger().warning("AC: No model in the scheme "+scheme.getId()+" for the recipe type "+recipe.getType());
			return i;
		}
		String type = path.split("\\.")[0];
		if(type.equalsIgnoreCase("v")) {
			i.setType(Material.valueOf(path.split("\\.")[1].toUpperCase()));
			ItemMeta m = i.getItemMeta();
			m.setCustomModelData(Integer.parseInt(path.split("\\.")[2]));
			i.setItemMeta(m);
		} else if(type.equalsIgnoreCase("ia")) {
			ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
			i = api.getArmorMerger().merge(i, Optional.empty(), path);
		}
		return i;
	}

	public StationFeedback hit(Player p, ItemStack i) {
		if(!checkItems(p)) return StationFeedback.LACKING_ITEMS;
		NBTItem nbt = NBTItem.get(i);
		if(!nbt.hasType()) return StationFeedback.WRONG_TYPE;
		CraftingHit hit = HitLoader.getByTool(nbt.getType()+"."+nbt.getString("MMOITEMS_ITEM_ID"));
		if(hit == null) return StationFeedback.WRONG_TYPE;
		if(!hitTypes.containsKey(hit.getType())) return StationFeedback.NONE;
		if(hitTypes.get(hit.getType()).isEqual()) return StationFeedback.CAPACITY;
		addHit(p, hit);
		return StationFeedback.SUCCESS;
		
	}
	
	private void addHit(Player p, CraftingHit hit) {
		if(hits.containsKey(hit)) {
			hits.get(hit).increaseCurrent(1);
		} else {
			IntCounter counter = new IntCounter();
			counter.setCurrent(1);
			hits.put(hit, counter);
		}
		hitTypes.get(hit.getType()).increaseCurrent(1);
		p.sendTitle("§a+1 "+hit.getName(), hit.getType().getName() + " hits: "+hitTypes.get(hit.getType()).getCurrent()+"/"+hitTypes.get(hit.getType()).getNeeded(), 5, 20, 5);
		CraftLifecycle.fireSmithingHit(p, hit.getId());
	}

	public void cancel() {
		drop(1);
		recipe = null;
		currentMaterials.clear();
	}
	
	public void drop() {
		for(String s : currentMaterials.keySet()) {
			ItemStack i = null;
			String type = s.split("\\.")[0];
			String mId = s.split("\\.")[1];
			if(type.equalsIgnoreCase("ingredient")) {
				Ingredient ing = IngredientLoader.getByString(mId);
				i = ing.build();
			} else if(type.equalsIgnoreCase("alloy")) {
				Alloy a = AlloyManager.getAlloyById(mId);
				i = a.build();
			}
			i.setAmount(currentMaterials.get(s));
			loc.getWorld().dropItem(loc, i);
		}
	}

	public void drop(int offset) {
		for(String s : currentMaterials.keySet()) {
			ItemStack i = null;
			String type = s.split("\\.")[0];
			String mId = s.split("\\.")[1];
			if(type.equalsIgnoreCase("ingredient")) {
				Ingredient ing = IngredientLoader.getByString(mId);
				i = ing.build();
			} else if(type.equalsIgnoreCase("alloy")) {
				Alloy a = AlloyManager.getAlloyById(mId);
				i = a.build();
			}
			i.setAmount(currentMaterials.get(s));
			loc.getWorld().dropItem(loc.clone().add(0, offset, 0), i);
		}
	}
}
