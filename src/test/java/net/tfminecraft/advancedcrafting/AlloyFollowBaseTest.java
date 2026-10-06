package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.database.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;
import net.tfminecraft.advancedcrafting.utils.AlloyRebaser;
import org.junit.jupiter.api.Test;

class AlloyFollowBaseTest extends CoverageSupport {
  static final long CUTOFF = 1_000_000_000_000L;

  static StatData stats(String... entries) {
    return new StatData(List.of(entries));
  }

  static double amount(StatData data, String type) {
    return data.getAmount(new StatModifier(type, 0));
  }

  void store() throws Exception {
    var field = AdvancedCrafting.class.getDeclaredField("alloyRecipeStore");
    field.setAccessible(true);
    field.set(plugin, new AlloyRecipeStore(temp.resolve("recipes").toFile()));
  }

  File alloyFile(String id, String recipe, String stats, String baseStats, long modified)
      throws Exception {
    Path folder = temp.resolve("data/alloys");
    Files.createDirectories(folder);
    String json =
        "{\"id\":\""
            + id
            + "\",\"name\":\""
            + id
            + "\",\"model\":30.0,\"colour scheme\":\"default\",\"type\":\"metal\","
            + "\"scheme\":\"default\",\"hits\":[\"strike.2\"],\"stats\":["
            + stats
            + "]"
            + (recipe == null ? "" : ",\"recipe\":{\"base\":\"" + recipe + "\",\"catalysts\":[]}")
            + (baseStats == null ? "" : ",\"baseStats\":[" + baseStats + "]")
            + "}";
    File file = folder.resolve(id + ".json").toFile();
    Files.writeString(file.toPath(), json);
    assertTrue(file.setLastModified(modified));
    return file;
  }

  String stamp() throws Exception {
    var field = AlloyDatabase.class.getDeclaredField("BACKUP_STAMP");
    field.setAccessible(true);
    return (String) field.get(null);
  }

  @Test
  void snapshotUsesTheRecordOrTheLiveBaseWithLegacyOverridesForOldFiles() {
    StatData live = stats("weapon_damage(5.0)", "armor(1.0)");
    StatData recorded = stats("weapon_damage(9.0)");
    assertSame(recorded, AlloyRebaser.snapshotFor(recorded, "steel", live, 0));
    assertEquals(5.0, amount(AlloyRebaser.snapshotFor(null, "steel", live, 0), "weapon_damage"));
    Cache.alloyLegacyBaseStats.put("steel", stats("weapon_damage(12.0)", "max_mana(2.0)"));
    Cache.alloyLegacyForgedBefore = 0;
    assertEquals(5.0, amount(AlloyRebaser.snapshotFor(null, "STEEL", live, 0), "weapon_damage"));
    Cache.alloyLegacyForgedBefore = CUTOFF;
    assertEquals(
        5.0, amount(AlloyRebaser.snapshotFor(null, "steel", live, CUTOFF), "weapon_damage"));
    assertEquals(5.0, amount(AlloyRebaser.snapshotFor(null, "iron", live, 0), "weapon_damage"));
    StatData legacy = AlloyRebaser.snapshotFor(null, "Steel", live, CUTOFF - 1);
    assertEquals(12.0, amount(legacy, "weapon_damage"));
    assertEquals(2.0, amount(legacy, "max_mana"));
    assertEquals(1.0, amount(legacy, "armor"));
    assertEquals(5.0, amount(live, "weapon_damage"));
  }

  @Test
  void rebaseShiftsByTheBaseChangeAndClampsToZeroAndTheForgeCap() {
    StatData base = stats("weapon_damage(12.0)", "armor(1.0)");
    assertNull(AlloyRebaser.rebase(stats("weapon_damage(13.0)"), base, base, 1.2));
    StatData moved =
        AlloyRebaser.rebase(
            stats("weapon_damage(12.15)", "armor(1.1)", "speed(-2.0)", "gone(3.0)", "mana(4.0)"),
            stats("weapon_damage(12.0)", "armor(1.0)", "speed(1.0)", "gone(3.0)", "mana(1.0)"),
            stats("weapon_damage(5.0)", "armor(2.0)", "speed(0.0)", "new_stat(1.5)", "mana(2.0)"),
            1.2);
    assertEquals(5.15, amount(moved, "weapon_damage"));
    assertEquals(2.1, amount(moved, "armor"));
    assertEquals(-3.0, amount(moved, "speed"));
    assertEquals(1.5, amount(moved, "new_stat"));
    assertEquals(2.4, amount(moved, "mana"));
    assertTrue(moved.getModifiers().stream().noneMatch(m -> m.getType().equals("gone")));
    StatData floored =
        AlloyRebaser.rebase(
            stats("weapon_damage(2.0)"),
            stats("weapon_damage(12.0)"),
            stats("weapon_damage(5.0)"),
            1.2);
    assertEquals(0.0, amount(floored, "weapon_damage"));
    assertEquals(1, floored.getModifiers().size());
  }

