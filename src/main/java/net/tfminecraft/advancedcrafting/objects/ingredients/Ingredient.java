package net.tfminecraft.advancedcrafting.objects.ingredients;


import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.enums.APIType;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.advancedcrafting.objects.data.IngredientData;
import net.tfminecraft.advancedcrafting.utils.AcItemTags;
import net.tfminecraft.advancedcrafting.utils.IngredientLore;
import net.tfminecraft.advancedcrafting.utils.PDCKeys;

public class Ingredient {
	private String id;
	private String path;
	private String hex;
	
	private IngredientData data;
	private int revision;
	
	public Ingredient(String key, ConfigurationSection config) {
		id = key;
		path = config.getString("path");
		hex = config.getString("hex", "#FFFFFF");
		data = new IngredientData(config);
	}

	public int getRevision() {
		return revision;
	}

	public void setRevision(int revision) {
		this.revision = revision;
	}

	public boolean hasHex() {
		return !hex.equalsIgnoreCase("none");
	}

	public String getHex() {
		return hex;
	}

	public String getId() {
		return id;
	}

	public String getPath() {
		return path;
	}

	public IngredientData getIngredientData() {
		return data;
	}

	
	public void buildTo(ItemStack i) {
		ItemMeta m = i.getItemMeta();
		m.getPersistentDataContainer().set(PDCKeys.ingredientId(), PersistentDataType.STRING, id);
		if (!m.hasDisplayName()) {
			String defaultName = StringFormatter.getVanillaName(i.getType()); // e.g. "Iron Ingot"
			m.setDisplayName(StringFormatter.formatHex(hex + defaultName));
		}
		List<String> lore = m.getLore();
		if (lore == null) {
			lore = new ArrayList<>();
		}
		IngredientLore.Block loreBlock = IngredientLore.applyTypeAndRole(lore, data);
		AcItemTags.write(m, revision, loreBlock);
		m.setLore(lore);
		i.setItemMeta(m);
	}

	public ItemStack build() {
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		ItemStack i = api.getCreator().getItemFromPath(path);
		buildTo(i);
		return i;
	}
}
