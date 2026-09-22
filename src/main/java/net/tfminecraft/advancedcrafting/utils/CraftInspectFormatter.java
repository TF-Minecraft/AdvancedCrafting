package net.tfminecraft.advancedcrafting.utils;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.loaders.RecipeLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.CraftStack;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.data.CraftInput;
import net.tfminecraft.advancedcrafting.objects.data.CraftProvenance;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;

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
