package net.tfminecraft.advancedcrafting.objects.schemes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.advancedcrafting.loaders.SchemeLoader;

public class NamingScheme {
	private String id;
	private ColourScheme colourScheme;
	
	private List<String> names = new ArrayList<>();
	
	public NamingScheme(String key, ConfigurationSection config) {
		id = key;
		names = config.getStringList("names");
		colourScheme = SchemeLoader.getColourSchemeByString(config.getString("colour-scheme"));
	}

	public String getId() {
		return id;
	}

	public ColourScheme getColourScheme() {
		return colourScheme;
	}

	public List<String> getNames() {
		return names;
	}
}
