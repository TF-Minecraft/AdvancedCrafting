package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.database.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.stats.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class EdgeCoverageTest extends CoverageSupport {
  static Object call(Class<?> cls, String name, Class<?>[] types, Object... args) throws Exception {
    var m = cls.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m.invoke(null, args);
  }

  ItemStack alloyTag(String id) {
    var item = new ItemStack(Material.IRON_INGOT);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(PDCKeys.alloyId(), PersistentDataType.STRING, id);
    item.setItemMeta(meta);
    return item;
  }

  @Test
  void bareItemsAndMetadataDefaultsRemainUnmanaged() {
    new Cache();
    new StatToString();
    var item = mock(ItemStack.class);
    when(item.getType()).thenReturn(Material.PAPER);
    assertEquals(0, AcItemTags.getStoredRevision(item));
    assertEquals(-1, AcItemTags.getLoreStart(item));
    assertEquals(-1, AcItemTags.getLoreLen(item));
    assertFalse(AcItemTags.hasStatsLoreFlag(null));
    assertFalse(AcItemTags.hasStatsLoreFlag(item));
    assertFalse(AcItemTags.getStatsLore(item));
    assertFalse(AcItemRefresher.isOutdated(new ItemStack(Material.AIR)));
    assertFalse(AcItemLoreRefresher.refresh(item).isChanged());
    assertFalse(AcItemLoreRefresher.refresh(item).isChanged());
    assertTrue(StatToString.getFullString(new StatModifier("damage", -2)).contains("-2"));
  }

  @Test
  void loreRefreshLoadsMissingAlloysAndSplicesExistingBlocks() throws Exception {
    var iron = ingredient("iron", "tier: 2");
    var alloy =
        new Alloy("bronze", "Bronze", new AlloyData(iron, new StatData(), new HashMap<>(), null));
    alloy.setRevision(3);
    var item = alloyTag("bronze");
    Cache.showIngredientStats = false;
    try (var db =
        mockConstruction(
            AlloyDatabase.class, (m, c) -> when(m.loadAlloy("bronze")).thenReturn(alloy))) {
      var changed = AcItemLoreRefresher.refresh(item);
      assertTrue(changed.isChanged());
      assertSame(alloy, AlloyManager.getAlloyById("bronze"));
      assertFalse(AcItemLoreRefresher.isOutdated(changed.getItem()));
      alloy.setRevision(4);
      assertTrue(AcItemLoreRefresher.isOutdated(changed.getItem()));
      assertTrue(AcItemLoreRefresher.refresh(changed.getItem()).isChanged());
      AlloyManager.removeAlloy("bronze");
      assertEquals(
          4,
          call(
              AcItemLoreRefresher.class,
              "getLiveRevision",
              new Class[] {ItemStack.class},
              changed.getItem()));
    }
    var missing = alloyTag("unknown");
    try (var db = mockConstruction(AlloyDatabase.class)) {
      assertTrue(AcItemLoreRefresher.refresh(missing).getError().contains("unknown alloy"));
      assertEquals(
          0,
          call(
              AcItemLoreRefresher.class,
              "getLiveRevision",
              new Class[] {ItemStack.class},
              missing));
    }
    assertEquals(
        0,
        call(
            AcItemLoreRefresher.class,
            "getLiveRevision",
            new Class[] {ItemStack.class},
            new ItemStack(Material.PAPER)));
    var stale = iron.build();
    IngredientLoader.oList.clear();
    assertEquals(
        0,
        call(AcItemLoreRefresher.class, "getLiveRevision", new Class[] {ItemStack.class}, stale));
  }

  @Test
  void bucketAndRevisionResolversHandleMissingUnknownAndUnmappedSources() throws Exception {
    var iron = ingredient("iron", "");
    var alloy =
        new Alloy("bronze", "Bronze", new AlloyData(iron, new StatData(), new HashMap<>(), null));
    AlloyManager.addAlloy(alloy);
    for (String key : List.of("invalid", "ingredient.missing", "alloy.missing", "unknown.foo"))
      assertNull(
          call(BucketStatAverager.class, "resolveStatData", new Class[] {String.class}, key));
    assertSame(
        alloy.getData().getStatData(),
        call(
            BucketStatAverager.class,
            "resolveStatData",
            new Class[] {String.class},
            "alloy.bronze"));
    assertNull(
        call(
            BucketStatAverager.class,
            "normalizeBucketId",
            new Class[] {String.class},
            (Object) null));
    assertNull(
        call(BucketStatAverager.class, "normalizeBucketId", new Class[] {String.class}, " "));
    assertTrue(
        BucketStatAverager.compute(Map.of("alloy.missing", 1, "unknown.foo", 1))
            .getModifiers()
            .isEmpty());
    assertEquals(
        "alloy.bronze",
        MajorityTierResolver.resolveMajorityKey(recipe(""), Map.of("alloy.bronze", 1)));
    assertEquals(
        "alloy.bronze",
        MajorityTierResolver.resolveMajorityKey(
            recipe("main-type: wood"), Map.of("alloy.bronze", 1)));
    assertEquals(
        "ingredient.iron",
        MajorityTierResolver.resolveMajorityKey(
            recipe("main-type: wood"), Map.of("ingredient.iron", 1)));
    for (String key : List.of("ingredient.missing", "alloy.missing", "unknown.foo"))
      assertEquals(key, MajorityTierResolver.resolveMajorityKey(recipe(""), Map.of(key, 1)));
    assertEquals(
        0.,
        call(
            StatTemplateMath.class,
            "getSourceAmount",
            new Class[] {StatData.class, String.class},
            new StatData(),
            "damage"));
    var data = new StatData();
    data.addModifier(new StatModifier("speed", 1));
    assertEquals(
        0.,
        call(
            StatTemplateMath.class,
            "getSourceAmount",
            new Class[] {StatData.class, String.class},
            data,
            "damage"));
    for (CraftInput input :
        List.of(
            new CraftInput("ingredient", "missing", 1, 1),
            new CraftInput("alloy", "bronze", 1, 1),
            new CraftInput("alloy", "missing", 1, 1),
            new CraftInput("other", "missing", 1, 1)))
      assertNotNull(
          call(StatRefreshDebug.class, "liveRevision", new Class[] {CraftInput.class}, input));
  }

  @Test
  void malformedIndexesAndFilesystemFailuresStayRecoverable() throws Exception {
    var root = temp.resolve("indexes");
    var store = new AlloyRecipeStore(root.toFile());
    Files.createDirectories(root.resolve("longbase"));
    Files.writeString(root.resolve("longbase/x.idx"), "result=old");
    store.updateResultId("old", "new");
    assertTrue(store.loadAllComboResults().isEmpty());
    assertNull(
        invoke(store, "recipeFromIndexFile", new Class[] {File.class}, new File("no-parent.idx")));
    assertNull(
        invoke(
            store,
            "recipeFromIndexFile",
            new Class[] {File.class},
            root.resolve("file.txt").toFile()));
    var index = root.resolve("iron/iron.idx");
    Files.createDirectories(index);
    Files.writeString(index.resolve("occupied"), "x");
    store.deleteByCombo("iron");
    assertTrue(Files.exists(index));
    try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
      files
          .when(() -> Files.walk(root))
          .thenThrow(new IOException("simulated unavailable filesystem"));
      assertTrue(store.loadAllComboResults().isEmpty());
    }
    try (var digest = mockStatic(java.security.MessageDigest.class)) {
      digest
          .when(() -> java.security.MessageDigest.getInstance("SHA-256"))
          .thenThrow(new java.security.NoSuchAlgorithmException("unavailable provider"));
      assertThrows(RuntimeException.class, () -> RevisionTracker.sha256("x"));
    }
  }

  @Test
  void publicMetadataGuardsHandleEmptyItemsAndUnknownDefinitions() throws Exception {
    var plain = mock(ItemStack.class);
    when(plain.getType()).thenReturn(Material.PAPER);
    CraftTierLore.applyPdc(plain, 0, 1);
    CraftTierLore.applyTierLine(plain, 1);
    CraftTierLore.refreshTierLine(plain, 1);
    assertNull(ScrapProvenance.readBaseId(plain));
    var listener = new MMOItemRebuildListener();
    assertNull(invoke(listener, "readMajorityTier", new Class[] {ItemStack.class}, plain));
    assertNull(invoke(listener, "readTierLoreStart", new Class[] {ItemStack.class}, (Object) null));
    assertNull(invoke(listener, "readTierLoreStart", new Class[] {ItemStack.class}, plain));
    var db = new AlloyDatabase();
    var json = new org.json.simple.JSONObject();
    var jsonField = AlloyDatabase.class.getDeclaredField("json");
    jsonField.setAccessible(true);
    jsonField.set(db, new org.json.simple.JSONObject());
    var defaults = new HashMap<String, Object>();
    defaults.put("object", json);
    assertSame(json, db.getObject("object", defaults));
    var stored = new org.json.simple.JSONObject();
    stored.put("stored", json);
    jsonField.set(db, stored);
    assertSame(json, db.getObject("stored", defaults));
    var dataFolder = temp.resolve("new-plugin").toFile();
    when(plugin.getDataFolder()).thenReturn(dataFolder);
    doCallRealMethod().when(plugin).createFolders();
    plugin.createFolders();
    assertTrue(dataFolder.isDirectory());
  }

  @Test
  void bucketAccumulationSkipsUnavailableStatsAndNonpositiveOverflowTotals() throws Exception {
    var iron = ingredient("iron", "stats: ['armor(2)']");
    var copper = ingredient("copper", "stats: ['armor(4)']");
    assertTrue(
        BucketStatAverager.compute(
                Map.of("ingredient.iron", Integer.MAX_VALUE, "ingredient.copper", 1))
            .getModifiers()
            .isEmpty());
    var missing = new Alloy("missing", "Missing", new AlloyData(iron, null, new HashMap<>(), null));
    AlloyManager.addAlloy(missing);
    assertTrue(BucketStatAverager.compute(Map.of("alloy.missing", 1)).getModifiers().isEmpty());
  }

  @Test
  void stationPersistenceHandlesUuidCollisionAndUnwritableRoot() throws Exception {
    var world = server.addSimpleWorld("world");
    var station =
        new net.tfminecraft.advancedcrafting.objects.crafting.CraftingStation(
            new Location(world, 0, 1, 0));
    station.setRecipe(recipe(""));
    var db = new Database();
    var id = UUID.randomUUID();
    try (var ids = mockStatic(UUID.class, CALLS_REAL_METHODS)) {
      ids.when(UUID::randomUUID).thenReturn(id);
      db.saveStation(station);
      db.saveStation(station);
      assertEquals(1, Files.list(temp.resolve("data/stations")).count());
    }
    var blocked = temp.resolve("blocked");
    Files.writeString(blocked, "file");
    when(plugin.getDataFolder()).thenReturn(blocked.toFile());
    assertDoesNotThrow(() -> db.saveStation(station));
  }

  @Test
  void validSocketRegistryAndDuplicateJsonMappingsAreAccepted() throws Exception {
    var file = temp.resolve("sockets.yml");
    Files.writeString(file, "gemstones:\n  slots:\n    fine: [Red]");
    try (var registry = mockStatic(net.tfminecraft.tlibs.socket.SocketTierRegistry.class)) {
      registry
          .when(() -> net.tfminecraft.tlibs.socket.SocketTierRegistry.getGroup("Red"))
          .thenReturn("gemstones");
      new SocketGroupLoader().load(file.toFile());
      assertEquals(List.of("Red"), SocketGroupLoader.getByString("gemstones").getSlots("fine"));
    }
    var store = new AlloyRecipeStore(temp.resolve("recipes").toFile());
    var field = AdvancedCrafting.class.getDeclaredField("alloyRecipeStore");
    field.setAccessible(true);
    field.set(plugin, store);
    store.upsert(new AlloyRecipe("iron", List.of()), "bronze");
    var root = temp.resolve("data/alloys");
    Files.createDirectories(root);
    for (String filename : List.of("one.json", "two.json"))
      Files.writeString(
          root.resolve(filename), "{\"id\":\"bronze\",\"recipe\":{\"base\":\"iron\"}}");
    var sender = mock(org.bukkit.command.CommandSender.class);
    AlloyRecipeSync.run(sender, false);
    verify(sender).sendMessage("§a[AC] No recipe issues found.");
    var index = temp.resolve("recipes/iron/iron.idx");
    Files.writeString(index, "combo=iron\nignored\nresult=bronze\n");
    assertEquals("bronze", store.getResultByCombo("iron"));
    Files.writeString(index, "unknown=value\nresult=bronze\n");
    assertEquals("bronze", store.getResultByCombo("iron"));
  }

  @Test
  void craftedRefreshDetectionTracksRevisionChanges() {
    try {
      var iron = ingredient("iron", "");
      iron.setRevision(1);
      var item = new ItemStack(Material.IRON_SWORD);
      new CraftProvenance("unknown", "fine", List.of(new CraftInput("ingredient", "iron", 1, 1)), 0)
          .applyTo(item);
      assertFalse(AcItemRefresher.isOutdated(item));
      iron.setRevision(2);
      assertTrue(AcItemRefresher.isOutdated(item));
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
