package net.tfminecraft.advancedcrafting;

import java.io.File;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.advancedcrafting.database.AlloyDatabase;
import net.tfminecraft.advancedcrafting.database.AlloyRecipeStore;
import net.tfminecraft.advancedcrafting.database.Database;
import net.tfminecraft.advancedcrafting.loaders.CategoryLoader;
import net.tfminecraft.advancedcrafting.loaders.ConfigLoader;
import net.tfminecraft.advancedcrafting.loaders.IngredientLoader;
import net.tfminecraft.advancedcrafting.loaders.QualityLoader;
import net.tfminecraft.advancedcrafting.loaders.ConversionLoader;
import net.tfminecraft.advancedcrafting.loaders.HitLoader;
import net.tfminecraft.advancedcrafting.loaders.RecipeLoader;
import net.tfminecraft.advancedcrafting.loaders.SchemeLoader;
import net.tfminecraft.advancedcrafting.loaders.SocketGroupLoader;
import net.tfminecraft.advancedcrafting.loaders.StatTemplateLoader;
import net.tfminecraft.advancedcrafting.loaders.StationLoader;
import net.tfminecraft.advancedcrafting.loaders.TypeLoader;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.managers.CommandManager;
import net.tfminecraft.advancedcrafting.managers.CraftRefreshListener;
import net.tfminecraft.advancedcrafting.managers.CraftingManager;
import net.tfminecraft.advancedcrafting.managers.IngredientManager;
import net.tfminecraft.advancedcrafting.managers.MMOItemRebuildListener;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingStation;
import net.tfminecraft.advancedcrafting.utils.RevisionTracker;
import net.tfminecraft.advancedcrafting.lifecycle.CraftLifecycle;
import net.tfminecraft.advancedcrafting.lifecycle.PlayerAlloyForgeTracker;

public class AdvancedCrafting extends JavaPlugin{
	
	public static AdvancedCrafting plugin;
	private static final RevisionTracker revisionTracker = new RevisionTracker();
	private final CategoryLoader categoryLoader = new CategoryLoader();
	private final RecipeLoader recipeLoader = new RecipeLoader();
	private final StationLoader stationLoader = new StationLoader();
	private final ConfigLoader configLoader = new ConfigLoader();
	private final TypeLoader typeLoader = new TypeLoader();
	private final IngredientLoader ingredientLoader = new IngredientLoader();
	private final ConversionLoader conversionLoader = new ConversionLoader();
	private final SchemeLoader schemeLoader = new SchemeLoader();
	private final HitLoader hitLoader = new HitLoader();
	private final QualityLoader qualityLoader = new QualityLoader();
	private final SocketGroupLoader socketGroupLoader = new SocketGroupLoader();
	private final StatTemplateLoader statTemplateLoader = new StatTemplateLoader();
	
	private final CommandManager commandManager = new CommandManager();
	private final CraftingManager craftingManager = new CraftingManager();
	private static final AlloyManager alloyManager = new AlloyManager();
	private final IngredientManager ingredientManager = new IngredientManager();
	private final CraftRefreshListener craftRefreshListener = new CraftRefreshListener();
	private final MMOItemRebuildListener mmoItemRebuildListener = new MMOItemRebuildListener();
	
	private final AlloyDatabase alloyDatabase = new AlloyDatabase();
	private AlloyRecipeStore alloyRecipeStore;
	private final Database db = new Database();
	
	
	@Override
	public void onEnable() {
		plugin = this;
		createFolders();
		alloyRecipeStore = new AlloyRecipeStore(new File(getDataFolder(), "data/alloy-recipes"));
		createConfigs();
		revisionTracker.load(getDataFolder());
		loadConfigs();
		CraftLifecycle.init(new PlayerAlloyForgeTracker(new File(getDataFolder(), "data")));
		registerListeners();
		craftRefreshListener.start(this);
		getCommand(commandManager.cmd1).setExecutor(commandManager);
		getCommand(commandManager.cmd1).setTabCompleter(commandManager);
		getCommand(commandManager.cmd2).setExecutor(commandManager);
		getCommand(commandManager.cmd2).setTabCompleter(commandManager);
		alloyDatabase.loadAlloys();
		revisionTracker.flush();
		startManagers();
	}
	
	@Override
	public void onDisable() {
		craftRefreshListener.stop();
		revisionTracker.flush();
		db.clear();
		for(CraftingStation s : craftingManager.getStations()) {
			db.saveStation(s);
		}
	}
	