  @Test
  void describeListsOnlyStatsThatChanged() {
    assertEquals(
        "armor 1.0 -> 0.0, weapon_damage 12.15 -> 5.15, zinc 0.0 -> 2.0",
        AlloyRebaser.describe(
            stats("weapon_damage(12.15)", "armor(1.0)", "mana(3.0)"),
            stats("weapon_damage(5.15)", "mana(3.0)", "zinc(2.0)")));
    assertEquals("", AlloyRebaser.describe(stats("a(1.0)"), stats("a(1.0)")));
  }

  @Test
  void forgedAlloysRecordTheirBaseAndStatDataCopiesAreIndependent() throws Exception {
    var steel = ingredient("steel", "stats:\n  - weapon_damage(5.0)");
    var data = new AlloyData(steel, stats("weapon_damage(6.0)"), new HashMap<>(), null);
    assertEquals(5.0, amount(data.getBaseStats(), "weapon_damage"));
    data.getBaseStats().getModifiers().getFirst().setAmount(1.0);
    assertEquals(5.0, amount(steel.getIngredientData().getStatData(), "weapon_damage"));
    data.setStatData(stats("armor(1.0)"));
    assertEquals(1.0, amount(data.getStatData(), "armor"));
    data.setBaseStats(null);
    assertNull(data.getBaseStats());
  }

  @Test
  void configParsesLegacyBaseStatsAndCutoff() throws Exception {
    Path config = temp.resolve("legacy.yml");
    Files.writeString(
        config,
        """
        alloy-legacy-base:
          forged-before: "2026-10-05T20:29:00Z"
          stats:
            Steel_Ingot:
              - weapon_damage(12.0)
        """);
    new ConfigLoader().load(config.toFile());
    assertEquals(
        java.time.Instant.parse("2026-10-05T20:29:00Z").toEpochMilli(),
        Cache.alloyLegacyForgedBefore);
    assertEquals(12.0, amount(Cache.alloyLegacyBaseStats.get("steel_ingot"), "weapon_damage"));
    Files.writeString(config, "alloy-legacy-base:\n  forged-before: soon\n");
    new ConfigLoader().load(config.toFile());
    assertEquals(0, Cache.alloyLegacyForgedBefore);
    assertTrue(Cache.alloyLegacyBaseStats.isEmpty());
    assertEquals(0, ConfigLoader.parseInstant(null));
    assertEquals(0, ConfigLoader.parseInstant(" "));
  }

