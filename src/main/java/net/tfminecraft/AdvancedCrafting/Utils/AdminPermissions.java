package net.tfminecraft.AdvancedCrafting.Utils;

import org.bukkit.command.CommandSender;

public final class AdminPermissions {
	public static final String PERMISSION = "advancedcrafting.admin";

	private AdminPermissions() {
	}

	public static boolean require(CommandSender sender) {
		if (sender.hasPermission(PERMISSION)) {
			return true;
		}
		sender.sendMessage("§cNo permission.");
		return false;
	}
}
