package net.tfminecraft.AdvancedCrafting.Managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.AdvancedCrafting.AdvancedCrafting;
import net.tfminecraft.AdvancedCrafting.Database.AlloyDatabase;
import net.tfminecraft.AdvancedCrafting.Managers.AlloyManager;
import net.tfminecraft.AdvancedCrafting.Objects.Alloys.Alloy;
import net.tfminecraft.AdvancedCrafting.Objects.CraftStack;
import net.tfminecraft.AdvancedCrafting.Utils.AdminPermissions;
import net.tfminecraft.AdvancedCrafting.Utils.AlloyInfoFormatter;
import net.tfminecraft.AdvancedCrafting.Utils.AlloyRecipeSync;
import net.tfminecraft.AdvancedCrafting.Utils.CraftInspectFormatter;
import net.tfminecraft.AdvancedCrafting.Utils.CraftStatRefresher;
import net.tfminecraft.AdvancedCrafting.Utils.CraftStatRefresher.RefreshResult;

public class CommandManager implements Listener, CommandExecutor, TabCompleter {
	public String cmd1 = "ac";
	public String cmd2 = "alloy";

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase(cmd1)) {
			return handleAcCommand(sender, args);
		}
		if (sender instanceof Player p) {
			if (cmd.getName().equalsIgnoreCase(cmd2) && args.length >= 1 && args[0].equalsIgnoreCase("name")) {
				if (args.length < 2) {
					p.sendMessage("§cNo name specified, format: /alloy name newname");
					return false;
				}
				AdvancedCrafting.getAlloyManager().nameAlloy(p, args[1]);
				return true;
			}
		}
		return false;
	}

	private boolean handleAcCommand(CommandSender sender, String[] args) {
		if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
			if (!AdminPermissions.require(sender)) {
				return true;
			}
			if (sender instanceof Player p) {
				AdvancedCrafting.plugin.reloadMessage(p);
			} else {
				AdvancedCrafting.plugin.reload();
				sender.sendMessage("[AdvancedCrafting] Reload complete.");
			}
			return true;
		}
		if (args.length >= 2 && args[0].equalsIgnoreCase("sync") && args[1].equalsIgnoreCase("recipes")) {
			if (!AdminPermissions.require(sender)) {
				return true;
			}
			boolean repair = args.length >= 3 && args[2].equalsIgnoreCase("repair");
			AlloyRecipeSync.run(sender, repair);
			return true;
		}
		if (args.length >= 1 && args[0].equalsIgnoreCase("refresh")) {
			if (!AdminPermissions.require(sender)) {
				return true;
			}
			if (!(sender instanceof Player p)) {
				sender.sendMessage("§cPlayers only.");
				return true;
			}
			ItemStack hand = p.getInventory().getItemInMainHand();
			CraftStack cs = new CraftStack(hand);
			if (!cs.isCrafted()) {
				p.sendMessage("§cHold a crafted AdvancedCrafting item.");
				return true;
			}
			RefreshResult result = CraftStatRefresher.refresh(hand, true);
			if (result.getError() != null) {
				p.sendMessage("§cRefresh failed: " + result.getError());
				return true;
			}
			if (!result.isChanged()) {
				p.sendMessage("§7No changes applied.");
				return true;
			}
			p.getInventory().setItemInMainHand(result.getItem());
			p.sendMessage("§aItem stats refreshed.");
			return true;
		}
		if (args.length >= 1 && args[0].equalsIgnoreCase("inspect")) {
			if (!AdminPermissions.require(sender)) {
				return true;
			}
			if (!(sender instanceof Player p)) {
				sender.sendMessage("§cPlayers only.");
				return true;
			}
			CraftInspectFormatter.send(p);
			return true;
		}
		if (args.length >= 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("alloy")) {
			if (!AdminPermissions.require(sender)) {
				return true;
			}
			return handleGiveAlloy(sender, args[2], args.length >= 4 ? args[3] : null);
		}
		if (args.length >= 3 && args[0].equalsIgnoreCase("alloy") && args[1].equalsIgnoreCase("info")) {
			if (!AdminPermissions.require(sender)) {
				return true;
			}
			AlloyInfoFormatter.send(sender, args[2]);
			return true;
		}
		if (args.length >= 2 && args[0].equalsIgnoreCase("craft")) {
			if (!AdminPermissions.require(sender)) {
				return true;
			}
			if (!(sender instanceof Player p)) {
				sender.sendMessage("§cPlayers only.");
				return true;
			}
			double percent;
			try {
				percent = Double.parseDouble(args[1]);
			} catch (NumberFormatException ex) {
				p.sendMessage("§cInvalid quality percent. Usage: /ac craft <percent>");
				return true;
			}
			if (percent < 0) {
				percent = 0;
			}
			if (percent > 100) {
				percent = 100;
			}
			AdvancedCrafting.getCraftingManager().setAdminCraftPending(p, percent);
			p.sendMessage("§aAdmin craft armed at §f" + percent + "%§a quality.");
			p.sendMessage("§7Right-click the crafting station with all materials ready (30s).");
			return true;
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase(cmd1)) {
			return onAcTabComplete(sender, args);
		}
		if (cmd.getName().equalsIgnoreCase(cmd2)) {
			return onAlloyTabComplete(args);
		}
		return List.of();
	}

	private List<String> onAcTabComplete(CommandSender sender, String[] args) {
		if (!sender.hasPermission(AdminPermissions.PERMISSION)) {
			return List.of();
		}
		if (args.length == 0) {
			return filterPrefix("", "reload", "sync", "refresh", "inspect", "give", "alloy", "craft");
		}
		if (args.length == 1) {
			return filterPrefix(args[0], "reload", "sync", "refresh", "inspect", "give", "alloy", "craft");
		}
		if (args.length == 2) {
			String sub = args[0].toLowerCase(Locale.ROOT);
			if (sub.equals("sync")) {
				return filterPrefix(args[1], "recipes");
			}
			if (sub.equals("give")) {
				return filterPrefix(args[1], "alloy");
			}
			if (sub.equals("alloy")) {
				return filterPrefix(args[1], "info");
			}
			if (sub.equals("craft")) {
				return filterPrefix(args[1], "25", "50", "75", "90", "100");
			}
			return List.of();
		}
		if (args.length == 3) {
			String sub = args[0].toLowerCase(Locale.ROOT);
			if (sub.equals("sync") && args[1].equalsIgnoreCase("recipes")) {
				return filterPrefix(args[2], "repair");
			}
			if (sub.equals("give") && args[1].equalsIgnoreCase("alloy")) {
				return filterPrefix(args[2], AlloyManager.getAlloyIds());
			}
			if (sub.equals("alloy") && args[1].equalsIgnoreCase("info")) {
				return filterPrefix(args[2], AlloyManager.getAlloyIds());
			}
			return List.of();
		}
		if (args.length == 4) {
			String sub = args[0].toLowerCase(Locale.ROOT);
			if (sub.equals("give") && args[1].equalsIgnoreCase("alloy")) {
				return filterPrefix(args[3], Bukkit.getOnlinePlayers().stream()
						.map(Player::getName)
						.collect(Collectors.toList()));
			}
		}
		return List.of();
	}

	private List<String> onAlloyTabComplete(String[] args) {
		if (args.length == 0) {
			return filterPrefix("", "name");
		}
		if (args.length == 1) {
			return filterPrefix(args[0], "name");
		}
		return List.of();
	}

	private List<String> filterPrefix(String input, String... options) {
		return filterPrefix(input, Arrays.asList(options));
	}

	private List<String> filterPrefix(String input, Iterable<String> options) {
		String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
		List<String> matches = new ArrayList<>();
		for (String option : options) {
			if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
				matches.add(option);
			}
		}
		return matches;
	}

	private boolean handleGiveAlloy(CommandSender sender, String alloyId, String playerName) {
		Alloy alloy = AlloyManager.getAlloyById(alloyId);
		if (alloy == null) {
			AlloyDatabase db = new AlloyDatabase();
			alloy = db.loadAlloy(alloyId);
			if (alloy != null) {
				AlloyManager.addAlloy(alloy);
			}
		}
		if (alloy == null) {
			sender.sendMessage("§cUnknown alloy: §f" + alloyId);
			return true;
		}

		Player target;
		if (playerName != null) {
			target = Bukkit.getPlayer(playerName);
			if (target == null) {
				sender.sendMessage("§cPlayer not found: §f" + playerName);
				return true;
			}
		} else if (sender instanceof Player p) {
			target = p;
		} else {
			sender.sendMessage("§cSpecify a player from console.");
			return true;
		}

		ItemStack item = alloy.build();
		HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(item);
		for (ItemStack drop : leftover.values()) {
			target.getWorld().dropItemNaturally(target.getLocation(), drop);
		}
		sender.sendMessage("§aGave alloy §f" + alloy.getId() + "§a to §f" + target.getName() + "§a.");
		if (!sender.equals(target)) {
			target.sendMessage("§aYou received alloy §f" + alloy.getName() + "§a.");
		}
		return true;
	}
}
