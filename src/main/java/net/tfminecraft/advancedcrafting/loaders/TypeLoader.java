package net.tfminecraft.advancedcrafting.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.HitType;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;

public class TypeLoader{
	public static HashMap<String, IngredientType> map = new HashMap<>();
	
	public static HashMap<String, HitType> hMap = new HashMap<>();
	
	public static HashMap<String, IngredientType> getIngredientTypes(){
		return map;
	}
	
	public void loadIngredientTypes(File configFile) {
		
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			IngredientType o = new IngredientType(key, config.getConfigurationSection(key));
			map.put(key, o);
		}
	}
	
	public void loadHitTypes(File configFile) {
		
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			HitType o = new HitType(key, config.getConfigurationSection(key));
			hMap.put(key, o);
		}
	}

	public static IngredientType getIngredientTypeByString(String id) {
		if(!map.containsKey(id)) return null;
		return map.get(id);
	}
	
	public static HitType getHitTypeByString(String id) {
		if(!hMap.containsKey(id)) return null;
		return hMap.get(id);
	}
}
