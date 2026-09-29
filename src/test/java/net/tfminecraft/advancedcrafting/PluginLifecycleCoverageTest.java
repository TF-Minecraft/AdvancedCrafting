package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class PluginLifecycleCoverageTest extends CoverageSupport {
  @Test
  void freshPluginBootReloadAndShutdownUsePersistentDataFolder() throws Exception {
    for (String name : List.of("TLibs", "MMOCore", "MMOItems", "MythicLib"))
      MockBukkit.createMockPlugin(name);
    var running = MockBukkit.load(AdvancedCrafting.class);
    assertSame(running, AdvancedCrafting.plugin);
    assertNotNull(running.getIngredientManager());
    assertNotNull(AdvancedCrafting.getAlloyManager());
    assertNotNull(AdvancedCrafting.getAlloyRecipeStore());
    assertNotNull(AdvancedCrafting.getCraftingManager());
    var data = running.getDataFolder().toPath();
    assertTrue(Files.exists(data.resolve("config.yml")));
    running.createConfigs();
    running.createFolders();
    for (String folder : List.of("colour-schemes", "naming-schemes", "model-schemes", "recipes")) {
      Files.createDirectories(data.resolve(folder + "/ignored"));
      Files.copy(
          Path.of(
              "src/main/resources", folder, folder.equals("recipes") ? "weapons.yml" : "basic.yml"),
          data.resolve(folder + "/default.yml"));
    }
    running.reload();
    var player = server.addPlayer();
    running.reloadMessage(player);
    assertTrue(player.nextMessage().contains("Reloading plugin"));
    assertTrue(player.nextMessage().contains("Reloading complete"));
    var recipe = RecipeLoader.get().values().iterator().next();
    var station = new CraftingStation(new Location(server.addSimpleWorld("world"), 0, 1, 2));
    station.setRecipe(recipe);
    AdvancedCrafting.getCraftingManager().set(new HashMap<>(Map.of(station.getLoc(), station)));
    running.onDisable();
    assertTrue(
        Files.list(data.resolve("data/stations")).anyMatch(p -> p.toString().endsWith(".json")));
  }
}
