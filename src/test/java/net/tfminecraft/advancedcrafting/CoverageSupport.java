package net.tfminecraft.advancedcrafting;

import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import net.Indyuce.mmoitems.MMOItems;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.objects.schemes.*;
import net.tfminecraft.advancedcrafting.objects.stats.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockito.MockedStatic;

abstract class CoverageSupport {
  @TempDir Path temp;
  ServerMock server;
  MockedStatic<TLibs> tlibs;
  ItemAPI items;
  AdvancedCrafting plugin;
  Map<java.lang.reflect.Field, Object> cacheBefore = new HashMap<>();

  @BeforeEach
  void setup() throws Exception {
    for (var field : Cache.class.getFields()) {
      Object value = field.get(null);
      cacheBefore.put(field, value instanceof Map ? new HashMap<>((Map<?, ?>) value) : value);
    }
    server = MockBukkit.mock();
    plugin = mock(AdvancedCrafting.class);
    when(plugin.getName()).thenReturn("AdvancedCrafting");
    when(plugin.namespace()).thenReturn("advancedcrafting");
    when(plugin.getLogger()).thenReturn(Logger.getLogger("AdvancedCraftingTest"));
    when(plugin.getDataFolder()).thenReturn(temp.toFile());
    AdvancedCrafting.plugin = plugin;
    io.lumine.mythic.lib.MythicLib.plugin =
        mock(io.lumine.mythic.lib.MythicLib.class, RETURNS_DEEP_STUBS);
    when(io.lumine.mythic.lib.MythicLib.plugin.namespace()).thenReturn("mythiclib");
    MMOItems.plugin = mock(MMOItems.class, RETURNS_DEEP_STUBS);
    when(MMOItems.plugin.namespace()).thenReturn("mmoitems");
    when(MMOItems.plugin.getConfig()).thenReturn(yaml("gem-sockets.uncolored: Uncolored"));
    items = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    tlibs = mockStatic(TLibs.class);
    tlibs.when(TLibs::getItemAPI).thenReturn(items);
    when(items.getCreator().getItemFromPath(anyString()))
        .thenAnswer(
            i -> {
              String path = i.getArgument(0);
              Material material = Material.matchMaterial(path.substring(path.lastIndexOf('.') + 1));
              return new ItemStack(material == null ? Material.PAPER : material);
            });
    TypeLoader.map.clear();
    TypeLoader.hMap.clear();
    SchemeLoader.names.clear();
    SchemeLoader.colours.clear();
    SchemeLoader.models.clear();
    IngredientLoader.oList.clear();
    HitLoader.map.clear();
    QualityLoader.map.clear();
    RecipeLoader.map.clear();
    CategoryLoader.categories.clear();
    StatTemplateLoader.get().clear();
    SocketGroupLoader.map.clear();
    for (String id :
        new ArrayList<>(net.tfminecraft.advancedcrafting.managers.AlloyManager.getAlloyIds()))
      net.tfminecraft.advancedcrafting.managers.AlloyManager.removeAlloy(id);
    Cache.globalStatOffsets.clear();
    Cache.permissionNamespaces.clear();
    Cache.combinations.clear();
    Cache.alloyForgeBaseBonus.clear();
    Cache.showIngredientStats = true;
    AdvancedCrafting.getRevisionTracker().load(temp.toFile());
    TypeLoader.map.put("metal", new IngredientType("metal", yaml("name: Metal")));
    TypeLoader.hMap.put("hammer", new HitType("hammer", yaml("name: Hammer")));
    SchemeLoader.colours.put(
        "default", new ColourScheme("default", yaml("models: [2]\ncolours: ['#FFFFFF']")));
    SchemeLoader.models.put("default", new ModelScheme("default", yaml("models: ['sword(7)']")));
    SchemeLoader.names.put(
        "default", new NamingScheme("default", yaml("names: [Iron]\ncolour-scheme: default")));
    HitLoader.map.put(
        "strike", new CraftingHit("strike", yaml("name: Strike\ntype: hammer\ntool: v.iron_axe")));
    CategoryLoader.categories.put("weapons", new RecipeCategory("weapons", yaml("name: Weapons")));
  }

  @AfterEach
  void cleanup() throws Exception {
    MockBukkit.unmock();
    tlibs.close();
    AdvancedCrafting.plugin = null;
    for (var entry : cacheBefore.entrySet()) entry.getKey().set(null, entry.getValue());
  }

  static YamlConfiguration yaml(String text) throws Exception {
    var result = new YamlConfiguration();
    result.loadFromString(text);
    return result;
  }

  Ingredient ingredient(String id, String extra) throws Exception {
    var result = new Ingredient(id, yaml("path: v.iron_ingot\ntype: metal\n" + extra));
    IngredientLoader.oList.add(result);
    return result;
  }

  CraftingRecipe recipe(String extra) throws Exception {
    return new CraftingRecipe(
        "sword",
        yaml(
            "name: '%material% Sword'\ncategory: weapons\ntemplate: SWORD.TEST\ntype: smith\n"
                + extra));
  }

  static Object invoke(Object instance, String method, Class<?>[] types, Object... args)
      throws Exception {
    var m = instance.getClass().getDeclaredMethod(method, types);
    m.setAccessible(true);
    return m.invoke(instance, args);
  }
}
