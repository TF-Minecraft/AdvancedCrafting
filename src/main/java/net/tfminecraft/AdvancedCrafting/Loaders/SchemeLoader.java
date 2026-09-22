package net.tfminecraft.AdvancedCrafting.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import net.tfminecraft.AdvancedCrafting.Objects.Schemes.ColourScheme;
import net.tfminecraft.AdvancedCrafting.Objects.Schemes.ModelScheme;
import net.tfminecraft.AdvancedCrafting.Objects.Schemes.NamingScheme;

public class SchemeLoader{
	public static HashMap<String, NamingScheme> names = new HashMap<>();
	public static HashMap<String, ColourScheme> colours = new HashMap<>();
	public static HashMap<String, ModelScheme> models = new HashMap<>();
	
	public static HashMap<String, NamingScheme> getNamingSchemes(){
		return names;
	}
	public static HashMap<String, ColourScheme> getColourSchemes(){
		return colours;
	}

	public void loadNamingSchemes(File configFile) {
		
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			NamingScheme o = new NamingScheme(key, config.getConfigurationSection(key));
			names.put(key, o);
		}
	}
	public void loadColourSchemes(File configFile) {
		
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			ColourScheme o = new ColourScheme(key, config.getConfigurationSection(key));
			colours.put(key, o);
		}
	}
	public void loadModelSchemes(File configFile) {
		
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			ModelScheme o = new ModelScheme(key, config.getConfigurationSection(key));
			models.put(key, o);
		}
	}

	public static NamingScheme getNamingSchemeByString(String id) {
		if(!names.containsKey(id)) return null;
		return names.get(id);
	}
	public static ColourScheme getColourSchemeByString(String id) {
		if(!colours.containsKey(id)) return null;
		return colours.get(id);
	}
	public static ModelScheme getModelSchemeByString(String id) {
		if(!models.containsKey(id)) return null;
		return models.get(id);
	}

}