  @Test
  void startupMovesLegacyAlloysWithTheirBaseAndBacksThemUp() throws Exception {
    store();
    ingredient("steel", "stats:\n  - weapon_damage(5.0)\n  - armor(0.5)");
    Cache.maxFactor = 1.2;
    Cache.alloyLegacyBaseStats.put("steel", stats("weapon_damage(12.0)"));
    Cache.alloyLegacyForgedBefore = CUTOFF;
    File legacy =
        alloyFile(
            "parsusite", "steel", "\"weapon_damage(12.15)\",\"armor(0.6)\"", null, CUTOFF - 5);
    File newer = alloyFile("newite", "steel", "\"weapon_damage(5.3)\"", null, CUTOFF + 5);
    File recorded =
        alloyFile(
            "recordite",
            "steel",
            "\"weapon_damage(5.4)\"",
            "\"weapon_damage(5.0)\",\"armor(0.5)\"",
            CUTOFF - 5);
    File noRecipe = alloyFile("loose", null, "\"weapon_damage(9.0)\"", null, CUTOFF - 5);
    File noBase = alloyFile("orphan", "missing", "\"weapon_damage(9.0)\"", null, CUTOFF - 5);
    new AlloyDatabase().loadAlloys();

    var moved = AlloyManager.getAlloyById("parsusite").getData();
    assertEquals(5.15, amount(moved.getStatData(), "weapon_damage"));
    assertEquals(0.6, amount(moved.getStatData(), "armor"));
    assertEquals(5.0, amount(moved.getBaseStats(), "weapon_damage"));
    Alloy reloaded = new AlloyDatabase().loadAlloy("parsusite");
    assertEquals(5.15, amount(reloaded.getData().getStatData(), "weapon_damage"));
    assertEquals(5.0, amount(reloaded.getData().getBaseStats(), "weapon_damage"));
    Path backups = temp.resolve("data/alloy-backups/" + stamp());
    assertTrue(Files.readString(backups.resolve("parsusite.json")).contains("12.15"));

    assertEquals(5.3, amount(new AlloyDatabase().loadAlloy("newite").getData().getStatData(), "weapon_damage"));
    assertNotNull(new AlloyDatabase().loadAlloy("newite").getData().getBaseStats());
    assertTrue(Files.exists(backups.resolve("newite.json")));

    assertFalse(Files.exists(backups.resolve("recordite.json")));
    assertEquals(CUTOFF - 5, recorded.lastModified());
    assertEquals(CUTOFF - 5, noRecipe.lastModified());
    assertEquals(CUTOFF - 5, noBase.lastModified());
    assertNull(new AlloyDatabase().loadAlloy("loose").getData().getBaseStats());
    assertTrue(legacy.exists() && newer.exists());

    // A second start finds the record and leaves every file alone.
    long movedStamp = legacy.lastModified();
    for (String id : new ArrayList<>(AlloyManager.getAlloyIds())) AlloyManager.removeAlloy(id);
    new AlloyDatabase().loadAlloys();
    assertEquals(movedStamp, legacy.lastModified());
    assertEquals(5.15, amount(AlloyManager.getAlloyById("parsusite").getData().getStatData(), "weapon_damage"));
  }

  @Test
  void failedBackupLeavesTheAlloyUnchanged() throws Exception {
    store();
    ingredient("steel", "stats:\n  - weapon_damage(5.0)");
    Cache.maxFactor = 1.2;
    Cache.alloyLegacyBaseStats.put("steel", stats("weapon_damage(12.0)"));
    Cache.alloyLegacyForgedBefore = CUTOFF;
    File blocked = alloyFile("blocked", "steel", "\"weapon_damage(12.0)\"", null, CUTOFF - 5);
    Files.createDirectories(temp.resolve("data/alloy-backups/" + stamp() + "/blocked.json/inner"));
    new AlloyDatabase().loadAlloys();
    var data = AlloyManager.getAlloyById("blocked").getData();
    assertEquals(12.0, amount(data.getStatData(), "weapon_damage"));
    assertNull(data.getBaseStats());
    assertEquals(CUTOFF - 5, blocked.lastModified());
    assertFalse(Files.readString(blocked.toPath()).contains("baseStats"));
  }

  @Test
  void repeatedBaseStatEntriesStaySeparateSoAnUnchangedBaseIsLeftAlone() throws Exception {
    store();
    var base = ingredient("doubled", "stats:\n  - damage(5.0)\n  - damage(2.0)");
    var copy = StatData.copyOf(base.getIngredientData().getStatData());
    assertEquals(2, copy.getModifiers().size());
    assertNotSame(base.getIngredientData().getStatData().getModifiers().getFirst(), copy.getModifiers().getFirst());
    File file =
        alloyFile("twin", "doubled", "\"damage(6.0)\",\"damage(1.0)\"", "\"damage(5.0)\",\"damage(2.0)\"", CUTOFF - 5);
    new AlloyDatabase().loadAlloys();
    assertEquals(CUTOFF - 5, file.lastModified());
    var data = AlloyManager.getAlloyById("twin").getData();
    assertEquals(2, data.getBaseStats().getModifiers().size());
    assertEquals(1, data.getStatData().getModifiers().size());
    assertEquals(7.0, amount(data.getStatData(), "damage"));
  }

  @Test
  void alloysWithoutARecordAreSavedWithoutOne() throws Exception {
    store();
    var data =
        new AlloyData(
            SchemeLoader.colours.get("default"),
            2,
            TypeLoader.map.get("metal"),
            SchemeLoader.models.get("default"),
            stats("armor(1.0)"),
            new HashMap<>(),
            null,
            null,
            1,
            null);
    new AlloyDatabase().saveAlloy(new Alloy("plain", "plain", data));
    assertFalse(Files.readString(temp.resolve("data/alloys/plain.json")).contains("baseStats"));
    assertNull(new AlloyDatabase().loadAlloy("plain").getData().getBaseStats());
  }
}
