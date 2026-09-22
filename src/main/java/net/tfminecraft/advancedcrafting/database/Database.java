package net.tfminecraft.advancedcrafting.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.tfminecraft.advancedcrafting.loaders.HitLoader;
import net.tfminecraft.advancedcrafting.loaders.RecipeLoader;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingStation;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.CraftingHit;

public class Database {
	private JSONObject json; // org.json.simple
    JSONParser parser = new JSONParser();
    public HashMap<Location, CraftingStation> loadStations() {
    	HashMap<Location, CraftingStation> map = new HashMap<>();
    	File folder = new File("plugins/AdvancedCrafting/data/stations");
    	for(final File file : folder.listFiles()) {
    		if(!file.isDirectory()) {
    			try {
    				json = (JSONObject) parser.parse(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    				Location loc = new Location(Bukkit.getServer().getWorld((String) json.get("world")), (Double) json.get("xPos"),(Double) json.get("yPos"),(Double) json.get("zPos"));
    				CraftingRecipe r = RecipeLoader.getByString((String) json.get("recipe"));
    				HashMap<String, Integer> materials = new HashMap<>();
    				int i = 0;
    				JSONArray matArray = (JSONArray) json.get("materials");
    				while(i < matArray.size()) {
    					String s = matArray.get(i).toString();
    					String st = s.split("\\(")[0];
    					int amount = Integer.parseInt(s.split("\\(")[1].replace(")", ""));
    					materials.put(st, amount);
    					i++;
    				}
    				HashMap<CraftingHit, Integer> hits = new HashMap<>();
    				i = 0;
    				JSONArray hitArray = (JSONArray) json.get("hits");
    				while(i < hitArray.size()) {
    					String s = hitArray.get(i).toString();
    					String hit = s.split("\\.")[0];
    					int amount = Integer.parseInt(s.split("\\.")[1]);
    					hits.put(HitLoader.getByString(hit), amount);
    					i++;
    				}
    				map.put(loc, new CraftingStation(loc, r, materials, hits));
    				
    			} catch (Exception ex) {
    				ex.printStackTrace();
    			}
    		}
    	}
    	return map;
	}
    public void clear() {
    	File folder = new File("plugins/AdvancedCrafting/data/stations");
    	for(final File file : folder.listFiles()) {
    		if(!file.isDirectory()) {
    			file.delete();
    		}
    	}
    }
	@SuppressWarnings("unchecked")
	public void saveStation(CraftingStation s) {
		if(!s.hasRecipe()) return;
		try {
			File file = new File("plugins/AdvancedCrafting/data/stations",UUID.randomUUID().toString()+".json");
			if(file.exists() == true) {
				file.delete();
			}
			file.createNewFile();
        	PrintWriter pw = new PrintWriter(file, "UTF-8");
        	pw.print("{");
        	pw.print("}");
        	pw.flush();
        	pw.close();
            HashMap<String, Object> defaults = new HashMap<String, Object>();
        	json = (JSONObject) parser.parse(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        	defaults.put("world", s.getLoc().getWorld().toString().replace("CraftWorld{name=", "").replace("}", ""));
        	defaults.put("xPos", s.getLoc().getX());
        	defaults.put("yPos", s.getLoc().getY());
        	defaults.put("zPos", s.getLoc().getZ());
        	defaults.put("recipe", s.getRecipe().getId());
        	JSONArray matArray = new JSONArray();
        	for(String m : s.getCurrentMaterials().keySet()) {
        		String mat = m+ "("+ s.getCurrentMaterials().get(m)+")";
        		matArray.add(mat);
        	}
        	defaults.put("materials", matArray);
        	JSONArray hitArray = new JSONArray();
        	for(CraftingHit h : s.getHits().keySet()) {
        		String hit = h.getId()+"."+s.getHits().get(h).getCurrent();
        		hitArray.add(hit);
        	}
        	defaults.put("hits", hitArray);
        	save(file, defaults);
        } catch (Throwable ex) {
			ex.printStackTrace();
        }
    }
	@SuppressWarnings("unchecked")
    public boolean save(File file, HashMap<String, Object> defaults) {
      try {
    	  JSONObject toSave = new JSONObject();
      
        for (String s : defaults.keySet()) {
          Object o = defaults.get(s);
          if (o instanceof String) {
            toSave.put(s, getString(s, defaults));
          } else if (o instanceof Double) {
            toSave.put(s, getDouble(s, defaults));
          } else if (o instanceof Integer) {
            toSave.put(s, getInteger(s, defaults));
          } else if (o instanceof JSONObject) {
            toSave.put(s, getObject(s, defaults));
          } else if (o instanceof JSONArray) {
            toSave.put(s, getArray(s, defaults));
          }
        }
      
        TreeMap<String, Object> treeMap = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        treeMap.putAll(toSave);
      
       Gson g = new GsonBuilder().setPrettyPrinting().create();
       String prettyJsonString = g.toJson(treeMap);
      
        FileWriter fw = new FileWriter(file);
        fw.write(prettyJsonString);
        fw.flush();
        fw.close();
      
        return true;
      } catch (Exception ex) {
        ex.printStackTrace();
        return false;
      }
    }
    
    public String getRawData(String key, HashMap<String, Object> defaults) {
        return json.containsKey(key) ? json.get(key).toString()
           : (defaults.containsKey(key) ? defaults.get(key).toString() : key);
      }
    
      public String getString(String key, HashMap<String, Object> defaults) {
        return ChatColor.translateAlternateColorCodes('&', getRawData(key, defaults));
      }

      public boolean getBoolean(String key, HashMap<String, Object> defaults) {
        return Boolean.valueOf(getRawData(key, defaults));
      }

      public double getDouble(String key, HashMap<String, Object> defaults) {
        try {
          return Double.parseDouble(getRawData(key, defaults));
        } catch (Exception ex) { }
        return -1;
      }

      public double getInteger(String key, HashMap<String, Object> defaults) {
        try {
          return Integer.parseInt(getRawData(key, defaults));
        } catch (Exception ex) { }
        return -1;
      }
     
      public JSONObject getObject(String key, HashMap<String, Object> defaults) {
         return json.containsKey(key) ? (JSONObject) json.get(key)
           : (defaults.containsKey(key) ? (JSONObject) defaults.get(key) : new JSONObject());
      }
     
      public JSONArray getArray(String key, HashMap<String, Object> defaults) {
    	     return json.containsKey(key) ? (JSONArray) json.get(key)
    	       : (defaults.containsKey(key) ? (JSONArray) defaults.get(key) : new JSONArray());
      }
}
