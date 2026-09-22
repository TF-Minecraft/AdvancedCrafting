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
import net.tfminecraft.AdvancedCrafting.Objects.Crafting.Hits.CraftingHit;

public class HitLoader implements LoaderInterface{

	public static HashMap<String, CraftingHit> map = new HashMap<>();
	
	public static HashMap<String, CraftingHit> get(){
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
			CraftingHit o = new CraftingHit(key, config.getConfigurationSection(key));
			map.put(key, o);
		}
	}

	public static CraftingHit getByString(String id) {
		if(map.containsKey(id)) return map.get(id);
		return null;
	}
	public static CraftingHit getByTool(String path) {
		for(String s : map.keySet()) {
			CraftingHit hit = map.get(s);
			if(hit.getTool().equalsIgnoreCase(path)) return hit;
		}
		return null;
	}
}
