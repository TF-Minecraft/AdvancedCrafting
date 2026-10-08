package net.tfminecraft.advancedcrafting.utils;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.schemes.ModelScheme;
import net.tfminecraft.advancedcrafting.util.LegacyModelData;
import net.tfminecraft.tlibs.TLibs;

/** Model scheme paths: {@code v.<material>.<model>} for vanilla items, {@code ia.<namespace>:<id>} for ItemsAdder. */
public final class ModelApplier {
	private ModelApplier() {
	}

	/** A model named after the recipe (e.g. heavy_helmet) wins over the shared type (helmet). */
	public static String modelFor(ModelScheme scheme, CraftingRecipe recipe) {
		String path = scheme.getModel(recipe.getId());
		return path != null ? path : scheme.getModel(recipe.getType());
	}

	// This path mutates the existing ItemStack; replacing it would change aliases held by callers.
	@SuppressWarnings("deprecation")
	public static ItemStack apply(ItemStack i, String path) {
		String type = path.split("\\.")[0];
		if(type.equalsIgnoreCase("v")) {
			i.setType(Material.valueOf(path.split("\\.")[1].toUpperCase()));
			ItemMeta m = i.getItemMeta();
			LegacyModelData.set(m, Integer.parseInt(path.split("\\.")[2]));
			i.setItemMeta(m);
		} else if(type.equalsIgnoreCase("ia")) {
			i = TLibs.getItemAPI().getArmorMerger().merge(i, Optional.empty(), path);
		}
		return i;
	}
}
