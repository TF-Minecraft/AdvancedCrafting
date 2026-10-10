package net.tfminecraft.advancedcrafting.objects.crafting.hits;

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

	// By id: a reload builds new objects, while open stations still hold the old ones.
	@Override
	public boolean equals(Object obj) {
		return obj instanceof HitType other && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
