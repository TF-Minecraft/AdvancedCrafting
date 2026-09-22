package net.tfminecraft.AdvancedCrafting.Objects.Data;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.tfminecraft.AdvancedCrafting.Objects.Alloys.AlloyStation;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;

public class AlloyRecipe {
	private final String baseId;
	private final List<String> catalystIds;

	public AlloyRecipe(String baseId, List<String> catalystIds) {
		this.baseId = baseId.toLowerCase();
		List<String> sorted = new ArrayList<>(catalystIds);
		for (int i = 0; i < sorted.size(); i++) {
			sorted.set(i, sorted.get(i).toLowerCase());
		}
		Collections.sort(sorted);
		this.catalystIds = Collections.unmodifiableList(sorted);
	}

	public static AlloyRecipe fromStation(AlloyStation station) {
		if (station.getBaseItem() == null) {
			return null;
		}
		List<String> catalysts = new ArrayList<>();
		for (Ingredient catalyst : station.getCatalysts()) {
			catalysts.add(catalyst.getId());
		}
		return new AlloyRecipe(station.getBaseItem().getId(), catalysts);
	}

	public String getBaseId() {
		return baseId;
	}

	public List<String> getCatalystIds() {
		return catalystIds;
	}

	public String comboKey() {
		if (catalystIds.isEmpty()) {
			return baseId;
		}
		return baseId + "|" + String.join(",", catalystIds);
	}

	public String catalystsJson() {
		return String.join(",", catalystIds);
	}

	public String fileBaseName() {
		if (catalystIds.isEmpty()) {
			return baseId;
		}
		return baseId + "__" + String.join("__", catalystIds);
	}

	public File resolveIndexFile(File root) {
		return new File(new File(root, baseId), fileBaseName() + ".idx");
	}

	public static AlloyRecipe fromComboKey(String comboKey) {
		if (comboKey == null || comboKey.isBlank()) {
			return null;
		}
		String normalized = comboKey.toLowerCase();
		int pipe = normalized.indexOf('|');
		if (pipe < 0) {
			return new AlloyRecipe(normalized, List.of());
		}
		String base = normalized.substring(0, pipe);
		String catalystPart = normalized.substring(pipe + 1);
		List<String> catalysts = new ArrayList<>();
		if (!catalystPart.isBlank()) {
			catalysts.addAll(Arrays.asList(catalystPart.split(",")));
		}
		return new AlloyRecipe(base, catalysts);
	}

	public boolean matches(AlloyRecipe other) {
		if (other == null) {
			return false;
		}
		return baseId.equalsIgnoreCase(other.baseId) && catalystIds.equals(other.catalystIds);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof AlloyRecipe other)) {
			return false;
		}
		return matches(other);
	}

	@Override
	public int hashCode() {
		return Objects.hash(baseId, catalystIds);
	}
}