	public void registerListeners() {
		getServer().getPluginManager().registerEvents(commandManager, this);
		getServer().getPluginManager().registerEvents(craftingManager, this);
		getServer().getPluginManager().registerEvents(alloyManager, this);
		getServer().getPluginManager().registerEvents(ingredientManager, this);
		getServer().getPluginManager().registerEvents(craftRefreshListener, this);
		getServer().getPluginManager().registerEvents(mmoItemRebuildListener, this);
	}
	public void loadConfigs() {
		/*
		stationLoader.load(new File(getDataFolder(), "stations.yml"));
		*/
		File folder = new File(getDataFolder(), "colour-schemes");
    	for (final File file : folder.listFiles()) {
    		if(!file.isDirectory()) {
    			schemeLoader.loadColourSchemes(file);
    		}
    	}
    	folder = new File(getDataFolder(), "naming-schemes");
    	for (final File file : folder.listFiles()) {
    		if(!file.isDirectory()) {
    			schemeLoader.loadNamingSchemes(file);
    		}
    	}
    	folder = new File(getDataFolder(), "model-schemes");
    	for (final File file : folder.listFiles()) {
    		if(!file.isDirectory()) {
    			schemeLoader.loadModelSchemes(file);
    		}
    	}
    	categoryLoader.load(new File(getDataFolder(), "recipe-categories.yml"));
    	statTemplateLoader.load(new File(getDataFolder(), "stats.yml"));
    	// Must load before the recipes, which validate their socket-group on construction
    	socketGroupLoader.load(new File(getDataFolder(), "socket-groups.yml"));
    	folder = new File(getDataFolder(), "recipes");
    	for (final File file : folder.listFiles()) {
    		if(!file.isDirectory()) {
    			recipeLoader.load(file);
    		}
    	}
    	
		typeLoader.loadIngredientTypes(new File(getDataFolder(), "ingredient-types.yml"));
		typeLoader.loadHitTypes(new File(getDataFolder(), "hit-types.yml"));
		hitLoader.load(new File(getDataFolder(), "crafting-hits.yml"));
		qualityLoader.load(new File(getDataFolder(), "qualities.yml"));
		ingredientLoader.load(new File(getDataFolder(), "ingredients.yml"));
		configLoader.load(new File(getDataFolder(), "config.yml"));
		
	}
	public void startManagers() {
		ingredientManager.set(conversionLoader.load(new File(getDataFolder(), "conversions.yml")));
		craftingManager.set(db.loadStations());
		alloyManager.start();
	}
	public void createFolders() {
		if (!getDataFolder().exists()) getDataFolder().mkdir();
		File subFolder = new File(getDataFolder(), "naming-schemes");
		if(!subFolder.exists()) subFolder.mkdir();
		subFolder = new File(getDataFolder(), "colour-schemes");
		if(!subFolder.exists()) subFolder.mkdir();
		subFolder = new File(getDataFolder(), "recipes");
		if(!subFolder.exists()) subFolder.mkdir();
		subFolder = new File(getDataFolder(), "data");
		if(!subFolder.exists()) subFolder.mkdir();
		subFolder = new File(getDataFolder(), "data/players");
		if(!subFolder.exists()) subFolder.mkdir();
		subFolder = new File(getDataFolder(), "data/stations");
		if(!subFolder.exists()) subFolder.mkdir();
		subFolder = new File(getDataFolder(), "data/alloys");
		if(!subFolder.exists()) subFolder.mkdir();
		subFolder = new File(getDataFolder(), "data/alloy-recipes");
		if(!subFolder.exists()) subFolder.mkdir();
	}
	
	public void createConfigs() {
		String[] files = {
				"recipe-categories.yml",
				"config.yml",
				"ingredients.yml",
				"ingredient-types.yml",
				"hit-types.yml",
				"crafting-hits.yml",
				"conversions.yml",
				"qualities.yml",
				"socket-groups.yml",
				"stats.yml",
				};
		for(String s : files) {
			File newConfigFile = new File(getDataFolder(), s);
	        if (!newConfigFile.exists()) {
	        	newConfigFile.getParentFile().mkdirs();
	            saveResource(s, false);
	        }
		}
	}
	
	public void reload() {
		loadConfigs();
		revisionTracker.flush();
	}
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public void reloadMessage(Player p) {
		p.sendMessage(ChatColor.GREEN + "[AdvancedCrafting]" + ChatColor.YELLOW + " Reloading plugin...");
		reload();
		p.sendMessage(ChatColor.GREEN + "[AdvancedCrafting]" + ChatColor.YELLOW + " Reloading complete!");
	}

	public static AlloyManager getAlloyManager() {
		return alloyManager;
	}

	public static RevisionTracker getRevisionTracker() {
		return revisionTracker;
	}

	public static AlloyRecipeStore getAlloyRecipeStore() {
		return plugin.alloyRecipeStore;
	}

	public static CraftingManager getCraftingManager() {
		return plugin.craftingManager;
	}

	public IngredientManager getIngredientManager() {
		return ingredientManager;
	}
}
