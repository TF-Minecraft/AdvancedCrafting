package net.tfminecraft.AdvancedCrafting.lifecycle;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SmithingHitEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final UUID playerUuid;
	private final String hitId;

	public SmithingHitEvent(Player player, UUID playerUuid, String hitId) {
		this.player = player;
		this.playerUuid = playerUuid;
		this.hitId = hitId;
	}

	public Player getPlayer() {
		return player;
	}

	public UUID getPlayerUuid() {
		return playerUuid;
	}

	public String getHitId() {
		return hitId;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
