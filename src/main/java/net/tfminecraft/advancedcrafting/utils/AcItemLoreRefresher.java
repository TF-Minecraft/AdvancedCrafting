package net.tfminecraft.advancedcrafting.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.database.AlloyDatabase;
import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.utils.AcItemTags.Kind;

public final class AcItemLoreRefresher {
	private AcItemLoreRefresher() {
	}

	public static RefreshResult refreshIfOutdated(ItemStack item) {
		if (!AcItemTags.isManaged(item)) {
			return RefreshResult.unchanged();
		}
		if (!isOutdated(item)) {
			return RefreshResult.unchanged();
		}
		return refresh(item);
	}

	public static boolean isOutdated(ItemStack item) {
		if (!AcItemTags.isManaged(item)) {
			return false;
		}
		if (AcItemTags.getLoreStart(item) < 0) {
			return true;
		}
		if (!AcItemTags.hasStatsLoreFlag(item)
				|| AcItemTags.getStatsLore(item) != Cache.showIngredientStats) {
			return true;
		}
		return getLiveRevision(item) > AcItemTags.getStoredRevision(item);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public static RefreshResult refresh(ItemStack item) {
		if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
			return RefreshResult.unchanged();
		}
		Kind kind = AcItemTags.getKind(item);
		String id = AcItemTags.getId(item);
		if (kind == null || id == null) {
			return RefreshResult.unchanged();
		}

		ItemStack copy = item.clone();
		ItemMeta meta = copy.getItemMeta();
		List<String> lore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : List.of());
		int loreStart = AcItemTags.getLoreStart(copy);
		int oldLen = AcItemTags.getLoreLen(copy);
		if (oldLen < 0) {
			oldLen = 2;
		}
		int liveRevision;
		IngredientLore.Block loreBlock;

		if (kind == Kind.INGREDIENT) {
			Ingredient ingredient = IngredientLoader.getByString(id);
			if (ingredient == null) {
				return RefreshResult.failed("unknown ingredient: " + id);
			}
			liveRevision = ingredient.getRevision();
			if (loreStart < 0) {
				loreBlock = IngredientLore.applyTypeAndRole(lore, ingredient.getIngredientData());
			} else {
				loreBlock = IngredientLore.spliceTypeAndRole(lore, loreStart, oldLen, ingredient.getIngredientData());
			}
		} else {
			Alloy alloy = AlloyManager.getAlloyById(id);
			if (alloy == null) {
				alloy = new AlloyDatabase().loadAlloy(id);
				if (alloy != null) {
					AlloyManager.addAlloy(alloy);
				}
			}
			if (alloy == null) {
				return RefreshResult.failed("unknown alloy: " + id);
			}
			liveRevision = alloy.getRevision();
			if (loreStart < 0) {
				loreBlock = IngredientLore.applyAlloyLore(lore, alloy.getData().getType(), alloy.getData().getTier(),
						alloy.getData().getStatData());
			} else {
				loreBlock = IngredientLore.spliceAlloyLore(lore, loreStart, oldLen, alloy.getData().getType(),
						alloy.getData().getTier(), alloy.getData().getStatData());
			}
		}

		meta.setLore(lore);
		AcItemTags.write(meta, liveRevision, loreBlock);
		copy.setItemMeta(meta);
		copy.setAmount(item.getAmount());
		return RefreshResult.updated(copy);
	}

	private static int getLiveRevision(ItemStack item) {
		Kind kind = AcItemTags.getKind(item);
		String id = AcItemTags.getId(item);
		if (kind == null || id == null) {
			return 0;
		}
		if (kind == Kind.INGREDIENT) {
			Ingredient ingredient = IngredientLoader.getByString(id);
			return ingredient != null ? ingredient.getRevision() : 0;
		}
		Alloy alloy = AlloyManager.getAlloyById(id);
		if (alloy == null) {
			alloy = new AlloyDatabase().loadAlloy(id);
		}
		return alloy != null ? alloy.getRevision() : 0;
	}

	public static final class RefreshResult {
		private final boolean changed;
		private final ItemStack item;
		private final String error;

		private RefreshResult(boolean changed, ItemStack item, String error) {
			this.changed = changed;
			this.item = item;
			this.error = error;
		}

		public static RefreshResult unchanged() {
			return new RefreshResult(false, null, null);
		}

		public static RefreshResult failed(String error) {
			return new RefreshResult(false, null, error);
		}

		public static RefreshResult updated(ItemStack item) {
			return new RefreshResult(true, item, null);
		}

		public boolean isChanged() {
			return changed;
		}

		public ItemStack getItem() {
			return item;
		}

		public String getError() {
			return error;
		}
	}
}
