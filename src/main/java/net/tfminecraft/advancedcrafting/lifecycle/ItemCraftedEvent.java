package net.tfminecraft.advancedcrafting.lifecycle;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ItemCraftedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final UUID playerUuid;
	private final String recipeId;
	private final String categoryId;

	public ItemCraftedEvent(Player player, UUID playerUuid, String recipeId, String categoryId) {
		this.player = player;
		this.playerUuid = playerUuid;
		this.recipeId = recipeId;
		this.categoryId = categoryId;
	}

	public Player getPlayer() {
		return player;
	}

	public UUID getPlayerUuid() {
		return playerUuid;
	}

	public String getRecipeId() {
		return recipeId;
	}

	public String getCategoryId() {
		return categoryId;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
