package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.advancedcrafting.database.Database;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.*;
import org.bukkit.Location;
import org.json.simple.*;
import org.junit.jupiter.api.Test;

class StationDatabaseCoverageTest extends CoverageSupport {
  @Test
  void absentStationDirectoryIsAnEmptyDatabase() {
    var db = new Database();
    assertTrue(db.loadStations().isEmpty());
    assertDoesNotThrow(db::clear);
  }

  @Test
  void stationRoundTripUsesPluginDataDirectoryAndWorldName() throws Exception {
    var db = new Database();
    var world = server.addSimpleWorld("custom_world");
    var loc = new Location(world, 1.5, 2.5, 3.5);
    var iron = ingredient("iron", "hits: ['strike.2']");
    var recipe = recipe("recipe: ['metal.2']");
    RecipeLoader.map.put("sword", recipe);
    var station =
        new CraftingStation(
            loc,
            recipe,
            new HashMap<>(Map.of("ingredient.iron", 2)),
            new HashMap<>(Map.of(HitLoader.getByString("strike"), 1)));
    db.saveStation(new CraftingStation(loc));
    db.saveStation(station);
    var loaded = db.loadStations();
    assertEquals(1, loaded.size());
    assertEquals(loc, loaded.values().iterator().next().getLoc());
    assertEquals(
        Map.of("ingredient.iron", 2), loaded.values().iterator().next().getCurrentMaterials());
    var dir = temp.resolve("data/stations");
    Files.createDirectories(dir.resolve("ignored"));
    Files.writeString(dir.resolve("broken.json"), "broken");
    assertEquals(1, db.loadStations().size());
    db.clear();
    assertTrue(db.loadStations().isEmpty());
    assertTrue(Files.isDirectory(dir.resolve("ignored")));
  }

  @Test
  void jsonConversionHandlesValuesDefaultsAndWriteFailures() throws Exception {
    var db = new Database();
    var field = Database.class.getDeclaredField("json");
    field.setAccessible(true);
    var current = new JSONObject();
    current.put("existing", "old");
    current.put("obj", new JSONObject());
    current.put("arr", new JSONArray());
    field.set(db, current);
    var defaults = new HashMap<String, Object>();
    defaults.put("text", "&aGreen");
    defaults.put("double", 2.5);
    defaults.put("integer", 3);
    defaults.put("bool", true);
    defaults.put("obj2", new JSONObject());
    defaults.put("arr2", new JSONArray());
    assertEquals("old", db.getRawData("existing", defaults));
    assertEquals("missing", db.getRawData("missing", defaults));
    assertEquals("§aGreen", db.getString("text", defaults));
    assertTrue(db.getBoolean("bool", defaults));
    assertEquals(2.5, db.getDouble("double", defaults));
    assertEquals(3, db.getInteger("integer", defaults));
    assertEquals(-1, db.getDouble("bad", defaults));
    assertEquals(-1, db.getInteger("bad", defaults));
    for (String key : List.of("obj", "obj2", "missing")) assertNotNull(db.getObject(key, defaults));
    for (String key : List.of("arr", "arr2", "missing")) assertNotNull(db.getArray(key, defaults));
    assertTrue(db.save(temp.resolve("test.json").toFile(), defaults));
    assertFalse(db.save(temp.toFile(), defaults));
  }

  @Test
  void unreadableStationFilesSurviveClearAndBecomePrunableOnlyAfterSuccessfulLoad()
      throws Exception {
    var db = new Database();
    var dir = temp.resolve("data/stations");
    Files.createDirectories(dir);
    var broken = dir.resolve("broken.json");
    Files.writeString(broken, "invalid original bytes");
    assertTrue(db.loadStations().isEmpty());
    db.clear();
    assertEquals("invalid original bytes", Files.readString(broken));
    var recipe = recipe("");
    RecipeLoader.map.put("sword", recipe);
    String station =
        "{\"world\":\"offline_world\",\"xPos\":0.0,\"yPos\":1.0,\"zPos\":2.0,\"recipe\":\"sword\",\"materials\":[],\"hits\":[]}";
    Files.writeString(broken, station);
    assertTrue(db.loadStations().isEmpty());
    db.clear();
    assertEquals(station, Files.readString(broken));
    server.addSimpleWorld("offline_world");
    assertEquals(1, db.loadStations().size());
    db.clear();
    assertFalse(Files.exists(broken));
  }

  @Test
  void savingWithACollidingUuidCannotOverwriteARejectedStation() throws Exception {
    var dir = temp.resolve("data/stations");
    Files.createDirectories(dir);
    var collision = UUID.randomUUID();
    var next = UUID.randomUUID();
    var rejected = dir.resolve(collision + ".json");
    Files.writeString(rejected, "original rejected bytes");
    var db = new Database();
    db.loadStations();
    db.clear();
    var world = server.addSimpleWorld("world");
    var station = new CraftingStation(new Location(world, 1, 2, 3));
    station.setRecipe(recipe(""));
    try (var ids = org.mockito.Mockito.mockStatic(UUID.class)) {
      ids.when(UUID::randomUUID).thenReturn(collision, next);
      db.saveStation(station);
    }
    assertEquals("original rejected bytes", Files.readString(rejected));
    assertTrue(Files.exists(dir.resolve(next + ".json")));
  }
}
