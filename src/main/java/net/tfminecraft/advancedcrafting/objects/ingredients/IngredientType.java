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
}
