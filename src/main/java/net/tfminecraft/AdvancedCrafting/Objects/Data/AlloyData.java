package net.tfminecraft.AdvancedCrafting.Objects.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import net.tfminecraft.AdvancedCrafting.Objects.Crafting.Hits.CraftingHit;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.IngredientType;
import net.tfminecraft.AdvancedCrafting.Objects.Schemes.ColourScheme;
import net.tfminecraft.AdvancedCrafting.Objects.Schemes.ModelScheme;

public class AlloyData {
	private ColourScheme colourScheme;
	private int model;
	private IngredientType type;
	
	private ModelScheme modelScheme;
	private StatData stats;
	
	private HashMap<CraftingHit, Integer> hits = new HashMap<>();

	private String xp;
	private AlloyRecipe recipe;
	private int tier;
	private String statMergeBucketId;

	public AlloyData(Ingredient base, StatData stats, HashMap<CraftingHit, Integer> hits, String xp) {
		colourScheme = base.getIngredientData().getScheme().getColourScheme();
		model = colourScheme.randomModel();
		this.stats = stats;
		this.type = base.getIngredientData().getType();
		this.modelScheme = base.getIngredientData().getModelScheme();
		this.hits = hits;
		this.xp = xp;
		this.tier = base.getIngredientData().hasTier() ? base.getIngredientData().getTier() : 1;
		this.statMergeBucketId = base.getIngredientData().getStatMergeBucketId();
	}

	public AlloyData(ColourScheme colourScheme, int model, IngredientType type, ModelScheme scheme,
			StatData stats, HashMap<CraftingHit, Integer> hits, String xp,
			AlloyRecipe recipe, int tier, String statMergeBucketId) {
		this.colourScheme = colourScheme;
		this.model = model;
		this.type = type;
		this.stats = stats;
		this.modelScheme = scheme;
		this.hits = hits;
		this.xp = xp;
		this.recipe = recipe;
		this.tier = tier;
		if (statMergeBucketId != null && !statMergeBucketId.isBlank()) {
			this.statMergeBucketId = statMergeBucketId.trim().toLowerCase();
		} else if (type != null) {
			this.statMergeBucketId = type.getId().toLowerCase();
		}
	}

	public boolean hasXP() {
		return xp != null;
	}

	public String getXP() {
		return xp;
	}

	public ColourScheme getColourScheme() {
		return colourScheme;
	}

	public int getModel() {
		return model;
	}

	public ModelScheme getModelScheme() {
		return modelScheme;
	}

	public StatData getStatData() {
		return stats;
	}

	public IngredientType getType() {
		return type;
	}

	public String getStatMergeBucketId() {
		return statMergeBucketId;
	}

	public HashMap<CraftingHit, Integer> getHits() {
		return hits;
	}

	public AlloyRecipe getRecipe() {
		return recipe;
	}

	public void setRecipe(AlloyRecipe recipe) {
		this.recipe = recipe;
	}

	public int getTier() {
		return tier;
	}

	public String buildRevisionContent() {
		StringBuilder sb = new StringBuilder();
		sb.append("xp=").append(xp != null ? xp : "").append(';');
		List<String> statParts = this.stats.getModifiers().stream()
				.map(m -> m.getType() + "(" + m.getAmount() + ")")
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.collect(Collectors.toList());
		sb.append("stats=").append(String.join(",", statParts)).append(';');
		List<String> hitParts = hits.entrySet().stream()
				.map(e -> e.getKey().getId() + "." + e.getValue())
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.collect(Collectors.toList());
		sb.append("hits=").append(String.join(",", hitParts)).append(';');
		sb.append("tier=").append(tier).append(';');
		sb.append("statMergeBucketId=").append(statMergeBucketId != null ? statMergeBucketId : "");
		return sb.toString();
	}
}
