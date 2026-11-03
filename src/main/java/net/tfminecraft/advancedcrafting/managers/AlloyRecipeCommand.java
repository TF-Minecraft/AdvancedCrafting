package net.tfminecraft.advancedcrafting.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang.WordUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.lifecycle.CraftLifecycle;
import net.tfminecraft.advancedcrafting.lifecycle.PlayerAlloyForgeTracker;
import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.data.AlloyRecipe;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.utils.AcItemTags;

/**
 * {@code /alloy recipes} and {@code /alloy recipe [name]}: the base and catalysts of the alloys a
 * player has forged. Only the ingredients are shown, never hits, stats or forge odds. An alloy the
 * player has not forged gets the same reply whether or not it exists, so names can't be probed.
 */
final class AlloyRecipeCommand {
	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

	/** {@code /alloy recipes}: every alloy this account has forged, each name clickable. */
	void list(Player p) {
		List<Alloy> known = knownAlloys(p);
		if (known.isEmpty()) {
			p.sendMessage("§7You haven't forged any alloys yet. Forge one to keep its recipe here.");
			return;
		}
		p.sendMessage("§6Alloys you have forged §7(" + known.size() + ")§6, click one for its recipe:");
		Component line = Component.empty();
		for (int i = 0; i < known.size(); i++) {
			Alloy alloy = known.get(i);
			if (i > 0) {
				line = line.append(Component.text(", ", NamedTextColor.GRAY));
			}
			line = line.append(LEGACY.deserialize(alloy.getName())
					.clickEvent(ClickEvent.runCommand("/alloy recipe " + alloy.getId()))
					.hoverEvent(HoverEvent.showText(Component.text("Show the recipe", NamedTextColor.GRAY))));
		}
		p.sendMessage(line);
	}

	/** {@code /alloy recipe [name]}: the named alloy, or the alloy in the main hand. */
	void show(Player p, String[] args) {
		if (args.length < 2) {
			showHeld(p);
			return;
		}
		String typed = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
		String id = typed.replace(' ', '_').toLowerCase(Locale.ROOT);
		Alloy alloy = AlloyManager.getAlloyById(id);
		if (alloy == null || !knows(p, alloy.getId())) {
			p.sendMessage("§cYou don't know the recipe for §f" + typed + "§c.");
			return;
		}
		sendRecipe(p, alloy);
	}

	private void showHeld(Player p) {
		ItemStack held = p.getInventory().getItemInMainHand();
		if (AcItemTags.getKind(held) != AcItemTags.Kind.ALLOY) {
			p.sendMessage("§cHold an alloy, or use §f/alloy recipe <name>§c. §f/alloy recipes §clists the ones you know.");
			return;
		}
		Alloy alloy = AlloyManager.getAlloyById(AcItemTags.getId(held));
		if (alloy == null || !knows(p, alloy.getId())) {
			String name = held.getItemMeta().hasDisplayName() ? held.getItemMeta().getDisplayName() : "this alloy";
			p.sendMessage("§cYou don't know the recipe for §f" + name + "§c. Forge it yourself to learn it.");
			return;
		}
		sendRecipe(p, alloy);
	}

	private void sendRecipe(Player p, Alloy alloy) {
		AlloyRecipe recipe = alloy.getData().getRecipe();
		if (recipe == null) {
			p.sendMessage("§cThe recipe for §f" + alloy.getName() + " §cwas never written down.");
			return;
		}
		// The forge reads this index, so a combo that now makes something else must not be shown.
		String result = AdvancedCrafting.getAlloyRecipeStore().getResultByCombo(recipe.comboKey());
		if (result != null && !result.equalsIgnoreCase(alloy.getId())) {
			p.sendMessage("§cThat recipe no longer makes §f" + alloy.getName() + "§c.");
			return;
		}
		p.sendMessage("§6Recipe for §r" + alloy.getName());
		p.sendMessage("§7Base: §r" + ingredientName(recipe.getBaseId()));
		List<String> catalysts = new ArrayList<>();
		for (String id : recipe.getCatalystIds()) {
			catalysts.add(ingredientName(id));
		}
		p.sendMessage("§7Catalysts: §r" + (catalysts.isEmpty() ? "§fnone" : String.join("§7, §r", catalysts)));
		p.sendMessage("§8Add the base to an alloy forge first, then the catalysts in any order.");
	}

	/** Tab completion for {@code /alloy recipe}: only alloys this player has forged. */
	List<String> completions(Player p) {
		List<String> ids = new ArrayList<>();
		for (Alloy alloy : knownAlloys(p)) {
			ids.add(alloy.getId());
		}
		return ids;
	}

	private List<Alloy> knownAlloys(Player p) {
		PlayerAlloyForgeTracker tracker = CraftLifecycle.getTracker();
		if (tracker == null) {
			return List.of();
		}
		Set<String> ids = tracker.getForgedIds(p.getUniqueId());
		List<Alloy> known = new ArrayList<>();
		for (String id : ids) {
			Alloy alloy = AlloyManager.getAlloyById(id);
			if (alloy != null) {
				known.add(alloy);
			}
		}
		return known;
	}

	private boolean knows(Player p, String alloyId) {
		PlayerAlloyForgeTracker tracker = CraftLifecycle.getTracker();
		return tracker != null && tracker.hasForged(p.getUniqueId(), alloyId);
	}

	/** The ingredient's item name with its colour, or its id in title case if the item can't be built. */
	static String ingredientName(String id) {
		Ingredient ingredient = IngredientLoader.getByString(id);
		if (ingredient != null) {
			try {
				ItemStack item = ingredient.build();
				if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
					return item.getItemMeta().getDisplayName();
				}
			} catch (RuntimeException ex) {
				// The item source (MMOItems, ItemsAdder) may be missing; fall back to the id.
			}
		}
		return "§f" + WordUtils.capitalize(id.replace('_', ' '));
	}
}
