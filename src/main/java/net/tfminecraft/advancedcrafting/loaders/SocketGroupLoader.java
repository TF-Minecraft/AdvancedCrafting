package net.tfminecraft.advancedcrafting.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.tlibs.socket.SocketTierRegistry;
import net.tfminecraft.advancedcrafting.objects.crafting.SocketGroup;

public class SocketGroupLoader implements LoaderInterface{

	public static final String DEFAULT_GROUP = "gemstones";

	public static HashMap<String, SocketGroup> map = new HashMap<>();

	public static HashMap<String, SocketGroup> get(){
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

		map.clear();
		for(String key : list) {
			SocketGroup o = new SocketGroup(key, config.getConfigurationSection(key));
			map.put(key, o);
			validate(o);
		}
		if(!map.containsKey(DEFAULT_GROUP)) {
			Bukkit.getLogger().warning("AC: socket-groups.yml is missing the default group " + DEFAULT_GROUP);
		}
	}

	// A colour TLibs does not know about produces a socket no gem or rune can ever fill,
	// and SocketTierRegistry rejects it silently, so catch the typo at load instead.
	private void validate(SocketGroup group) {
		for(String quality : group.getAllSlots().keySet()) {
			for(String colour : group.getSlots(quality)) {
				if(SocketTierRegistry.getGroup(colour) == null) {
					Bukkit.getLogger().warning("AC: Socket group " + group.getId() + " (" + quality
							+ ") uses colour '" + colour + "' which is not registered in the TLibs socket-tier-groups");
				}
			}
		}
	}

	public static SocketGroup getByString(String id) {
		if(map.containsKey(id)) return map.get(id);
		return null;
	}

}
