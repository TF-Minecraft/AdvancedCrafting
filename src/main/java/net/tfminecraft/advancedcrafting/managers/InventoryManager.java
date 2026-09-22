package net.tfminecraft.advancedcrafting.managers;

import net.tfminecraft.advancedcrafting.util.LegacyModelData;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.loaders.CategoryLoader;
import net.tfminecraft.advancedcrafting.loaders.StatTemplateLoader;
import net.tfminecraft.advancedcrafting.loaders.TypeLoader;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.crafting.RecipeCategory;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;
import net.tfminecraft.advancedcrafting.objects.stats.StatTemplate;
import net.tfminecraft.advancedcrafting.utils.StatTemplateMath;


public class InventoryManager {
	public static final String STAT_PREVIEW_TITLE = "§7Stat Preview";

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void categoryView(Player p) {
		Inventory i = AdvancedCrafting.plugin.getServer().createInventory(null, 27, "§7Select Category");
		int x = 0;
		for(String key : CategoryLoader.get().keySet()) {
			i.setItem(x, getCategoryItem(CategoryLoader.getByString(key)));
			x++;
		}
		int slotn = 0;
		while(slotn < i.getSize()) {
			if(i.getItem(slotn) == null) {
				ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
				ItemMeta fm = fill.getItemMeta();
				fm.setDisplayName("§8 ");
				fill.setItemMeta(fm);
				i.setItem(slotn, fill);
			}
			slotn++;
		}
		p.openInventory(i);
	}
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void recipeView(Player p, RecipeCategory c) {
		Inventory i = AdvancedCrafting.plugin.getServer().createInventory(null, 27, "§7Select Recipe");
		int x = 0;
		for(CraftingRecipe recipe : c.getRecipes()) {
			i.setItem(x, getRecipeItem(recipe));
			x++;
		}
		int slotn = 0;
		while(slotn < i.getSize()) {
			if(i.getItem(slotn) == null) {
				ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
				ItemMeta fm = fill.getItemMeta();
				fm.setDisplayName("§8 ");
				fill.setItemMeta(fm);
				i.setItem(slotn, fill);
			}
			slotn++;
		}
		p.openInventory(i);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void templatePreviewView(Player p, StatData source) {
		int count = 0;
		for (StatTemplate template : StatTemplateLoader.getAll()) {
			if (StatTemplateMath.hasOverlap(source, template)) {
				count++;
			}
		}
		if (count == 0) {
			p.sendMessage("§cThis item has no stats matching any template.");
			return;
		}
		int size = Math.min(54, Math.max(9, ((count + 8) / 9) * 9));
		Inventory i = AdvancedCrafting.plugin.getServer().createInventory(null, size, STAT_PREVIEW_TITLE);
		int slot = 0;
		for (StatTemplate template : StatTemplateLoader.getAll()) {
			if (!StatTemplateMath.hasOverlap(source, template)) {
				continue;
			}
			i.setItem(slot, getTemplatePreviewItem(source, template));
			slot++;
		}
		int slotn = 0;
		while (slotn < i.getSize()) {
			if (i.getItem(slotn) == null) {
				ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
				ItemMeta fm = fill.getItemMeta();
				fm.setDisplayName("§8 ");
				fill.setItemMeta(fm);
				i.setItem(slotn, fill);
			}
			slotn++;
		}
		p.openInventory(i);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private ItemStack getTemplatePreviewItem(StatData source, StatTemplate template) {
		ItemStack icon = template.getIcon();
		ItemStack item = icon != null ? icon.clone() : new ItemStack(Material.BARRIER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(template.getName());
		List<String> lore = new ArrayList<>();
		lore.addAll(StatTemplateMath.getPreviewLines(source, template));
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}
	
	// Keep the existing legacy text representation, formatting, and exact-string comparisons. This path mutates the existing ItemStack; replacing it would change aliases held by callers.
	@SuppressWarnings("deprecation")
	private ItemStack getCategoryItem(RecipeCategory c) {
		ItemStack i = new ItemStack(Material.BARRIER, 1);
		if(c.getRecipes().size() == 0) {
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(c.getName());
			List<String> lore = new ArrayList<>();
			lore.add("§7No entries");
			m.setLore(lore);
			i.setItemMeta(m);
			return i;
		}
		ItemAPI api = TLibs.getItemAPI();
		ItemStack template = api.getCreator().getItemFromPath("m."+c.getRecipes().get(0).getTemplate());
		if(template != null) {
			i.setType(template.getType());
		} 
		ItemMeta m = i.getItemMeta();
		if(template != null && LegacyModelData.has(template.getItemMeta())) {
			LegacyModelData.set(m, LegacyModelData.get(template.getItemMeta()));
		}
		m.setDisplayName(c.getName());
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#e0e677"+c.getRecipes().size()+" #b2db93Entries"));
		m.setLore(lore);
		NamespacedKey key = new NamespacedKey(AdvancedCrafting.plugin, "ac_category");
		m.getPersistentDataContainer().set(key, PersistentDataType.STRING, c.getId());
		i.setItemMeta(m);
		return i;
	}
	
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private ItemStack getRecipeItem(CraftingRecipe r) {
		ItemStack fallback = new ItemStack(Material.BARRIER, 1);
		ItemAPI api = TLibs.getItemAPI();
		String path = r.resolveMenuIconPath();
		ItemStack base = api.getCreator().getItemFromPath(path);
		if (base == null || (path.toLowerCase().startsWith("ia.") && base.getType() == Material.DIRT)) {
			return fallback;
		}
		ItemStack i = base.clone();
		ItemMeta m = i.getItemMeta();
		if (m == null) {
			return fallback;
		}
		m.setDisplayName("§7" + r.getCleanedName());
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#d1a566Recipe:"));
		for (String s : r.getRecipe().keySet()) {
			IngredientType t = TypeLoader.getIngredientTypeByString(s);
			lore.add(StringFormatter.formatHex(t.getName() + "§7: #6dd695x" + r.getRecipe().get(s)));
		}
		m.setLore(lore);
		NamespacedKey key = new NamespacedKey(AdvancedCrafting.plugin, "ac_recipe");
		m.getPersistentDataContainer().set(key, PersistentDataType.STRING, r.getId());
		i.setItemMeta(m);
		return i;
	}
}
