package net.tfminecraft.advancedcrafting.objects.crafting;

import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.advancedcrafting.loaders.CategoryLoader;
import net.tfminecraft.advancedcrafting.loaders.SocketGroupLoader;
import net.tfminecraft.advancedcrafting.loaders.StatTemplateLoader;
import net.tfminecraft.advancedcrafting.objects.stats.StatTemplate;

public class CraftingRecipe {
	private String id;
	private String name;
	private String template;
	private String iconPath;
	
	private String type;
	private String mainType;
	private String modelType;
	private String socketGroupId;
	private String statTemplateId;
	
	private HashMap<String, Integer> recipe = new HashMap<>();

	private String permissionNamespace;
	private String categoryId;

	public CraftingRecipe(String key, ConfigurationSection config) {
		this.id = key;
		this.template = config.getString("template");
		this.iconPath = config.getString("icon");
		if (iconPath != null && !iconPath.isBlank() && !iconPath.contains(".")) {
			Bukkit.getLogger().warning("AC: Recipe " + key + " icon must be a TLibs path (e.g. ia.tfmc:item_id), got: "
					+ iconPath);
		}
		this.name = StringFormatter.formatHex(config.getString("name"));
		this.type = config.getString("type");
		this.mainType = config.getString("main-type", "metal");
		this.modelType = config.getString("model-type", "none");
		this.socketGroupId = config.getString("socket-group", SocketGroupLoader.DEFAULT_GROUP);
		if (SocketGroupLoader.getByString(socketGroupId) == null) {
			Bukkit.getLogger().warning("AC: Recipe " + key + " references unknown socket-group: " + socketGroupId);
		}
		statTemplateId = config.getString("stat-template");
		if (statTemplateId == null) {
			Bukkit.getLogger().warning("AC: Recipe " + key + " is missing stat-template");
		} else if (StatTemplateLoader.getByString(statTemplateId) == null) {
			Bukkit.getLogger().warning("AC: Recipe " + key + " references unknown stat-template: " + statTemplateId);
		}
		categoryId = config.getString("category");
		CategoryLoader.getByString(categoryId).addRecipe(this);
		for(String r : config.getStringList("recipe")) {
			String recipeId = r.split("\\.")[0];
			int amount = Integer.parseInt(r.split("\\.")[1]);
			recipe.put(recipeId, amount);
		}

		permissionNamespace = config.getString("permission-namespace");
		if (permissionNamespace != null) {
			permissionNamespace = permissionNamespace.toLowerCase();
		}
	}

	public String getModelType() {
		return modelType;
	}

	public String getSocketGroupId() {
		return socketGroupId;
	}

	public String getMainType() {
		return mainType;
	}

	public String getPermissionNamespace() {
		return permissionNamespace;
	}

	public boolean hasPermissionNamespace() {
		return permissionNamespace != null && !permissionNamespace.isBlank();
	}

	public String getStatTemplateId() {
		return statTemplateId;
	}

	public StatTemplate getStatTemplate() {
		return StatTemplateLoader.getByString(statTemplateId);
	}
	
	public String getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}

	public String getCleanedName() {
		return new String(name).replace("%material% ", "");
	}

	public String getTemplate() {
		return template;
	}

	public String getIconPath() {
		return iconPath;
	}

	public String resolveMenuIconPath() {
		if (iconPath != null && !iconPath.isBlank()) {
			return iconPath;
		}
		return "m." + template;
	}
	
	public String getType() {
		return type;
	}

	public HashMap<String, Integer> getRecipe() {
		return recipe;
	}

	public String getCategoryId() {
		return categoryId;
	}
}
