package net.tfminecraft.AdvancedCrafting.Loaders;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;

public class ConversionLoader{
	
	public HashMap<String, Ingredient> load(File configFile) {
		HashMap<String, Ingredient> map = new HashMap<>();
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
		
		for(String key : config.getStringList("conversions")) {
			String path = key.split("\\(")[0];
			Ingredient i = IngredientLoader.getByString(key.split("\\(")[1].replace(")", ""));
			map.put(path, i);
		}
		return map;
	}

}
