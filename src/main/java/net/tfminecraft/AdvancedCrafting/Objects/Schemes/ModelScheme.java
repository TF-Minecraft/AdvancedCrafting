package net.tfminecraft.AdvancedCrafting.Objects.Schemes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

public class ModelScheme {
	private String id;
	
	private List<String> models = new ArrayList<>();
	
	public ModelScheme(String key, ConfigurationSection config) {
		this.id = key;
		models = config.getStringList("models");
	}

	public String getId() {
		return id;
	}

	public List<String> getModels() {
		return models;
	}
	
	public String getModel(String key) {
		for(String s : models) {
			if(s.split("\\(")[0].equalsIgnoreCase(key)) return s.split("\\(")[1].replace(")", "");
		}
		return null;
	}
}
