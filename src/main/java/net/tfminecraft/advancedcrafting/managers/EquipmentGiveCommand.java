package net.tfminecraft.advancedcrafting.managers;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.loaders.RecipeLoader;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingStation;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;

/** Staff grants use the normal equipment builder, without crafting rewards or station state. */
final class EquipmentGiveCommand {
    boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(Cache.giveEquipmentPermission);
    }

    boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 4 || args.length > 6) {
            sender.sendMessage("§cUsage: /ac give equipment <recipe> <ingredient.id|alloy.id> [player] [quality 0-100]");
            return true;
        }
        Player target;
        if (args.length >= 5) {
            target = Bukkit.getPlayerExact(args[4]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: §f" + args[4]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cSpecify an online player from console.");
            return true;
        }
        double quality = 100;
        if (args.length == 6) {
            try {
                quality = Double.parseDouble(args[5]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cQuality must be a finite number from 0 to 100.");
                return true;
            }
            if (!Double.isFinite(quality) || quality < 0 || quality > 100) {
                sender.sendMessage("§cQuality must be a finite number from 0 to 100.");
                return true;
            }
        }
        CraftingRecipe recipe = RecipeLoader.getByString(args[2].toLowerCase(Locale.ROOT));
        if (recipe == null) {
            sender.sendMessage("§cUnknown recipe: §f" + args[2]);
            return true;
        }
        String material = args[3].toLowerCase(Locale.ROOT);
        if (!materialsFor(recipe).contains(material)) {
            sender.sendMessage("§cUnknown or incompatible base material/alloy: §f" + args[3]);
            return true;
        }
        HashMap<String, Integer> inputs = new HashMap<>();
        for (var entry : recipe.getRecipe().entrySet()) {
            String key = material;
            if (!entry.getKey().equalsIgnoreCase(recipe.getMainType())) {
                Ingredient filler = IngredientLoader.get().stream()
                        .filter(i -> i.getIngredientData().getType().getId().equalsIgnoreCase(entry.getKey()))
                        .min(Comparator.comparingInt((Ingredient i) -> i.getIngredientData().getTier())
                                .thenComparing(Ingredient::getId)).orElse(null);
                if (filler == null) {
                    sender.sendMessage("§cNo configured ingredient for required type: §f" + entry.getKey());
                    return true;
                }
                key = "ingredient." + filler.getId();
            }
            inputs.put(key, entry.getValue());
        }
        try {
            CraftingStation station = new CraftingStation(target.getLocation(), recipe, inputs, new HashMap<>());
            ItemStack item = station.buildCompletedItem(target, quality);
            for (ItemStack overflow : target.getInventory().addItem(item).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), overflow);
            }
        } catch (RuntimeException ex) {
            AdvancedCrafting.plugin.getLogger().log(Level.SEVERE, "Failed to give equipment for recipe " + recipe.getId(), ex);
            sender.sendMessage("§cEquipment could not be built. Check the server log and recipe/template configuration.");
            return true;
        }
        sender.sendMessage("§aGave §f" + recipe.getId() + "§a using §f" + material + "§a to §f" + target.getName() + "§a.");
        return true;
    }

    private List<String> materialsFor(CraftingRecipe recipe) {
        if (!recipe.getRecipe().containsKey(recipe.getMainType())) return List.of();
        Stream<String> ingredients = IngredientLoader.get().stream()
                .filter(i -> i.getIngredientData().getType().getId().equalsIgnoreCase(recipe.getMainType()))
                .map(i -> "ingredient." + i.getId());
        Stream<String> alloys = AlloyManager.getAlloyIds().stream()
                .filter(id -> AlloyManager.getAlloyById(id).getData().getType().getId().equalsIgnoreCase(recipe.getMainType()))
                .map(id -> "alloy." + id);
        return Stream.concat(ingredients, alloys).sorted().toList();
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) return List.of();
        List<String> options;
        switch (args.length) {
            case 3 -> options = RecipeLoader.get().keySet().stream().sorted().toList();
            case 4 -> {
                CraftingRecipe recipe = RecipeLoader.getByString(args[2].toLowerCase(Locale.ROOT));
                options = recipe == null ? List.of() : materialsFor(recipe);
            }
            case 5 -> options = Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
            case 6 -> options = List.of("0", "25", "50", "75", "90", "100");
            default -> options = List.of();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
