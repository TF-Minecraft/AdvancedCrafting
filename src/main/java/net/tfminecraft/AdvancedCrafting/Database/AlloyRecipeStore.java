package net.tfminecraft.AdvancedCrafting.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.AdvancedCrafting.Objects.Alloys.AlloyStation;
import net.tfminecraft.AdvancedCrafting.Objects.Data.AlloyRecipe;

public class AlloyRecipeStore {
	private final File root;

	public AlloyRecipeStore(File root) {
		this.root = root;
		if (!root.exists()) {
			root.mkdirs();
		}
	}

	public String getResult(AlloyStation station) {
		AlloyRecipe recipe = AlloyRecipe.fromStation(station);
		if (recipe == null) {
			return null;
		}
		return getResultByCombo(recipe.comboKey());
	}

	public String getResultByCombo(String comboKey) {
		AlloyRecipe recipe = AlloyRecipe.fromComboKey(comboKey);
		if (recipe == null) {
			return null;
		}
		return readResultId(recipe.resolveIndexFile(root));
	}

	public AlloyRecipe getRecipeByResult(String resultId) {
		String target = resultId.toLowerCase();
		for (File indexFile : listIndexFiles()) {
			IndexEntry entry = readIndexEntry(indexFile);
			if (entry != null && target.equals(entry.resultId)) {
				return entry.recipe != null ? entry.recipe : recipeFromIndexFile(indexFile);
			}
		}
		return null;
	}

	public void upsert(AlloyRecipe recipe, String resultId) {
		File indexFile = recipe.resolveIndexFile(root);
		File parent = indexFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		try (PrintWriter writer = new PrintWriter(indexFile, StandardCharsets.UTF_8)) {
			writer.println("combo=" + recipe.comboKey());
			writer.println("result=" + resultId.toLowerCase());
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public void deleteByStation(AlloyStation station) {
		AlloyRecipe recipe = AlloyRecipe.fromStation(station);
		if (recipe == null) {
			return;
		}
		deleteByCombo(recipe.comboKey());
	}

	public void deleteByCombo(String comboKey) {
		AlloyRecipe recipe = AlloyRecipe.fromComboKey(comboKey);
		if (recipe == null) {
			return;
		}
		File indexFile = recipe.resolveIndexFile(root);
		if (indexFile.exists() && !indexFile.delete()) {
			indexFile.deleteOnExit();
		}
	}

	public void updateResultId(String oldId, String newId) {
		String oldResult = oldId.toLowerCase();
		String newResult = newId.toLowerCase();
		for (File indexFile : listIndexFiles()) {
			IndexEntry entry = readIndexEntry(indexFile);
			if (entry == null || !oldResult.equals(entry.resultId)) {
				continue;
			}
			AlloyRecipe recipe = entry.recipe != null ? entry.recipe : recipeFromIndexFile(indexFile);
			if (recipe == null) {
				continue;
			}
			upsert(recipe, newResult);
		}
	}

	public Map<String, Integer> findDuplicateResultsExcludingScrap() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (File indexFile : listIndexFiles()) {
			IndexEntry entry = readIndexEntry(indexFile);
			if (entry == null || "scrap".equals(entry.resultId)) {
				continue;
			}
			counts.merge(entry.resultId, 1, Integer::sum);
		}
		Map<String, Integer> duplicates = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (entry.getValue() > 1) {
				duplicates.put(entry.getKey(), entry.getValue());
			}
		}
		return duplicates;
	}

	public Map<String, String> loadAllComboResults() {
		Map<String, String> map = new LinkedHashMap<>();
		for (File indexFile : listIndexFiles()) {
			IndexEntry entry = readIndexEntry(indexFile);
			if (entry == null || entry.comboKey == null || entry.resultId == null) {
				continue;
			}
			map.put(entry.comboKey, entry.resultId);
		}
		return map;
	}

	private List<File> listIndexFiles() {
		if (!root.exists()) {
			return List.of();
		}
		try (var stream = Files.walk(root.toPath())) {
			return stream.filter(path -> path.toString().endsWith(".idx"))
					.map(path -> path.toFile())
					.toList();
		} catch (IOException ex) {
			ex.printStackTrace();
			return List.of();
		}
	}

	private String readResultId(File indexFile) {
		IndexEntry entry = readIndexEntry(indexFile);
		return entry == null ? null : entry.resultId;
	}

	private IndexEntry readIndexEntry(File indexFile) {
		if (!indexFile.exists()) {
			return null;
		}
		String comboKey = null;
		String resultId = null;
		try (BufferedReader reader = new BufferedReader(
				new FileReader(indexFile, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.startsWith("combo=")) {
					comboKey = line.substring("combo=".length()).trim().toLowerCase();
				} else if (line.startsWith("result=")) {
					resultId = line.substring("result=".length()).trim().toLowerCase();
				} else if (comboKey == null && resultId == null && !line.contains("=")) {
					resultId = line.toLowerCase();
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
		if (resultId == null) {
			return null;
		}
		AlloyRecipe recipe = comboKey != null ? AlloyRecipe.fromComboKey(comboKey) : recipeFromIndexFile(indexFile);
		if (comboKey == null && recipe != null) {
			comboKey = recipe.comboKey();
		}
		return new IndexEntry(comboKey, resultId, recipe);
	}

	private AlloyRecipe recipeFromIndexFile(File indexFile) {
		File baseDir = indexFile.getParentFile();
		if (baseDir == null) {
			return null;
		}
		String baseId = baseDir.getName().toLowerCase();
		String fileName = indexFile.getName();
		if (!fileName.endsWith(".idx")) {
			return null;
		}
		String fileBase = fileName.substring(0, fileName.length() - ".idx".length());
		if (fileBase.equals(baseId)) {
			return new AlloyRecipe(baseId, java.util.List.of());
		}
		String catalystPart = fileBase.substring(baseId.length() + 2);
		if (!fileBase.startsWith(baseId + "__")) {
			return null;
		}
		return new AlloyRecipe(baseId, java.util.Arrays.asList(catalystPart.split("__")));
	}

	private static final class IndexEntry {
		private final String comboKey;
		private final String resultId;
		private final AlloyRecipe recipe;

		private IndexEntry(String comboKey, String resultId, AlloyRecipe recipe) {
			this.comboKey = comboKey;
			this.resultId = resultId;
			this.recipe = recipe;
		}
	}
}
