package net.tfminecraft.AdvancedCrafting.Objects.Schemes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

public class ColourScheme {
	private String id;
	
	private String item;
	private List<Integer> models = new ArrayList<>();
	private List<String> hexCodes = new ArrayList<>();
	
	public ColourScheme(String key, ConfigurationSection config) {
		this.id = key;
		item = config.getString("item", "v.IRON_INGOT");
		models = config.getIntegerList("models");
		hexCodes = config.getStringList("colours");
	}

	public String getItem() {
		return item;
	}

	public String getId() {
		return id;
	}

	public List<Integer> getModels() {
		return models;
	}

	public List<String> getHexCodes() {
		return hexCodes;
	}
	
	public int randomModel() {
		int i = (int) Math.round(Math.random()*(models.size()-1));
		return models.get(i);
	}
	public String randomColour() {
		int i = (int) Math.round(Math.random()*(hexCodes.size()-1));
		return hexCodes.get(i);
	}
}
