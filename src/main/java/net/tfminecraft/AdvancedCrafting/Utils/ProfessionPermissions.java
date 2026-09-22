package net.tfminecraft.AdvancedCrafting.Utils;

import org.bukkit.entity.Player;

import net.tfminecraft.AdvancedCrafting.Cache.Cache;
import net.tfminecraft.AdvancedCrafting.Loaders.IngredientLoader;
import net.tfminecraft.AdvancedCrafting.Managers.AlloyManager;
import net.tfminecraft.AdvancedCrafting.Objects.Alloys.Alloy;
import net.tfminecraft.AdvancedCrafting.Objects.Data.IngredientData;
import net.tfminecraft.AdvancedCrafting.Objects.Data.PermissionNamespace;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;

public final class ProfessionPermissions {
	private static final int MIN_TIER = 1;
	private static final int MAX_TIER = 4;

	private ProfessionPermissions() {
	}

	public static String flatPermission(String permissionKey) {
		return Cache.permissionPrefix + permissionKey.toLowerCase();
	}

	public static boolean hasIngredientPerm(Player player, String permissionKey) {
		if (player == null || permissionKey == null || permissionKey.isBlank()) {
			return false;
		}
		return player.hasPermission(flatPermission(permissionKey));
	}

	public static String fullPermission(String namespace, int tier) {
		return Cache.permissionPrefix + namespace.toLowerCase() + "_" + tier;
	}

	public static boolean hasAnyNamespacePerm(Player player, String namespace) {
		if (player == null || namespace == null || namespace.isBlank()) {
			return false;
		}
		String ns = namespace.toLowerCase();
		for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
			if (player.hasPermission(fullPermission(ns, tier))) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasExactTierPerm(Player player, String namespace, int tier) {
		if (player == null || namespace == null || namespace.isBlank() || tier <= 0) {
			return false;
		}
		return player.hasPermission(fullPermission(namespace, tier));
	}

	public static String getDisplayName(String namespace) {
		if (namespace == null) {
			return "";
		}
		PermissionNamespace entry = Cache.permissionNamespaces.get(namespace.toLowerCase());
		if (entry != null) {
			return entry.getDisplay();
		}
		return namespace;
	}

	public static int resolveTier(Ingredient ingredient) {
		if (ingredient == null) {
			return 0;
		}
		return resolveTier(ingredient.getIngredientData());
	}

	public static int resolveIngredientTier(Ingredient ingredient) {
		if (ingredient == null) {
			return 0;
		}
		return resolveIngredientTier(ingredient.getIngredientData());
	}

	public static int resolveIngredientTier(IngredientData data) {
		if (data == null || !data.hasTier()) {
			return 0;
		}
		return data.getTier();
	}

	public static boolean canUseIngredient(Player player, Ingredient ingredient) {
		if (player == null || ingredient == null) {
			return false;
		}
		IngredientData data = ingredient.getIngredientData();
		if (data == null) {
			return false;
		}
		if (!data.hasPermission()) {
			return true;
		}
		return hasIngredientPerm(player, data.getPermission());
	}

	public static int resolveTier(IngredientData data) {
		if (data == null || !data.canBeBase() || !data.hasTier()) {
			return 0;
		}
		return data.getTier();
	}

	public static int resolveTier(Alloy alloy) {
		if (alloy == null) {
			return 0;
		}
		return alloy.getData().getTier();
	}

	public static int resolveTier(String ingredientId) {
		Ingredient ing = IngredientLoader.getByString(ingredientId);
		return resolveTier(ing);
	}

	public static int resolveAlloyTier(String alloyId) {
		Alloy alloy = AlloyManager.getAlloyById(alloyId);
		return resolveTier(alloy);
	}

	public static String missingIngredientPermissionMessage(String permissionKey) {
		return "§cYou need the " + getDisplayName(permissionKey) + " permission to use this material.";
	}

	public static String missingNamespaceMessage(String namespace) {
		return "§cYou need at least one of the " + getDisplayName(namespace) + " permissions.";
	}

	public static String missingExactTierMessage(String namespace, int tier) {
		return "§cYou need the " + getDisplayName(namespace) + " tier " + tier + " permission to use this material.";
	}
}
