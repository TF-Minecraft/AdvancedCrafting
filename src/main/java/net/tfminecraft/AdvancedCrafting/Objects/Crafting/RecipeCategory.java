package net.tfminecraft.AdvancedCrafting.Objects.Crafting;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class RecipeCategory {
	private String id;
	private String name;
	private String permission;
	private List<CraftingRecipe> recipes = new ArrayList<>();
	
	public RecipeCategory(String key, ConfigurationSection config) {
		this.id = key;
		this.name = StringFormatter.formatHex(config.getString("name"));
		if(config.contains("permission")) {
			this.permission = config.getString("permission");
		}
		this.permission = "none";
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<CraftingRecipe> getRecipes() {
		return recipes;
	}
	
	public String getPermission() {
		return permission;
	}

	public void addRecipe(CraftingRecipe r) {
		this.recipes.add(r);
	}
}
