package net.tfminecraft.advancedcrafting.objects.crafting;

import net.tfminecraft.advancedcrafting.util.LegacyModelData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.utils.IntCounter;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.GemSocketsData;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.type.NameData;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.enums.StationFeedback;
import net.tfminecraft.advancedcrafting.loaders.HitLoader;
import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.loaders.QualityLoader;
import net.tfminecraft.advancedcrafting.loaders.SocketGroupLoader;
import net.tfminecraft.advancedcrafting.loaders.TypeLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.CraftStack;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.CraftingHit;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.HitType;
import net.tfminecraft.advancedcrafting.objects.data.CraftProvenance;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;
import net.tfminecraft.advancedcrafting.objects.schemes.ModelScheme;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;
import net.tfminecraft.advancedcrafting.utils.CraftStatCalculator;
import net.tfminecraft.advancedcrafting.utils.CraftTierLore;
import net.tfminecraft.advancedcrafting.utils.MMOStatApplicator;
import net.tfminecraft.advancedcrafting.utils.MajorityTierResolver;
import net.tfminecraft.advancedcrafting.utils.ProfessionPermissions;
import net.tfminecraft.advancedcrafting.lifecycle.CraftLifecycle;

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

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
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
		StationFeedback f = createItem(p, forcedQualityPercent);
		// Failed attempts keep the station's materials, so paying XP before the checks let every retry pay again.
		if (f == StationFeedback.SUCCESS) {
			giveXP(p);
		}
		return f;
	}

	// skill(amount), e.g. crafter(2.0)
	private static final Pattern XP_FORMAT = Pattern.compile("([A-Za-z0-9_-]+)\\((\\d+(?:\\.\\d+)?)\\)");

	private void giveXP(Player p) {
		// Map of skill name -> total XP to give
		Map<String, Double> xpBySkill = new HashMap<>();

		for (String s : currentMaterials.keySet()) {
			String[] split = s.split("\\.");
			String type = split[0];
			String mId = split[1];
			int amount = currentMaterials.get(s);

			String raw = null;

			if (type.equalsIgnoreCase("ingredient")) {
				Ingredient ingredient = IngredientLoader.getByString(mId); // Assuming you have a method like this
				if (ingredient != null && ingredient.getIngredientData().hasXP()) {
					raw = ingredient.getIngredientData().getXP();
				}
			} else if (type.equalsIgnoreCase("alloy")) {
				Alloy alloy = AlloyManager.getAlloyById(mId); // Likewise for alloy
				if (alloy != null && alloy.getData().hasXP()) {
					raw = alloy.getData().getXP();
				}
			}
			if (raw == null) continue;

			// XP is paid after the item drops, so a bad value must be skipped rather than throw and leave the station uncleared.
			Matcher m = XP_FORMAT.matcher(raw.trim());
			if (!m.matches()) {
				Bukkit.getLogger().warning("AC: Invalid xp value '" + raw + "', expected skill(amount)");
				continue;
			}
			String skill = m.group(1);
			double xpPerUnit = Double.parseDouble(m.group(2));

			if (xpPerUnit > 0) {
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

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private StationFeedback createItem(Player p, Double forcedQualityPercent) {
		if (!checkItems(p)) {
			return StationFeedback.LACKING_ITEMS;
		}
		if (forcedQualityPercent == null && !checkHits(p)) {
			return StationFeedback.LACKING_HITS;
		}
		ItemAPI api = TLibs.getItemAPI();
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
		StatHistory hist = mmo.computeStatHistory(ItemStats.NAME);
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
	
	// This path mutates the existing ItemStack; replacing it would change aliases held by callers.
	@SuppressWarnings("deprecation")
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
			LegacyModelData.set(m, Integer.parseInt(path.split("\\.")[2]));
			i.setItemMeta(m);
		} else if(type.equalsIgnoreCase("ia")) {
			ItemAPI api = TLibs.getItemAPI();
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
	
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
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
