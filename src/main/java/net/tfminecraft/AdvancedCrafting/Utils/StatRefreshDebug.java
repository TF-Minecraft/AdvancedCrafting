package net.tfminecraft.AdvancedCrafting.Utils;

import java.util.Set;

import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.AdvancedCrafting.AdvancedCrafting;
import net.tfminecraft.AdvancedCrafting.Cache.Cache;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.CraftingRecipe;
import net.tfminecraft.AdvancedCrafting.Objects.Data.CraftProvenance;
import net.tfminecraft.AdvancedCrafting.Objects.Data.StatData;
import net.tfminecraft.AdvancedCrafting.Objects.Data.CraftInput;
import net.tfminecraft.AdvancedCrafting.Objects.Stats.StatModifier;
import net.tfminecraft.AdvancedCrafting.Objects.Stats.StatTemplate;
import net.tfminecraft.AdvancedCrafting.Loaders.IngredientLoader;
import net.tfminecraft.AdvancedCrafting.Managers.AlloyManager;
import net.tfminecraft.AdvancedCrafting.Objects.Alloys.Alloy;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;

public final class StatRefreshDebug {
	private StatRefreshDebug() {
	}

	public static void logBefore(ItemStack item, CraftProvenance provenance, CraftingRecipe recipe, StatData computed) {
		if (!Cache.debugStatRefresh) {
			return;
		}
		StatTemplate template = recipe.getStatTemplate();
		int liveRev = template != null ? template.getRevision() : -1;
		StringBuilder outdated = new StringBuilder();
		for (CraftInput input : provenance.getOutdatedInputs()) {
			if (outdated.length() > 0) {
				outdated.append(", ");
			}
			outdated.append(input.getKind()).append('.').append(input.getId())
					.append("(stored=").append(input.getRevision())
					.append(", live=").append(liveRevision(input)).append(')');
		}
		log("start recipe=" + provenance.getRecipeId()
				+ " templateRev stored=" + provenance.getStatTemplateRevision() + " live=" + liveRev
				+ " templateOutdated=" + provenance.isStatTemplateOutdated()
				+ " outdatedInputs=[" + outdated + "]");
		Set<String> managed = CraftStatCalculator.collectManagedStatIds(recipe);
		LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(item));
		for (String statId : managed) {
			if (MMOStatApplicator.isDurabilityStat(statId)) {
				continue;
			}
			logStatLine("before", mmo, statId, findComputed(computed, statId));
		}
	}

	public static void logAfter(ItemStack item, CraftingRecipe recipe) {
		if (!Cache.debugStatRefresh) {
			return;
		}
		Set<String> managed = CraftStatCalculator.collectManagedStatIds(recipe);
		LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(item));
		for (String statId : managed) {
			if (MMOStatApplicator.isDurabilityStat(statId)) {
				continue;
			}
			logStatLine("after", mmo, statId, null);
		}
	}

	private static Double findComputed(StatData computed, String statId) {
		if (computed == null) {
			return null;
		}
		for (StatModifier mod : computed.getModifiers()) {
			if (mod.getType().equalsIgnoreCase(statId)) {
				return mod.getAmount();
			}
		}
		return null;
	}

	private static void logStatLine(String phase, LiveMMOItem mmo, String statId, Double computed) {
		ItemStat<?, ?> itemStat = MMOItems.plugin.getStats().get(statId.toUpperCase());
		if (itemStat == null) {
			return;
		}
		DoubleData current = (DoubleData) mmo.getData(itemStat);
		double total = current != null ? current.getValue() : 0;
		double og = 0;
		double external = 0;
		StatHistory hist = StatHistory.from(mmo, itemStat);
		if (hist != null) {
			Object ogData = hist.getOriginalData();
			if (ogData instanceof DoubleData doubleOg) {
				og = doubleOg.getValue();
			}
			// External total is not always exposed; total - og is a useful hint after zero-OG apply.
			external = total - og;
		}
		String computedPart = computed != null ? " computed=" + computed : "";
		log(phase + " " + statId + " total=" + total + " og=" + og + " (total-og)=" + external + computedPart);
	}

	private static int liveRevision(CraftInput input) {
		if (input.getKind().equalsIgnoreCase("ingredient")) {
			Ingredient ing = IngredientLoader.getByString(input.getId());
			return ing != null ? ing.getRevision() : 0;
		}
		if (input.getKind().equalsIgnoreCase("alloy")) {
			Alloy alloy = AlloyManager.getAlloyById(input.getId());
			return alloy != null ? alloy.getRevision() : 0;
		}
		return 0;
	}

	private static void log(String message) {
		AdvancedCrafting.plugin.getLogger().info("[AC][StatRefresh] " + message);
	}
}
