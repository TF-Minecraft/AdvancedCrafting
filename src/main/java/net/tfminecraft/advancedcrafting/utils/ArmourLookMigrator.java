package net.tfminecraft.advancedcrafting.utils;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.RecipeLoader;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.data.CraftProvenance;
import net.tfminecraft.advancedcrafting.objects.schemes.ModelScheme;
import net.tfminecraft.advancedcrafting.util.LegacyModelData;

/**
 * Brings crafted pieces made before the per-recipe models up to date. A piece that still wears the old default
 * look of its recipe (its metal's type model, or a configured legacy model) gets the recipe's current model
 * through the same merge skins use, so its MMOItems data stays. Any other look is a skin and is kept. Every
 * recorded craft also gets its model scheme tag, which ArmourShop's metal skin lines read, and pieces with an
 * ItemsAdder look are opted out of ItemsAdder's auto_update (see {@link IaAutoUpdate}).
 */
public final class ArmourLookMigrator {
	private ArmourLookMigrator() {
	}

	/** The same item when nothing changes, otherwise an updated copy. */
	public static ItemStack migrate(ItemStack item) {
		if (!Cache.armourLookMigration || item == null || item.getType().isAir() || !item.hasItemMeta()) {
			return item;
		}
		CraftProvenance provenance = CraftProvenance.readFrom(item);
		if (provenance == null) {
			return item;
		}
		CraftingRecipe recipe = RecipeLoader.getByString(provenance.getRecipeId());
		ModelScheme scheme = ModelSchemeResolver.resolve(recipe, provenance.getInputs());
		if (scheme == null) {
			return item;
		}
		ItemStack result = item;
		String target = scheme.getModel(recipe.getId());
		if (target != null && !wears(item, target) && wearsLegacyLook(item, recipe, scheme)) {
			String before = describe(item);
			result = ModelApplier.apply(item.clone(), target);
			ItemMeta meta = result.getItemMeta();
			meta.getPersistentDataContainer().set(PDCKeys.previousModel(), PersistentDataType.STRING, before);
			result.setItemMeta(meta);
			Bukkit.getLogger().info("[AC][LookMigration] " + recipe.getId() + " (" + scheme.getId() + "): " + before
					+ " -> " + target);
		}
		PersistentDataContainer tags = result.getItemMeta().getPersistentDataContainer();
		if (!scheme.getId().equals(tags.get(PDCKeys.craftModelScheme(), PersistentDataType.STRING))) {
			if (result == item) {
				result = item.clone();
			}
			ItemMeta meta = result.getItemMeta();
			meta.getPersistentDataContainer().set(PDCKeys.craftModelScheme(), PersistentDataType.STRING, scheme.getId());
			result.setItemMeta(meta);
		}
		if (IaAutoUpdate.isExposed(result)) {
			if (result == item) {
				result = item.clone();
			}
			IaAutoUpdate.protect(result);
		}
		return result;
	}

	/** Configured legacy models replace the scheme's type model, e.g. infantry pieces wore the paper look. */
	static boolean wearsLegacyLook(ItemStack item, CraftingRecipe recipe, ModelScheme scheme) {
		List<String> legacy = Cache.legacyModels.get(recipe.getId().toLowerCase(Locale.ROOT));
		if (legacy != null) {
			for (String path : legacy) {
				if (wears(item, path)) {
					return true;
				}
			}
			return false;
		}
		String typeModel = scheme.getModel(recipe.getType());
		return typeModel != null && wears(item, typeModel);
	}

	/** Whether the item shows this scheme model: the same ItemsAdder item, or the same vanilla material and model. */
	static boolean wears(ItemStack item, String path) {
		String[] parts = path.split("\\.");
		String ia = iaId(item);
		if (parts[0].equalsIgnoreCase("ia")) {
			return ia != null && ia.equalsIgnoreCase(path.substring(3));
		}
		if (!parts[0].equalsIgnoreCase("v") || parts.length != 3 || ia != null || hasItemsAdderCompound(item)
				|| !item.getType().name().equalsIgnoreCase(parts[1])) {
			return false;
		}
		int model = Integer.parseInt(parts[2]);
		ItemMeta meta = item.getItemMeta();
		int current = LegacyModelData.has(meta) ? LegacyModelData.get(meta) : 0;
		return current == model;
	}

	/** The look as a scheme path, for the log and the previous-model tag. */
	static String describe(ItemStack item) {
		String ia = iaId(item);
		if (ia != null) {
			return "ia." + ia;
		}
		ItemMeta meta = item.getItemMeta();
		int model = LegacyModelData.has(meta) ? LegacyModelData.get(meta) : 0;
		return "v." + item.getType().name().toLowerCase(Locale.ROOT) + "." + model;
	}

	/** TLibs stamps merged ItemsAdder looks as {@code ia: <namespace>.<id>}; returned as namespace:id. */
	private static String iaId(ItemStack item) {
		NBTItem nbt = NBTItem.get(item);
		if (!nbt.hasTag("ia")) {
			return null;
		}
		String ia = nbt.getString("ia");
		int dot = ia.indexOf('.');
		return dot > 0 && dot < ia.length() - 1 ? ia.substring(0, dot) + ":" + ia.substring(dot + 1) : null;
	}

	private static boolean hasItemsAdderCompound(ItemStack item) {
		return NBTItem.get(item).hasTag("itemsadder");
	}
}
