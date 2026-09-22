package net.tfminecraft.AdvancedCrafting.Utils;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.AdvancedCrafting.Loaders.IngredientLoader;
import net.tfminecraft.AdvancedCrafting.Loaders.RecipeLoader;
import net.tfminecraft.AdvancedCrafting.Managers.AlloyManager;
import net.tfminecraft.AdvancedCrafting.Objects.Alloys.Alloy;
import net.tfminecraft.AdvancedCrafting.Objects.CraftStack;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.CraftingRecipe;
import net.tfminecraft.AdvancedCrafting.Objects.Data.CraftInput;
import net.tfminecraft.AdvancedCrafting.Objects.Data.CraftProvenance;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;

public final class CraftInspectFormatter {
	private CraftInspectFormatter() {
	}

	public static void send(Player player) {
		ItemStack hand = player.getInventory().getItemInMainHand();
		send(player, hand);
	}

	public static void send(CommandSender sender, ItemStack item) {
		CraftStack cs = new CraftStack(item);
		if (!cs.isCrafted()) {
			sender.sendMessage("§cHold a crafted AdvancedCrafting item.");
			return;
		}
		CraftProvenance provenance = cs.getProvenance();
		if (provenance == null) {
			sender.sendMessage("§cNo provenance data on this item.");
			return;
		}

		sender.sendMessage("§e--- Craft inspect ---");
		String recipeId = provenance.getRecipeId();
		CraftingRecipe recipe = RecipeLoader.getByString(recipeId);
		if (recipe != null) {
			sender.sendMessage("§7Recipe: §f" + recipeId + " §7(" + recipe.getCleanedName() + ")");
		} else {
			sender.sendMessage("§7Recipe: §f" + recipeId + " §c(unknown)");
		}
		sender.sendMessage("§7Quality: §f" + provenance.getQualityId());

		for (CraftInput input : provenance.getInputs()) {
			sender.sendMessage(formatInput(input));
		}

		List<CraftInput> outdated = cs.getOutdatedInputs();
		if (outdated.isEmpty()) {
			sender.sendMessage("§7Outdated: §aNo");
		} else {
			sender.sendMessage("§7Outdated: §cYes §7(" + outdated.size() + " input(s))");
		}
	}

	private static String formatInput(CraftInput input) {
		int liveRevision = getLiveRevision(input.getKind(), input.getId());
		String line = "§7- §f" + input.getKind() + "." + input.getId()
				+ " §7x" + input.getAmount()
				+ " §7rev " + input.getRevision();
		if (liveRevision > input.getRevision()) {
			line += " §c(live " + liveRevision + ")";
		}
		return line;
	}

	private static int getLiveRevision(String kind, String id) {
		if (kind.equalsIgnoreCase("ingredient")) {
			Ingredient ing = IngredientLoader.getByString(id);
			return ing != null ? ing.getRevision() : 0;
		}
		if (kind.equalsIgnoreCase("alloy")) {
			Alloy alloy = AlloyManager.getAlloyById(id);
			return alloy != null ? alloy.getRevision() : 0;
		}
		return 0;
	}
}
