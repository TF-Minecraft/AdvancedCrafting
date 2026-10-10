package net.tfminecraft.advancedcrafting.objects.ingredients;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class IngredientType {
	private String id;
	private String name;
	
	public IngredientType(String key, ConfigurationSection config) {
		id = key;
		name = StringFormatter.formatHex(config.getString("name"));
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	// By id: a reload builds new objects, while alloys and open stations still hold the old ones.
	@Override
	public boolean equals(Object obj) {
		return obj instanceof IngredientType other && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
