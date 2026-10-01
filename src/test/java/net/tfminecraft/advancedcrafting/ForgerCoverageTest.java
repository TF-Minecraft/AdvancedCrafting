package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.database.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class ForgerCoverageTest extends CoverageSupport {
  @Test
  void xpRangeUsesOnlyMatchingProfessionAndAllowsMissingXp() throws Exception {
    var station = new AlloyStation(new Location(server.addSimpleWorld("world"), 0, 1, 2));
    station.addIngredient(ingredient("iron", "base: true"));
    assertNull(new AlloyForger(station).getXP());
    station = new AlloyStation(station.getLocation());
    station.addIngredient(ingredient("base", "base: true\nxp: smith(3)"));
    station.addIngredient(ingredient("less", "xp: smith(1)"));
    station.addIngredient(ingredient("more", "xp: smith(5)"));
    station.addIngredient(ingredient("other", "xp: mine(20)"));
    station.addIngredient(ingredient("none", ""));
    var xp = new AlloyForger(station).getXP();
    assertTrue(xp.startsWith("smith("));
    double value = Double.parseDouble(xp.substring(6, xp.length() - 1));
    assertTrue(value >= 1 && value <= 5);
  }

  @Test
  void failedStaleRecipeDeletionRespectsRetryLimit() {
    var station = mock(AlloyStation.class);
    var p = server.addPlayer();
    try (var dbs =
        mockConstruction(
            AlloyDatabase.class, (db, ctx) -> when(db.getResult(station)).thenReturn("missing"))) {
      assertNull(new AlloyForger(station).forge(p, 100));
      assertEquals(1, dbs.constructed().size());
    }
  }

  AlloyForger forger(AlloyStation station, double random) throws Exception {
    var ctor =
        AlloyForger.class.getDeclaredConstructor(
            AlloyStation.class, java.util.function.DoubleSupplier.class);
    ctor.setAccessible(true);
    return ctor.newInstance(station, (java.util.function.DoubleSupplier) () -> random);
  }

  StatData stats(AlloyForger f) throws Exception {
    var field = AlloyForger.class.getDeclaredField("stats");
    field.setAccessible(true);
    return (StatData) field.get(f);
  }

  @Test
  void forgeReturnsScrapKnownAlloysNewDiscoveriesAndRecoversStaleIndexes() throws Exception {
    var p = server.addPlayer();
    var loc = new Location(server.addSimpleWorld("world"), 0, 1, 2);
    var station = new AlloyStation(loc);
    station.addIngredient(ingredient("iron", "base: true\nhits: ['strike.2']"));
    station.addIngredient(ingredient("copper", "hits: ['strike.1']"));
    Cache.scrap = "MATERIAL.SCRAP";
    when(net.Indyuce.mmoitems
            .MMOItems
            .plugin
            .getItems()
            .getMMOItem(any(), eq("SCRAP"))
            .newBuilder()
            .build())
        .thenAnswer(i -> new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_NUGGET));
    Cache.alloyForgeBaseSuccess = 0;
    Cache.alloyForgeBonusPerSqrtValue = 0;
    Cache.alloyForgeMaxSuccess = 0;
    try (var dbs = mockConstruction(AlloyDatabase.class)) {
      assertNull(forger(station, 0).forge(p));
      verify(dbs.constructed().getFirst()).saveRecipe(station, "scrap");
      var dropped = loc.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).iterator().next().getItemStack();
      assertEquals(Map.of("iron", 1, "copper", 1), ScrapProvenance.readInputs(dropped));
    }
    try (var dbs =
        mockConstruction(
            AlloyDatabase.class, (db, ctx) -> when(db.getResult(station)).thenReturn("scrap"))) {
      assertNull(forger(station, 0).forge(p));
    }
    var known = mock(Alloy.class);
    when(known.getId()).thenReturn("known");
    when(known.build())
        .thenAnswer(i -> new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_INGOT));
    AlloyManager.addAlloy(known);
    try (var dbs =
        mockConstruction(
            AlloyDatabase.class, (db, ctx) -> when(db.getResult(station)).thenReturn("known"))) {
      assertNull(forger(station, 0).forge(p));
    }
    AlloyManager.removeAlloy("known");
    try (var dbs =
        mockConstruction(
            AlloyDatabase.class,
            (db, ctx) -> {
              when(db.getResult(station)).thenReturn("known");
              when(db.loadAlloy("known")).thenReturn(known);
            })) {
      assertNull(forger(station, 0).forge(p));
      assertSame(known, AlloyManager.getAlloyById("known"));
    }
    Cache.alloyForgeBaseSuccess = 100;
    Cache.alloyForgeMaxSuccess = 100;
    try (var dbs = mockConstruction(AlloyDatabase.class);
        var alloys =
            mockConstruction(
                Alloy.class,
                (a, ctx) -> {
                  when(a.getId()).thenReturn("new");
                  when(a.build())
                      .thenReturn(
                          new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_INGOT));
                })) {
      assertNotNull(forger(station, 0).forge(p));
      verify(dbs.constructed().getFirst()).saveAlloy(alloys.constructed().getFirst());
    }
    var taken = mock(Alloy.class);
    when(taken.getId()).thenReturn("iron");
    AlloyManager.addAlloy(taken);
    try (var dbs = mockConstruction(AlloyDatabase.class)) {
      assertNull(forger(station, 0).forge(p));
      assertTrue(p.nextMessage().contains("no free alloy names"));
    }
    AlloyManager.removeAlloy("iron");
    try (var dbs =
            mockConstruction(
                AlloyDatabase.class,
                (db, ctx) -> {
                  if (ctx.getCount() == 1) when(db.getResult(station)).thenReturn("missing");
                });
        var alloys =
            mockConstruction(
                Alloy.class,
                (a, ctx) -> {
                  when(a.getId()).thenReturn("new2");
                  when(a.build())
                      .thenReturn(
                          new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_INGOT));
                })) {
      assertNotNull(forger(station, 0).forge(p));
      verify(dbs.constructed().getFirst()).deleteRecipe(station);
      assertEquals(2, dbs.constructed().size());
    }
    assertNull(forger(station, 0).forge(p, 101));
  }

  @Test
  void deterministicMergeCoversGuaranteedInheritanceCapsAndProtectedStats() throws Exception {
    Cache.maxFactor = 1.5;
    Cache.gemstoneStatBaseChance = 100;
    Cache.gemstoneStatBonusPerValue = 0;
    var station = new AlloyStation(new Location(server.addSimpleWorld("world"), 0, 1, 2));
    station.addIngredient(
        ingredient(
            "iron",
            "base: true\n"
                + "value: 100\n"
                + "stats: ['armor(10)', 'protected(2)']\n"
                + "protected-stats: [protected]"));
    station.addIngredient(
        ingredient(
            "gem", "path: m.gemstones.ruby\nstats: ['armor(2)', 'speed(4)', 'protected(3)']"));
    station.addIngredient(ingredient("gem2", "path: m.gemstones.emerald\nstats: ['speed(2)']"));
    station.addIngredient(ingredient("gem3", "path: m.gemstones.sapphire\nstats: ['speed(8)']"));
    var f = forger(station, .5);
    f.mergeStats();
    assertEquals(
        12,
        stats(f)
            .getAmount(
                new net.tfminecraft.advancedcrafting.objects.stats.StatModifier("armor", 0)));
    assertEquals(
        12,
        stats(f)
            .getAmount(
                new net.tfminecraft.advancedcrafting.objects.stats.StatModifier("speed", 0)));
    assertEquals(
        2,
        stats(f)
            .getAmount(
                new net.tfminecraft.advancedcrafting.objects.stats.StatModifier("protected", 0)));
    Cache.gemstoneStatBaseChance = 0;
    f = forger(station, .5);
    f.mergeStats();
    assertEquals(
        0,
        stats(f)
            .getAmount(
                new net.tfminecraft.advancedcrafting.objects.stats.StatModifier("speed", 0)));
    for (int value : List.of(0, 100))
      for (double random : List.of(0., .99)) {
        var s = new AlloyStation(station.getLocation());
        s.addIngredient(
            ingredient(
                "base" + value + random, "base: true\nvalue: " + value + "\nstats: ['armor(10)']"));
        s.addIngredient(
            ingredient(
                "catalyst" + value + random,
                "value: 0\nstats: ['armor(2)', 'new(3)', 'negative(-2)']"));
        var forged = forger(s, random);
        forged.mergeStats();
        assertTrue(stats(forged).hasModifiers());
      }
  }

  @Test
  void deterministicXpAndNameFallbacksRetainOriginalSelectionRules() throws Exception {
    var loc = new Location(server.addSimpleWorld("world"), 0, 1, 2);
    var station = new AlloyStation(loc);
    station.addIngredient(ingredient("iron", "base: true\nxp: smith(3)"));
    assertEquals("smith(3.0)", forger(station, 0).getXP());
    var names = net.tfminecraft.advancedcrafting.loaders.SchemeLoader.names.get("default");
    names.getNames().add("Second");
    var taken = mock(Alloy.class);
    when(taken.getId()).thenReturn("iron");
    AlloyManager.addAlloy(taken);
    assertEquals("Second", invoke(forger(station, .99), "getName", new Class[] {}));
    names.getNames().remove("Second");
    net.tfminecraft.advancedcrafting.loaders.SchemeLoader.names.put(
        "backup",
        new net.tfminecraft.advancedcrafting.objects.schemes.NamingScheme(
            "backup", yaml("names: [Backup]\ncolour-scheme: default")));
    station.addIngredient(ingredient("copper", "scheme: backup"));
    assertEquals("Backup", invoke(forger(station, 0), "getName", new Class[] {}));
  }

  @Test
  void mergeHelperPreservesMissingBaseAndClampsNegativeMultiplier() throws Exception {
    var catalyst = new net.tfminecraft.advancedcrafting.objects.stats.StatModifier("armor", 3);
    var method =
        AlloyForger.class.getDeclaredMethod(
            "modifierForMerge", catalyst.getClass(), catalyst.getClass(), boolean.class);
    method.setAccessible(true);
    assertEquals(
        3,
        ((net.tfminecraft.advancedcrafting.objects.stats.StatModifier)
                method.invoke(null, catalyst, null, false))
            .getAmount());
    var station = new AlloyStation(new Location(server.addSimpleWorld("world"), 0, 1, 0));
    var f = forger(station, 0);
    var value = AlloyForger.class.getDeclaredField("value");
    value.setAccessible(true);
    value.setInt(f, 10);
    assertEquals(
        0., invoke(f, "randomize", new Class[] {String.class, double.class}, "negative", -2.));
    var base = new HashMap<String, net.tfminecraft.advancedcrafting.objects.stats.StatModifier>();
    base.put("uncapped", catalyst);
    invoke(
        f,
        "merge",
        new Class[] {HashMap.class, HashMap.class, List.class},
        base,
        new HashMap<>(),
        List.of());
    assertSame(catalyst, base.get("uncapped"));
    Cache.scrap = "MATERIAL.SCRAP";
    when(net.Indyuce.mmoitems
            .MMOItems
            .plugin
            .getItems()
            .getMMOItem(any(), eq("SCRAP"))
            .newBuilder()
            .build())
        .thenReturn(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_NUGGET));
    try (var dbs =
        mockConstruction(
            AlloyDatabase.class, (db, ctx) -> when(db.getResult(station)).thenReturn("scrap"))) {
      assertNull(f.forge(server.addPlayer()));
    }
  }
}
