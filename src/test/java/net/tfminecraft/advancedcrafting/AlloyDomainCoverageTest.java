package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.enums.StationFeedback;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.objects.stats.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class AlloyDomainCoverageTest extends CoverageSupport {
  @Test
  void stationRejectsDuplicateIncompatibleAndOverCapacityIngredients() throws Exception {
    var world = server.addSimpleWorld("world");
    var loc = new Location(world, 1, 2, 3);
    var station = new AlloyStation(loc);
    assertSame(loc, station.getLocation());
    assertNull(station.getBaseItem());
    assertEquals(0, station.getElementAmount());
    assertEquals(0, station.getTotalValue());
    assertTrue(station.getIngredients().isEmpty());
    assertTrue(station.getStatus().contains("0/1"));
    station.drop();
    var base = ingredient("iron", "base: true\nvalue: 2");
    var catalyst = ingredient("copper", "value: 3");
    assertEquals(StationFeedback.WRONG_BASE, station.addIngredient(catalyst));
    assertEquals(StationFeedback.SUCCESS, station.addIngredient(base));
    assertSame(base, station.getBaseItem());
    assertEquals(StationFeedback.EXISTS, station.addIngredient(base));
    assertEquals(StationFeedback.SUCCESS, station.addIngredient(catalyst));
    assertEquals(StationFeedback.EXISTS, station.addIngredient(catalyst));
    TypeLoader.map.put("wood", new IngredientType("wood", yaml("name: Wood")));
    Cache.combinations.put(base.getIngredientData().getType(), new ArrayList<>());
    assertEquals(
        StationFeedback.INCOMPATIBLE_TYPE, station.addIngredient(ingredient("wood", "type: wood")));
    assertEquals(StationFeedback.SUCCESS, station.addIngredient(ingredient("a", "")));
    assertEquals(StationFeedback.SUCCESS, station.addIngredient(ingredient("b", "")));
    assertEquals(StationFeedback.SUCCESS, station.addIngredient(ingredient("c", "")));
    assertEquals(StationFeedback.CAPACITY, station.addIngredient(ingredient("d", "")));
    assertEquals(5, station.getElementAmount());
    assertEquals(8, station.getTotalValue());
    assertEquals(5, station.getIngredients().size());
    assertEquals(4, station.getCatalysts().size());
    assertTrue(station.getStatus().contains("1/1"));
    station.drop();
    assertEquals(5, world.getEntities().size());
  }

  @Test
  void alloyDataAndNamingWindowRetainRecipeAndCanonicalBuckets() throws Exception {
    var ingredient = ingredient("iron", "base: true\ntier: 3\nstat-merge-key: metals");
    var stats = new StatData(List.of("armor(3)"));
    var hits = new HashMap<>(Map.of(HitLoader.getByString("strike"), 2));
    var data = new AlloyData(ingredient, stats, hits, "smith(3)");
    assertTrue(data.hasXP());
    assertEquals("smith(3)", data.getXP());
    assertEquals(3, data.getTier());
    assertEquals("metals", data.getStatMergeBucketId());
    assertSame(stats, data.getStatData());
    assertSame(hits, data.getHits());
    assertSame(ingredient.getIngredientData().getType(), data.getType());
    assertSame(ingredient.getIngredientData().getModelScheme(), data.getModelScheme());
    assertEquals(2, data.getModel());
    var recipe = new AlloyRecipe("iron", List.of("copper"));
    data.setRecipe(recipe);
    assertSame(recipe, data.getRecipe());
    assertTrue(data.buildRevisionContent().contains("armor(3.0)"));
    var none =
        new AlloyData(
            data.getColourScheme(),
            2,
            null,
            data.getModelScheme(),
            new StatData(),
            new HashMap<>(),
            null,
            null,
            1,
            null);
    assertFalse(none.hasXP());
    assertNull(none.getStatMergeBucketId());
    assertTrue(none.buildRevisionContent().contains("xp=;"));
    var normalized =
        new AlloyData(
            data.getColourScheme(),
            2,
            data.getType(),
            data.getModelScheme(),
            stats,
            hits,
            null,
            recipe,
            3,
            " CUSTOM ");
    assertEquals("custom", normalized.getStatMergeBucketId());
    var fallback =
        new AlloyData(
            data.getColourScheme(),
            2,
            data.getType(),
            data.getModelScheme(),
            stats,
            hits,
            null,
            recipe,
            3,
            " ");
    assertEquals("metal", fallback.getStatMergeBucketId());
    var alloy = new Alloy("Test Alloy", ingredient, stats, hits, null, recipe);
    assertEquals("test_alloy", alloy.getId());
    assertTrue(alloy.getName().contains("Test Alloy"));
    alloy.setName("New");
    alloy.setId("new");
    alloy.setRevision(4);
    assertEquals("New", alloy.getName());
    assertEquals("new", alloy.getId());
    assertEquals(4, alloy.getRevision());
    assertEquals(recipe, alloy.getData().getRecipe());
    var item = new ItemStack(Material.IRON_INGOT);
    var named = new NamableAlloy(alloy, item);
    assertSame(alloy, named.getAlloy());
    assertSame(item, named.getItem());
    assertEquals(0, named.getTime());
    for (int i = 0; i < 59; i++) assertFalse(named.tick());
    assertTrue(named.tick());
    assertEquals(60, named.getTime());
  }

  @Test
  void alloyBuildUsesMmoNameLoreModelAndPersistentIdentity() throws Exception {
    var ing = ingredient("iron", "base: true\ntier: 3");
    var alloy =
        new Alloy(
            "bronze",
            "Bronze",
            new AlloyData(ing, new StatData(List.of("armor(2)")), new HashMap<>(), null));
    alloy.setRevision(5);
    for (boolean history : List.of(false, true))
      try (var nbts = mockStatic(io.lumine.mythic.lib.api.item.NBTItem.class);
          var models = mockStatic(net.tfminecraft.advancedcrafting.util.LegacyModelData.class);
          var mmos =
              mockConstruction(
                  net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem.class,
                  withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                  (mmo, ctx) -> {
                    when(mmo.getData(net.Indyuce.mmoitems.ItemStats.NAME))
                        .thenReturn(new net.Indyuce.mmoitems.stat.data.StringData("Old"));
                    when(mmo.newBuilder().build()).thenReturn(new ItemStack(Material.IRON_INGOT));
                    if (history) {
                      var hist = mock(net.Indyuce.mmoitems.stat.type.StatHistory.class);
                      when(hist.getOriginalData())
                          .thenReturn(new net.Indyuce.mmoitems.stat.type.NameData("Old"));
                      when(mmo.computeStatHistory(net.Indyuce.mmoitems.ItemStats.NAME))
                          .thenReturn(hist);
                    } else
                      when(mmo.computeStatHistory(net.Indyuce.mmoitems.ItemStats.NAME))
                          .thenReturn(null);
                  })) {
        var result = alloy.build();
        assertEquals("bronze", net.tfminecraft.advancedcrafting.utils.AcItemTags.getId(result));
        assertEquals(
            5, net.tfminecraft.advancedcrafting.utils.AcItemTags.getStoredRevision(result));
        assertTrue(result.getItemMeta().hasEnchants());
        models.verify(
            () -> net.tfminecraft.advancedcrafting.util.LegacyModelData.set(any(), eq(2)));
      }
  }
}
