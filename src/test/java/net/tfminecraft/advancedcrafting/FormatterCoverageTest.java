package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.advancedcrafting.database.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class FormatterCoverageTest extends CoverageSupport {
  AlloyRecipeStore store() throws Exception {
    var store = new AlloyRecipeStore(temp.resolve("recipes").toFile());
    var field = AdvancedCrafting.class.getDeclaredField("alloyRecipeStore");
    field.setAccessible(true);
    field.set(plugin, store);
    return store;
  }

  @Test
  void craftInspectorReportsRecipeInputsAndRevisionChanges() throws Exception {
    var p = server.addPlayer();
    CraftInspectFormatter.send(p);
    assertTrue(p.nextMessage().contains("Hold"));
    var iron = ingredient("iron", "");
    iron.setRevision(2);
    var alloy =
        new Alloy("bronze", "Bronze", new AlloyData(iron, new StatData(), new HashMap<>(), null));
    alloy.setRevision(3);
    AlloyManager.addAlloy(alloy);
    var inputs =
        List.of(
            new CraftInput("ingredient", "iron", 1, 1),
            new CraftInput("ingredient", "missing", 1, 0),
            new CraftInput("alloy", "bronze", 1, 3),
            new CraftInput("alloy", "missing", 1, 0),
            new CraftInput("other", "missing", 1, 0));
    var provenance = new CraftProvenance("sword", "fine", inputs, 0);
    var item = new ItemStack(Material.IRON_SWORD);
    provenance.applyTo(item);
    CraftInspectFormatter.send(p, item);
    assertTrue(p.nextMessage().contains("Craft inspect"));
    var recipe = recipe("");
    RecipeLoader.map.put("sword", recipe);
    provenance.syncInputRevisions();
    provenance.applyTo(item);
    CraftInspectFormatter.send(p, item);
    var messages = new ArrayList<String>();
    String msg;
    while ((msg = p.nextMessage()) != null) messages.add(msg);
    assertTrue(messages.stream().anyMatch(s -> s.contains("(unknown)")));
    assertTrue(messages.stream().anyMatch(s -> s.contains("(Sword)")));
    assertTrue(messages.stream().anyMatch(s -> s.contains("Outdated: §aNo")));
    assertTrue(messages.stream().anyMatch(s -> s.contains("live 2")));
  }

  @Test
  void alloyInfoExplainsMissingMappingsCollisionsAndTruncatesRecipeLists() throws Exception {
    var store = store();
    var p = server.addPlayer();
    AlloyInfoFormatter.send(p, "MISSING");
    assertTrue(p.nextMessage().contains("Unknown alloy"));
    var iron = ingredient("iron", "");
    var data = new AlloyData(iron, new StatData(), new HashMap<>(), null);
    var alloy = new Alloy("bronze", "Bronze", data);
    AlloyManager.addAlloy(alloy);
    AlloyInfoFormatter.send(p, "BRONZE");
    var recipe = new AlloyRecipe("iron", List.of("zinc"));
    data.setRecipe(recipe);
    AlloyInfoFormatter.send(p, "bronze");
    store.upsert(recipe, "wrong");
    AlloyInfoFormatter.send(p, "bronze");
    store.upsert(recipe, "bronze");
    for (int i = 0; i < 22; i++) {
      var craft =
          new CraftingRecipe(
              "sword" + i, yaml("name: Sword\ncategory: weapons\nrecipe: ['metal.2']"));
      RecipeLoader.map.put(craft.getId(), craft);
    }
    RecipeLoader.map.put("empty", recipe(""));
    AlloyInfoFormatter.send(p, "bronze");
    var messages = new ArrayList<String>();
    String msg;
    while ((msg = p.nextMessage()) != null) messages.add(msg);
    assertTrue(messages.stream().anyMatch(s -> s.contains("not set")));
    assertTrue(messages.stream().anyMatch(s -> s.contains("no row")));
    assertTrue(messages.stream().anyMatch(s -> s.contains("mismatch")));
    assertTrue(messages.stream().anyMatch(s -> s.contains("this alloy")));
    assertTrue(messages.stream().anyMatch(s -> s.contains("2 §7more")));
    Files.createDirectories(temp.resolve("data/alloys"));
    data.setRecipe(null);
    Path file = temp.resolve("data/alloys/bronze.json");
    for (String content :
        List.of(
            "{}",
            "bad",
            "{\"recipe\":{\"base\":\"iron\"}}",
            "{\"recipe\":{\"base\":\"iron\",\"catalysts\":[\"zinc\"]}}")) {
      Files.writeString(file, content);
      AlloyInfoFormatter.send(p, "bronze");
    }
    AlloyManager.removeAlloy("bronze");
    try (var db =
        mockConstruction(
            AlloyDatabase.class, (mock, ctx) -> when(mock.loadAlloy("bronze")).thenReturn(alloy))) {
      AlloyInfoFormatter.send(p, "bronze");
    }
  }

  @Test
  void recipeSyncAuditsRepairsAndDetectsMalformedOrConflictingJson() throws Exception {
    var store = store();
    var sender = mock(CommandSender.class);
    AlloyRecipeSync.run(sender, false);
    verify(sender).sendMessage("§cAlloys folder not found.");
    var root = temp.resolve("data/alloys");
    Files.createDirectories(root.getParent());
    Files.writeString(root, "file");
    AlloyRecipeSync.run(sender, false);
    verify(sender).sendMessage("§cCould not read alloys folder.");
    Files.delete(root);
    Files.createDirectories(root);
    AlloyRecipeSync.run(sender, false);
    verify(sender).sendMessage("§a[AC] No recipe issues found.");
    Files.createDirectory(root.resolve("dir"));
    Files.writeString(root.resolve("ignored.txt"), "ignored");
    Files.writeString(root.resolve("bad.json"), "bad");
    Files.writeString(root.resolve("norecipe.json"), "{\"id\":\"none\"}");
    Files.writeString(root.resolve("nullrecipe.json"), "{\"id\":\"null\",\"recipe\":null}");
    Files.writeString(root.resolve("emptyrecipe.json"), "{\"id\":\"empty\",\"recipe\":{}}");
    Files.writeString(
        root.resolve("bronze.json"),
        "{\"id\":\"bronze\",\"recipe\":{\"base\":\"iron\",\"catalysts\":[\"zinc\"]}}");
    Files.writeString(
        root.resolve("steel.json"),
        "{\"id\":\"steel\",\"recipe\":{\"base\":\"iron\",\"catalysts\":[\"zinc\"]}}");
    Files.writeString(
        root.resolve("bronze2.json"), "{\"id\":\"bronze\",\"recipe\":{\"base\":\"iron\"}}");
    Files.writeString(
        root.resolve("scrap.json"), "{\"id\":\"scrap\",\"recipe\":{\"base\":\"wood\"}}");
    store.upsert(new AlloyRecipe("unrelated", List.of()), "bronze");
    store.upsert(new AlloyRecipe("extra", List.of()), "bronze");
    store.upsert(new AlloyRecipe("scrap", List.of()), "scrap");
    store.upsert(new AlloyRecipe("iron", List.of("zinc")), "wrong");
    AlloyRecipeSync.run(sender, false);
    assertEquals("wrong", store.getResultByCombo("iron|zinc"));
    AlloyRecipeSync.run(sender, true);
    assertNotEquals("wrong", store.getResultByCombo("iron|zinc"));
    assertEquals("bronze", store.getResultByCombo("iron"));
    verify(sender, atLeastOnce()).sendMessage(contains("issue(s)"));
    verify(sender).sendMessage(contains("Repaired"));
  }
}
