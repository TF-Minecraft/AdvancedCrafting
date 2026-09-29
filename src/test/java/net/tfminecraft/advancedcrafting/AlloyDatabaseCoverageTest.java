package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.advancedcrafting.database.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.Location;
import org.json.simple.*;
import org.junit.jupiter.api.Test;

class AlloyDatabaseCoverageTest extends CoverageSupport {
  AlloyRecipeStore store() throws Exception {
    var store = new AlloyRecipeStore(temp.resolve("recipes").toFile());
    var field = AdvancedCrafting.class.getDeclaredField("alloyRecipeStore");
    field.setAccessible(true);
    field.set(plugin, store);
    return store;
  }

  Alloy alloy(String id, boolean withRecipe) throws Exception {
    var ingredient = ingredient("iron", "base: true\ntier: 3");
    var data =
        new AlloyData(
            ingredient,
            new StatData(List.of("armor(2)")),
            new HashMap<>(Map.of(HitLoader.getByString("strike"), 2)),
            withRecipe ? "smith(4)" : null);
    if (withRecipe) data.setRecipe(new AlloyRecipe("iron", List.of("zinc")));
    return new Alloy(id, id, data);
  }

  @Test
  void databaseRoundTripsAlloyStatsHitsRecipeAndRevisions() throws Exception {
    var store = store();
    var db = new AlloyDatabase();
    db.loadAlloys();
    assertNull(db.loadAlloy("missing"));
    var alloy = alloy("bronze", true);
    db.saveAlloy(alloy);
    assertEquals(1, alloy.getRevision());
    db.saveAlloy(alloy);
    var loaded = db.loadAlloy("BRONZE");
    assertNotNull(loaded);
    assertEquals("bronze", loaded.getName());
    assertEquals(2, loaded.getData().getModel());
    assertEquals(3, loaded.getData().getTier());
    assertEquals("smith(4)", loaded.getData().getXP());
    assertEquals(alloy.getData().getRecipe(), loaded.getData().getRecipe());
    assertEquals(2, loaded.getData().getStatData().getModifiers().getFirst().getAmount());
    assertEquals(2, loaded.getData().getHits().get(HitLoader.getByString("strike")));
    Files.createDirectory(temp.resolve("data/alloys/ignored"));
    Files.writeString(temp.resolve("data/alloys/invalid.json"), "invalid");
    db.loadAlloys();
    assertNotNull(AlloyManager.getAlloyById("bronze"));
    assertNull(db.loadAlloy("invalid"));
    var station = new AlloyStation(new Location(server.addSimpleWorld("world"), 0, 1, 2));
    db.saveRecipe(station, "scrap");
    assertNull(db.getResult(station));
    station.addIngredient(IngredientLoader.getByString("iron"));
    db.saveRecipe(station, "scrap");
    assertEquals("scrap", db.getResult(station));
    db.deleteRecipe(station);
    assertNull(db.getResult(station));
    loaded.setId("brass");
    db.editAlloy(loaded, "bronze");
    assertNull(db.loadAlloy("bronze"));
    assertNotNull(db.loadAlloy("brass"));
    assertEquals("brass", store.getResultByCombo("iron|zinc"));
    db.editAlloy(loaded, "missing");
    db.saveAlloy(alloy("untagged", false));
    assertNull(db.loadAlloy("untagged").getData().getRecipe());
  }

  @Test
  void databaseParsesOptionalLegacyRecipeAndBucketFields() throws Exception {
    store();
    var db = new AlloyDatabase();
    db.saveAlloy(alloy("bronze", true));
    var file = temp.resolve("data/alloys/bronze.json");
    var root = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    root.remove("statMergeBucketId");
    root.remove("xp");
    root.getAsJsonObject("recipe").remove("catalysts");
    Files.writeString(file, root.toString());
    assertEquals("metal", db.loadAlloy("bronze").getData().getStatMergeBucketId());
    root.add("recipe", com.google.gson.JsonNull.INSTANCE);
    root.addProperty("statMergeBucketId", " ");
    Files.writeString(file, root.toString());
    assertNotNull(db.loadAlloy("bronze"));
    root.add("recipe", new com.google.gson.JsonObject());
    root.addProperty("type", "missing");
    Files.writeString(file, root.toString());
    assertNull(db.loadAlloy("bronze").getData().getStatMergeBucketId());
    root.remove("recipe");
    root.addProperty("statMergeBucketId", "CUSTOM");
    Files.writeString(file, root.toString());
    assertEquals("custom", db.loadAlloy("bronze").getData().getStatMergeBucketId());
  }

  @Test
  void genericJsonAccessorsPreserveDefaultsAndRejectBadNumericValues() throws Exception {
    store();
    var db = new AlloyDatabase();
    db.saveAlloy(alloy("bronze", false));
    db.loadAlloy("bronze");
    var defaults = new HashMap<String, Object>();
    defaults.put("text", "&cRed");
    defaults.put("double", 2.5);
    defaults.put("integer", 3);
    defaults.put("bool", true);
    defaults.put("object", new JSONObject());
    defaults.put("array", new JSONArray());
    assertEquals("bronze", db.getRawData("id", defaults));
    assertEquals("missing", db.getRawData("missing", defaults));
    assertEquals("§cRed", db.getString("text", defaults));
    assertTrue(db.getBoolean("bool", defaults));
    assertEquals(2.5, db.getDouble("double", defaults));
    assertEquals(3, db.getInteger("integer", defaults));
    assertEquals(-1, db.getDouble("missing", defaults));
    assertEquals(-1, db.getInteger("missing", defaults));
    assertEquals(new JSONObject(), db.getObject("object", defaults));
    assertEquals(new JSONObject(), db.getObject("missing", defaults));
    assertEquals(new JSONArray(), db.getArray("array", defaults));
    assertEquals(new JSONArray(), db.getArray("missing", defaults));
    assertNotNull(db.getArray("stats", defaults));
    assertTrue(db.save(temp.resolve("generic.json").toFile(), defaults));
    assertFalse(db.save(temp.toFile(), defaults));
    var blocked = temp.resolve("data/alloys/bad.json");
    Files.createDirectory(blocked);
    Files.writeString(blocked.resolve("child"), "keep");
    var bad = alloy("bad", false);
    db.saveAlloy(bad);
    assertTrue(Files.isDirectory(blocked));
    try (var main = mockStatic(AdvancedCrafting.class, CALLS_REAL_METHODS)) {
      main.when(AdvancedCrafting::getAlloyRecipeStore)
          .thenThrow(new IllegalStateException("unavailable"));
      db.editAlloy(bad, "missing");
    }
  }
}
