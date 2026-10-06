package net.tfminecraft.advancedcrafting.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.CraftingHit;

public class HitLoader implements LoaderInterface{

	// Keeps config order so the branding status lists hits as written.
	public static HashMap<String, CraftingHit> map = new LinkedHashMap<>();
	
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
            return;
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		// Rebuilt on reload so the order follows the file and removed hits disappear.
		map.clear();

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
