package net.tfminecraft.advancedcrafting.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.objects.stats.StatTemplate;
import net.tfminecraft.advancedcrafting.utils.RevisionTracker;

public class StatTemplateLoader implements LoaderInterface {
	private static final Map<String, StatTemplate> map = new LinkedHashMap<>();
	private static final List<StatTemplate> ordered = new ArrayList<>();

	public static Map<String, StatTemplate> get() {
		return map;
	}

	public static List<StatTemplate> getAll() {
		return Collections.unmodifiableList(ordered);
	}

	@Override
	public void load(File configFile) {
		map.clear();
		ordered.clear();

		FileConfiguration config = new YamlConfiguration();
		try {
			config.load(configFile);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}

		for (String key : config.getKeys(false)) {
			StatTemplate template = new StatTemplate(key, config.getConfigurationSection(key));
			String hash = RevisionTracker.sha256(template.buildRevisionContent());
			int revision = AdvancedCrafting.getRevisionTracker().resolveStatTemplate(key, hash);
			template.setRevision(revision);
			map.put(key.toLowerCase(), template);
			ordered.add(template);
		}
	}

	public static StatTemplate getByString(String id) {
		if (id == null) {
			return null;
		}
		return map.get(id.toLowerCase());
	}
}
