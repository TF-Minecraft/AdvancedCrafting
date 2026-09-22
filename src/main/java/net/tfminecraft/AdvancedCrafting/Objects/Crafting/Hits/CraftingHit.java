package net.tfminecraft.AdvancedCrafting.Objects.Crafting.Hits;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.AdvancedCrafting.Loaders.TypeLoader;

public class CraftingHit {
	private String id;
	private String tool;
	private String name;
	private HitType type;
	
	public CraftingHit(String key, ConfigurationSection config) {
		id = key;
		tool = config.getString("tool");
		name = config.getString("name");
		type = TypeLoader.getHitTypeByString(config.getString("type"));
	}

	public String getId() {
		return id;
	}

	public String getTool() {
		return tool;
	}

	public String getName() {
		return name;
	}

	public HitType getType() {
		return type;
	}
	
	
}
