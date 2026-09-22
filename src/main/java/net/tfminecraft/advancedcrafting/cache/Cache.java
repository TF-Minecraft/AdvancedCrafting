package net.tfminecraft.advancedcrafting.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.advancedcrafting.objects.data.PermissionNamespace;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;

public class Cache {
	public static String scrap;
	
	public static String craftingStation;
	public static String alloyStation;
	public static String ingredientStation;

	public static double alloyForgeBaseSuccess = 2.0;
	public static double alloyForgeBonusPerSqrtValue = 4.0;
	public static double alloyForgeMaxSuccess = 85.0;

	public static String brandingTool;

	public static HashMap<IngredientType, List<IngredientType>> combinations = new HashMap<>();

	public static double maxFactor;

	public static double hitOvershootWarnPercent = 30.0;
	public static String hitOvershootWarnMessage = "§cYour over-reliance on %hit% ruins the result further";

	public static boolean debugStatRefresh;

	public static boolean showIngredientStats = true;

	/** Divisors applied to bucket-averaged stats before template factors (e.g. movement_speed: 100). */
	public static Map<String, Double> globalStatOffsets = new HashMap<>();

	public static String permissionPrefix = "professions.";
	public static Map<String, PermissionNamespace> permissionNamespaces = new HashMap<>();

	public static boolean canCombine(IngredientType base, IngredientType type){
		if(base.getId().equalsIgnoreCase(type.getId())) return true;
		if(!combinations.containsKey(base)) return true;
		return combinations.get(base).contains(type);
	}
}
