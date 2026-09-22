package net.tfminecraft.advancedcrafting.objects;

import java.util.Collections;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.data.CraftInput;
import net.tfminecraft.advancedcrafting.objects.data.CraftProvenance;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.utils.PDCKeys;

public class CraftStack {
	private ItemStack item;
	
	public CraftStack(ItemStack i) {
		item = i;
	}
	
	public boolean isIngredient() {
		if(item == null) return false;
		ItemMeta m = item.getItemMeta();
		if(m == null) return false;
		NamespacedKey key = PDCKeys.ingredientId();
		String id = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		if(id != null) {
			return true;
		}
		return false;
	}
	public boolean isAlloy() {
		if(item == null) return false;
		ItemMeta m = item.getItemMeta();
		if(m == null) return false;
		NamespacedKey key = PDCKeys.alloyId();
		String id = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		if(id != null) {
			return true;
		}
		return false;
	}
	public boolean isCrafted() {
		if (item == null || !item.hasItemMeta()) {
			return false;
		}
		return item.getItemMeta().getPersistentDataContainer().get(PDCKeys.craftRecipe(), PersistentDataType.STRING) != null;
	}
	public Ingredient getIngredient() {
		if(!isIngredient()) {
			return null;
		}
		ItemMeta m = item.getItemMeta();
		NamespacedKey key = PDCKeys.ingredientId();
		String id = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		Ingredient i = IngredientLoader.getByString(id);
		return i;
	}
	public Alloy getAlloy() {
		if(!isAlloy()) {
			return null;
		}
		ItemMeta m = item.getItemMeta();
		NamespacedKey key = PDCKeys.alloyId();
		String id = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		Alloy a = AlloyManager.getAlloyById(id);
		return a;
	}
	public CraftProvenance getProvenance() {
		return CraftProvenance.readFrom(item);
	}
	public boolean hasOutdatedInputs() {
		CraftProvenance provenance = getProvenance();
		return provenance != null && provenance.isOutdated();
	}
	public List<CraftInput> getOutdatedInputs() {
		CraftProvenance provenance = getProvenance();
		if (provenance == null) {
			return Collections.emptyList();
		}
		return provenance.getOutdatedInputs();
	}
}
