package net.tfminecraft.advancedcrafting.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.tfminecraft.advancedcrafting.loaders.RecipeLoader;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.CraftStack;
import net.tfminecraft.advancedcrafting.objects.data.CraftInput;
import net.tfminecraft.advancedcrafting.objects.data.CraftProvenance;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.utils.CraftTierLore;
import net.tfminecraft.advancedcrafting.utils.MajorityTierResolver;

public final class CraftStatRefresher {
	private CraftStatRefresher() {
	}

	public static RefreshResult refresh(ItemStack item) {
		return refresh(item, false);
	}

	public static RefreshResult refresh(ItemStack item, boolean force) {
		if (item == null || item.getType().isAir()) {
			return RefreshResult.unchanged();
		}

		CraftProvenance provenance = CraftProvenance.readFrom(item);
		if (provenance == null) {
			return RefreshResult.unchanged();
		}
		if (!force && !provenance.isOutdated()) {
			return RefreshResult.unchanged();
		}

		CraftingRecipe recipe = RecipeLoader.getByString(provenance.getRecipeId());
		if (recipe == null) {
			return RefreshResult.failed("unknown recipe: " + provenance.getRecipeId());
		}

		List<CraftInput> outdatedBefore = new ArrayList<>(provenance.getOutdatedInputs());
		StatData newStats = CraftStatCalculator.compute(recipe, provenance.getInputs());
		StatRefreshDebug.logBefore(item, provenance, recipe, newStats);

		LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(item));
		MMOStatApplicator.applyExternalLayer(mmo, newStats, CraftStatCalculator.collectManagedStatIds(recipe), true);

		ItemStack rebuilt = mmo.newBuilder().build();
		rebuilt.setAmount(item.getAmount());
		provenance.syncRevisions();
		provenance.applyTo(rebuilt);

		int majorityTier = MajorityTierResolver.resolveTier(recipe, provenance.getInputs());
		if (majorityTier > 0) {
			CraftTierLore.applyTierLine(rebuilt, majorityTier);
		}

		StatRefreshDebug.logAfter(rebuilt, recipe);

		return RefreshResult.updated(rebuilt, outdatedBefore);
	}

	public static RefreshResult refreshIfOutdated(ItemStack item) {
		CraftStack cs = new CraftStack(item);
		if (!cs.isCrafted() || !cs.hasOutdatedInputs()) {
			return RefreshResult.unchanged();
		}
		return refresh(item, false);
	}

	public static final class RefreshResult {
		private final boolean changed;
		private final ItemStack item;
		private final String error;
		private final List<CraftInput> outdatedInputs;

		private RefreshResult(boolean changed, ItemStack item, String error, List<CraftInput> outdatedInputs) {
			this.changed = changed;
			this.item = item;
			this.error = error;
			this.outdatedInputs = outdatedInputs != null ? outdatedInputs : List.of();
		}

		public static RefreshResult unchanged() {
			return new RefreshResult(false, null, null, List.of());
		}

		public static RefreshResult failed(String error) {
			return new RefreshResult(false, null, error, List.of());
		}

		public static RefreshResult updated(ItemStack item, List<CraftInput> outdatedInputs) {
			return new RefreshResult(true, item, null, outdatedInputs);
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

		public List<CraftInput> getOutdatedInputs() {
			return outdatedInputs;
		}
	}
}
