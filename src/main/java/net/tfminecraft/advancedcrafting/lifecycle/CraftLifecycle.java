package net.tfminecraft.advancedcrafting.lifecycle;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class CraftLifecycle {

	private static PlayerAlloyForgeTracker tracker;

	private CraftLifecycle() {
	}

	public static void init(PlayerAlloyForgeTracker forgeTracker) {
		tracker = forgeTracker;
	}

	public static void fireAlloyCrafted(Player player, String alloyId) {
		if (player == null || alloyId == null || alloyId.isBlank()) {
			return;
		}
		String normalizedId = alloyId.toLowerCase(Locale.ROOT);
		Bukkit.getPluginManager().callEvent(new AlloyCraftedEvent(
				player, player.getUniqueId(), normalizedId));
	}

	public static void fireAlloyOutcome(Player player, String alloyId) {
		if (player == null || alloyId == null || alloyId.isBlank()) {
			return;
		}
		String normalizedId = alloyId.toLowerCase(Locale.ROOT);
		UUID playerUuid = player.getUniqueId();
		boolean firstForge = tracker != null && tracker.recordForge(playerUuid, normalizedId);

		Bukkit.getPluginManager().callEvent(new AlloyCraftedEvent(player, playerUuid, normalizedId));
		if (firstForge) {
			Bukkit.getPluginManager().callEvent(new AlloyDiscoveredEvent(player, playerUuid, normalizedId));
		}
	}

	public static void fireItemCrafted(Player player, String recipeId, String categoryId) {
		if (player == null || recipeId == null || recipeId.isBlank()) {
			return;
		}
		String category = categoryId == null ? "" : categoryId.toLowerCase(Locale.ROOT);
		Bukkit.getPluginManager().callEvent(new ItemCraftedEvent(
				player, player.getUniqueId(), recipeId, category));
	}

	public static void fireSmithingHit(Player player, String hitId) {
		if (player == null || hitId == null || hitId.isBlank()) {
			return;
		}
		String normalizedId = hitId.toLowerCase(Locale.ROOT);
		Bukkit.getPluginManager().callEvent(new SmithingHitEvent(
				player, player.getUniqueId(), normalizedId));
	}

	public static PlayerAlloyForgeTracker getTracker() {
		return tracker;
	}
}
