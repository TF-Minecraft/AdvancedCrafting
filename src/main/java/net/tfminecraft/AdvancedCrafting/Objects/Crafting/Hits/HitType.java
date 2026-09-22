package net.tfminecraft.AdvancedCrafting.Objects.Crafting.Hits;

import org.bukkit.configuration.ConfigurationSection;

public class HitType {
	private String id;
	private String name;
	
	public HitType(String key, ConfigurationSection config) {
		id = key;
		name = config.getString("name");
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
