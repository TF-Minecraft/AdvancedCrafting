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
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;

public class RecipeLoader implements LoaderInterface{

	public static HashMap<String, CraftingRecipe> map = new HashMap<>();
	
	public static HashMap<String, CraftingRecipe> get(){
		return map;
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
			CraftingRecipe o = new CraftingRecipe(key, config.getConfigurationSection(key));
			map.put(key, o);
		}
	}

	public static CraftingRecipe getByString(String id) {
		if(map.containsKey(id)) return map.get(id);
		return null;
	}


}
