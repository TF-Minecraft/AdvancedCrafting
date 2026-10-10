package net.tfminecraft.advancedcrafting.objects.crafting.hits;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.advancedcrafting.loaders.TypeLoader;

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

	// By id: a reload builds new objects, while alloys and open stations still hold the old ones.
	@Override
	public boolean equals(Object obj) {
		return obj instanceof CraftingHit other && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
