package net.tfminecraft.advancedcrafting.lifecycle;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AlloyDiscoveredEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final UUID playerUuid;
	private final String alloyId;

	public AlloyDiscoveredEvent(Player player, UUID playerUuid, String alloyId) {
		this.player = player;
		this.playerUuid = playerUuid;
		this.alloyId = alloyId;
	}

	public Player getPlayer() {
		return player;
	}

	public UUID getPlayerUuid() {
		return playerUuid;
	}

	public String getAlloyId() {
		return alloyId;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
