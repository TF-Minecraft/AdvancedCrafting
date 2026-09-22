package net.tfminecraft.AdvancedCrafting.Objects.Crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

public class SocketGroup {
	private String id;
	private HashMap<String, List<String>> slots = new HashMap<>();

	public SocketGroup(String key, ConfigurationSection config) {
		id = key;
		ConfigurationSection section = config.getConfigurationSection("slots");
		if(section == null) return;
		for(String quality : section.getKeys(false)) {
			slots.put(quality, new ArrayList<String>(section.getStringList(quality)));
		}
	}

	public String getId() {
		return id;
	}

	public List<String> getSlots(String qualityId) {
		if(qualityId == null) return Collections.emptyList();
		List<String> list = slots.get(qualityId);
		if(list == null) return Collections.emptyList();
		return list;
	}

	public HashMap<String, List<String>> getAllSlots() {
		return slots;
	}
}
