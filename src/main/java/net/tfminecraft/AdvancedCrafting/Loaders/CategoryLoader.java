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

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.RecipeCategory;

public class CategoryLoader implements LoaderInterface{

	public static HashMap<String, RecipeCategory> categories = new HashMap<>();
	
	public static HashMap<String, RecipeCategory> get(){
		return categories;
	}
	
	@Override
	public void load(File configFile) {
		
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			RecipeCategory o = new RecipeCategory(key, config.getConfigurationSection(key));
			categories.put(key, o);
		}
	}

	public static RecipeCategory getByString(String id) {
		if(categories.containsKey(id)) return categories.get(id);
		return null;
	}

}
