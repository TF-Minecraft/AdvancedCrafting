package net.tfminecraft.advancedcrafting.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;
import net.tfminecraft.advancedcrafting.utils.RevisionTracker;

public class IngredientLoader implements LoaderInterface{
	public static List<Ingredient> oList = new ArrayList<>();
	
	public static List<Ingredient> get(){
		return oList;
	}
	
	@Override
	public void load(File configFile) {
		oList.clear();
		
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			Ingredient o = new Ingredient(key, config.getConfigurationSection(key));
			String hash = RevisionTracker.sha256(o.getIngredientData().buildRevisionContent());
			int revision = AdvancedCrafting.getRevisionTracker().resolveIngredient(key, hash);
			o.setRevision(revision);
			oList.add(o);
		}
	}

	public static Ingredient getByString(String id) {
		for(Ingredient o : oList) {
			if(o.getId().equalsIgnoreCase(id)) return o;
		}
		return null;
	}
}
