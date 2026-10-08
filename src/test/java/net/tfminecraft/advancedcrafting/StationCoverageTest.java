package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.Indyuce.mmoitems.*;
import net.Indyuce.mmoitems.api.item.mmoitem.*;
import net.Indyuce.mmoitems.stat.data.*;
import net.Indyuce.mmoitems.stat.type.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.enums.StationFeedback;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.objects.schemes.*;
import net.tfminecraft.advancedcrafting.util.LegacyModelData;
import net.tfminecraft.advancedcrafting.utils.*;
import net.tfminecraft.tlibs.objects.utils.IntCounter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class StationCoverageTest extends CoverageSupport {
  Location loc() {
    return new Location(server.addSimpleWorld("world"), 1, 2, 3);
  }

  ItemStack alloyItem(String id) {
    var i = new ItemStack(Material.IRON_INGOT);
    var m = i.getItemMeta();
    m.getPersistentDataContainer().set(PDCKeys.alloyId(), PersistentDataType.STRING, id);
    i.setItemMeta(m);
    return i;
  }

  @Test
  void materialAdmissionChecksItemTypePermissionsRecipeAndCapacity() throws Exception {
    var p = server.addPlayer();
    var station = new CraftingStation(loc());
    assertFalse(station.hasRecipe());
    assertNull(station.getRecipe());
    var r = recipe("recipe: ['metal.3']\npermission-namespace: smith");
    station.setRecipe(r);
    assertTrue(station.hasRecipe());
    assertSame(r, station.getRecipe());
    assertEquals(3, station.getTypes().get(TypeLoader.map.get("metal")).getNeeded());
    assertFalse(station.hasAllMaterials(p));
    assertEquals(
        StationFeedback.NOT_INGREDIENT, station.addMaterial(p, new ItemStack(Material.PAPER)));
    var iron = ingredient("iron", "base: true\ntier: 3\npermission: smith\nhits: ['strike.2']");
    var item = iron.build();
    item.setAmount(5);
    p.getInventory().setItemInMainHand(item);
    assertEquals(StationFeedback.NO_PERMS, station.addMaterial(p, item));
    p.addAttachment(
        org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin(), "professions.smith", true);
    assertEquals(StationFeedback.NO_PERMS, station.addMaterial(p, item));
    p.setOp(true);
    assertEquals(StationFeedback.SUCCESS, station.addMaterial(p, item));
    assertEquals(StationFeedback.SUCCESS, station.addMaterial(p, item));
    assertEquals(2, station.getCurrentMaterials().get("ingredient.iron"));
    assertEquals(4, station.getHits().get(HitLoader.map.get("strike")).getNeeded());
    var alloy =
        new Alloy(
            "bronze",
            "Bronze",
            new AlloyData(
                iron,
                new net.tfminecraft.advancedcrafting.objects.data.StatData(),
                new HashMap<>(Map.of(HitLoader.map.get("strike"), 1)),
                null));
    AlloyManager.addAlloy(alloy);
    var a = alloyItem("bronze");
    p.getInventory().setItemInMainHand(a);
    assertEquals(StationFeedback.SUCCESS, station.addMaterial(p, a));
    assertTrue(station.hasAllMaterials(p));
    assertEquals(StationFeedback.CAPACITY, station.addMaterial(p, iron.build()));
    TypeLoader.map.put("wood", new IngredientType("wood", yaml("name: Wood")));
    assertEquals(
        StationFeedback.WRONG_TYPE,
        station.addMaterial(p, ingredient("wood", "type: wood").build()));
    var catalyst = ingredient("gem", "");
    var other = new CraftingStation(station.getLoc());
    other.setRecipe(recipe("recipe: ['metal.1']"));
    p.getInventory().setItemInMainHand(catalyst.build());
    assertEquals(StationFeedback.SUCCESS, other.addMaterial(p, catalyst.build()));
  }

  @Test
  void statusLinesShowMaterialsButNoHitCounts() throws Exception {
    var p = server.addPlayer();
    var station = new CraftingStation(loc());
    station.setRecipe(recipe("recipe: ['metal.2']"));
    var item = ingredient("iron", "hits: ['strike.2']").build();
    p.getInventory().setItemInMainHand(item);
    assertEquals(StationFeedback.SUCCESS, station.addMaterial(p, item));
    station.getHits().get(HitLoader.map.get("strike")).setCurrent(3);
    // Unknown types from a stale config are skipped, not printed as "null".
    station.getTypes().put(null, new IntCounter());
    // Players have to find the hits themselves, so no hit counts are listed.
    assertEquals(
        List.of(
            "§7Recipe: Sword",
            "Metal§7: §e1/2",
            "§7Left-click branding to finish",
            "§cSHIFT + LEFT CLICK with the branding tool to cancel the project!"),
        station.getStatusLines());
  }

  @Test
  void restoredStationsAggregateMaterialAndHitCounts() throws Exception {
    var loc = loc();
    var iron = ingredient("iron", "hits: ['strike.2']");
    var copper = ingredient("copper", "hits: ['strike.1']");
    var alloy =
        new Alloy(
            "bronze",
            "Bronze",
            new AlloyData(
                iron,
                new net.tfminecraft.advancedcrafting.objects.data.StatData(),
                new HashMap<>(Map.of(HitLoader.map.get("strike"), 3)),
                null));
    AlloyManager.addAlloy(alloy);
    var unknownHit = new CraftingHit("other", yaml("name: Other\ntype: unknown\ntool: other.tool"));
    var sameType = new CraftingHit("tap", yaml("name: Tap\ntype: hammer\ntool: material.tap"));
    var materials =
        new HashMap<String, Integer>(
            Map.of(
                "ingredient.iron", 1, "ingredient.copper", 1, "alloy.bronze", 1, "unknown.x", 1));
    var station =
        new CraftingStation(
            loc,
            recipe("recipe: ['metal.3']"),
            materials,
            new HashMap<>(Map.of(HitLoader.map.get("strike"), 1, unknownHit, 1, sameType, 1)));
    assertEquals(3, station.getTypes().get(TypeLoader.map.get("metal")).getCurrent());
    assertEquals(6, station.getHits().get(HitLoader.map.get("strike")).getNeeded());
    assertEquals(1, station.getHits().get(HitLoader.map.get("strike")).getCurrent());
    assertSame(materials, station.getCurrentMaterials());
    TypeLoader.map.put("wood", new IngredientType("wood", yaml("name: Wood")));
    var wood = ingredient("wood", "type: wood");
    var wa =
        new Alloy(
            "woodalloy",
            "Wood",
            new AlloyData(
                wood,
                new net.tfminecraft.advancedcrafting.objects.data.StatData(),
                new HashMap<>(),
                null));
    AlloyManager.addAlloy(wa);
    new CraftingStation(
        loc,
        recipe("recipe: ['metal.1']"),
        new HashMap<>(Map.of("ingredient.wood", 1, "alloy.woodalloy", 1)),
        new HashMap<>());
  }

  @Test
  void hitSelectionUsesRegisteredMmoToolsAndSharedTypeCapacity() throws Exception {
    var p = server.addPlayer();
    var iron = ingredient("iron", "hits: ['strike.2']");
    var station = new CraftingStation(loc());
    station.setRecipe(recipe("recipe: ['metal.1']"));
    var tool = new ItemStack(Material.IRON_AXE);
    assertEquals(StationFeedback.LACKING_ITEMS, station.hit(p, tool));
    p.getInventory().setItemInMainHand(iron.build());
    station.addMaterial(p, iron.build());
    try (var nbt = mockStatic(NBTItem.class)) {
      var data = mock(NBTItem.class);
      nbt.when(() -> NBTItem.get(tool)).thenReturn(data);
      assertEquals(StationFeedback.WRONG_TYPE, station.hit(p, tool));
      when(data.hasType()).thenReturn(true);
      when(data.getType()).thenReturn("v");
      when(data.getString("MMOITEMS_ITEM_ID")).thenReturn("missing");
      assertEquals(StationFeedback.WRONG_TYPE, station.hit(p, tool));
      var different =
          new CraftingHit("different", yaml("name: Other\ntype: missing\ntool: v.other"));
      HitLoader.map.put("different", different);
      when(data.getString("MMOITEMS_ITEM_ID")).thenReturn("other");
      assertEquals(StationFeedback.NONE, station.hit(p, tool));
      var extra = new CraftingHit("extra", yaml("name: Extra\ntype: hammer\ntool: v.extra"));
      HitLoader.map.put("extra", extra);
      when(data.getString("MMOITEMS_ITEM_ID")).thenReturn("extra");
      assertEquals(StationFeedback.SUCCESS, station.hit(p, tool));
      when(data.getString("MMOITEMS_ITEM_ID")).thenReturn("iron_axe");
      assertEquals(StationFeedback.SUCCESS, station.hit(p, tool));
      assertEquals(StationFeedback.CAPACITY, station.hit(p, tool));
      assertEquals(1, station.getHits().get(extra).getCurrent());
    }
  }

  @Test
  void qualityReflectsUnderAndOverWorkAndWarningsUseConfiguredThreshold() throws Exception {
    var p = server.addPlayer();
    var station = new CraftingStation(loc());
    var counter = new IntCounter();
    counter.setNeeded(4);
    station.getHits().put(HitLoader.map.get("strike"), counter);
    for (int current : List.of(0, 2, 4, 6, 8, 10)) {
      counter.setCurrent(current);
      double expected = current <= 4 ? current * 25 : current < 8 ? 200 - current * 25 : 0;
      assertEquals(
          expected,
          ((Number) invoke(station, "calculatePercentage", new Class[] {})).doubleValue());
    }
    Cache.hitOvershootWarnPercent = 0;
    invoke(station, "warnOvershootHits", new Class[] {Player.class}, p);
    Cache.hitOvershootWarnPercent = 30;
    Cache.hitOvershootWarnMessage = null;
    invoke(station, "warnOvershootHits", new Class[] {Player.class}, p);
    Cache.hitOvershootWarnMessage = " ";
    invoke(station, "warnOvershootHits", new Class[] {Player.class}, p);
    Cache.hitOvershootWarnMessage = "Too many %hit%";
    counter.setNeeded(0);
    invoke(station, "warnOvershootHits", new Class[] {Player.class}, p);
    counter.setNeeded(4);
    for (int n : List.of(2, 5, 6)) {
      counter.setCurrent(n);
      invoke(station, "warnOvershootHits", new Class[] {Player.class}, p);
    }
    assertTrue(p.nextMessage().contains("Strike"));
  }

  @Test
  void failedCraftDoesNotAwardExperienceAndSuccessUsesMmoOutput() throws Exception {
    var p = server.addPlayer();
    var iron = ingredient("iron", "base: true\ntier: 2\nxp: smith(2)\nhits: ['strike.1']");
    var station = new CraftingStation(loc());
    station.setRecipe(recipe("recipe: ['metal.1']"));
    assertEquals(StationFeedback.LACKING_ITEMS, station.craft(p));
    p.getInventory().setItemInMainHand(iron.build());
    station.addMaterial(p, iron.build());
    assertEquals(StationFeedback.LACKING_HITS, station.craft(p));
    QualityLoader.map.put("fine", new Quality("fine", yaml("name: Fine\namount: 0\nvalue: 0")));
    try (var nbt = mockStatic(NBTItem.class);
        var models = mockStatic(LegacyModelData.class);
        var mmos =
            mockConstruction(
                LiveMMOItem.class,
                withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                (mmo, ctx) -> {
                  when(mmo.getData(ItemStats.NAME)).thenReturn(new StringData("Old"));
                  when(mmo.computeStatHistory(ItemStats.NAME)).thenReturn(null);
                  when(mmo.newBuilder().build()).thenReturn(new ItemStack(Material.IRON_SWORD));
                });
        var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      bukkit.when(() -> Bukkit.dispatchCommand(any(), anyString())).thenReturn(true);
      assertEquals(StationFeedback.SUCCESS, station.craft(p, 50.));
      bukkit.verify(() -> Bukkit.dispatchCommand(any(), contains("smith 2.0")));
      station.getHits().get(HitLoader.map.get("strike")).setCurrent(1);
      var ht = CraftingStation.class.getDeclaredField("hitTypes");
      ht.setAccessible(true);
      ((Map<?, IntCounter>) ht.get(station)).values().forEach(c -> c.setCurrent(c.getNeeded()));
      assertEquals(StationFeedback.SUCCESS, station.craft(p));
      assertFalse(station.getLoc().getWorld().getEntities().isEmpty());
    }
  }

  @Test
  void dropAndCancelRefundBothIngredientAndAlloyQuantities() throws Exception {
    var p = server.addPlayer();
    var iron = ingredient("iron", "");
    var alloy = mock(Alloy.class);
    when(alloy.getId()).thenReturn("bronze");
    when(alloy.build()).thenAnswer(i -> new ItemStack(Material.GOLD_INGOT));
    AlloyManager.addAlloy(alloy);
    var station = new CraftingStation(loc());
    station.setRecipe(recipe(""));
    station.getCurrentMaterials().put("ingredient.iron", 2);
    station.getCurrentMaterials().put("alloy.bronze", 3);
    station.drop();
    station.drop(2);
    station.cancel();
    assertFalse(station.hasRecipe());
    assertTrue(station.getCurrentMaterials().isEmpty());
    assertEquals(6, station.getLoc().getWorld().getEntities().size());
  }

  @Test
  void obsoleteMaterialTagsDoNotCrashOrConsumeItems() throws Exception {
    var p = server.addPlayer();
    var station = new CraftingStation(loc());
    station.setRecipe(recipe("recipe: ['metal.1']"));
    var missing = ingredient("removed", "").build();
    IngredientLoader.oList.removeIf(i -> i.getId().equals("removed"));
    p.getInventory().setItemInMainHand(missing);
    assertEquals(StationFeedback.NOT_INGREDIENT, station.addMaterial(p, missing));
    assertEquals(1, p.getInventory().getItemInMainHand().getAmount());
    assertEquals(StationFeedback.NOT_INGREDIENT, station.addMaterial(p, alloyItem("removed")));
  }

  @Test
  void modelApplicationHandlesVanillaItemsAdderMissingAndOtherSchemes() throws Exception {
    var station = new CraftingStation(loc());
    station.setRecipe(recipe(""));
    var original = new ItemStack(Material.IRON_SWORD);
    try (var models = mockStatic(LegacyModelData.class)) {
      var vanilla = new ModelScheme("vanilla", yaml("models: ['smith(v.golden_sword.7)']"));
      assertSame(
          original,
          invoke(
              station,
              "applyModel",
              new Class[] {ItemStack.class, ModelScheme.class},
              original,
              vanilla));
      assertEquals(Material.GOLDEN_SWORD, original.getType());
      models.verify(() -> LegacyModelData.set(any(), eq(7)));
      var ia = new ModelScheme("ia", yaml("models: ['smith(ia.custom:sword)']"));
      var merged = new ItemStack(Material.DIAMOND_SWORD);
      when(items.getArmorMerger().merge(original, Optional.empty(), "ia.custom:sword"))
          .thenReturn(merged);
      assertSame(
          merged,
          invoke(
              station,
              "applyModel",
              new Class[] {ItemStack.class, ModelScheme.class},
              original,
              ia));
      var perRecipe =
          new ModelScheme(
              "per-recipe", yaml("models: ['smith(v.golden_sword.7)', 'sword(v.iron_sword.9)']"));
      var weighted = new ItemStack(Material.STONE_SWORD);
      assertSame(
          weighted,
          invoke(
              station,
              "applyModel",
              new Class[] {ItemStack.class, ModelScheme.class},
              weighted,
              perRecipe));
      assertEquals(Material.IRON_SWORD, weighted.getType());
      models.verify(() -> LegacyModelData.set(any(), eq(9)));
      for (String path : List.of("models: ['smith(other.item)']", "models: []"))
        assertSame(
            original,
            invoke(
                station,
                "applyModel",
                new Class[] {ItemStack.class, ModelScheme.class},
                original,
                new ModelScheme("empty", yaml(path))));
    }
  }

  @Test
  void mainMaterialRecipeModelWinsAndSecondaryIngredientRemainsFallback() throws Exception {
    var p = server.addPlayer();
    QualityLoader.map.put("fine", new Quality("fine", yaml("name: Fine\namount: 0\nvalue: 0")));
    TypeLoader.map.put(
        "paper",
        new net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType(
            "paper", yaml("name: Paper")));
    for (boolean specific : List.of(true, false)) {
      SchemeLoader.models.put(
          "metal-look",
          new ModelScheme(
              "metal-look",
              yaml(
                  "models: ['smith(v.iron_sword.1)'"
                      + (specific ? ", 'sword(v.diamond_sword.2)'" : "")
                      + "]")));
      SchemeLoader.models.put(
          "cloth-look",
          new ModelScheme("cloth-look", yaml("models: ['smith(v.golden_sword.3)']")));
      var metal = ingredient("metal-" + specific, "model-scheme: metal-look");
      var cloth = ingredient("cloth-" + specific, "type: paper\nmodel-scheme: cloth-look");
      var alloy =
          new Alloy(
              "metal-" + specific,
              "Metal",
              new AlloyData(
                  metal,
                  new net.tfminecraft.advancedcrafting.objects.data.StatData(),
                  new HashMap<>(),
                  null));
      AlloyManager.addAlloy(alloy);
      var clothAlloy =
          new Alloy(
              "cloth-" + specific,
              "Cloth",
              new AlloyData(
                  cloth,
                  new net.tfminecraft.advancedcrafting.objects.data.StatData(),
                  new HashMap<>(),
                  null));
      AlloyManager.addAlloy(clothAlloy);
      for (String key : List.of("ingredient." + metal.getId(), "alloy." + alloy.getId()))
        for (String secondary : List.of("ingredient." + cloth.getId(), "alloy." + clothAlloy.getId()))
          for (String modelType : List.of("paper", "none")) {
            int expectedModel = specific ? 2 : modelType.equals("none") ? 1 : 3;
            Material expectedMaterial =
                specific
                    ? Material.DIAMOND_SWORD
                    : modelType.equals("none") ? Material.IRON_SWORD : Material.GOLDEN_SWORD;
            var craftRecipe = recipe("recipe: ['metal.1', 'paper.1']\nmodel-type: " + modelType);
            RecipeLoader.map.put(craftRecipe.getId(), craftRecipe);
            var station =
                new CraftingStation(
                    loc(), craftRecipe, new HashMap<>(Map.of(key, 1, secondary, 1)), new HashMap<>());
            station.getCurrentMaterials().put("unknown.removed", 1);
            try (var nbt = mockStatic(NBTItem.class);
                var models = mockStatic(LegacyModelData.class);
                var mmos =
                    mockConstruction(
                        LiveMMOItem.class,
                        withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                        (mmo, ctx) -> {
                          when(mmo.getData(ItemStats.NAME)).thenReturn(new StringData("Old"));
                          when(mmo.computeStatHistory(ItemStats.NAME)).thenReturn(null);
                          when(mmo.newBuilder().build()).thenReturn(new ItemStack(Material.IRON_SWORD));
                        })) {
              var result = station.buildCompletedItem(p, 50.);
              assertEquals(expectedMaterial, result.getType());
              models.verify(() -> LegacyModelData.set(any(), eq(expectedModel)));
              // The tag names the scheme whose look the item got; alloys carry their base's.
              assertEquals(
                  expectedModel == 3 ? "cloth-look" : "metal-look",
                  result
                      .getItemMeta()
                      .getPersistentDataContainer()
                      .get(PDCKeys.craftModelScheme(), PersistentDataType.STRING));
            }
          }
    }
  }

  @Test
  void experienceAggregatesOnlyExistingPositiveValidDefinitions() throws Exception {
    var p = server.addPlayer();
    var station = new CraftingStation(loc());
    var iron = ingredient("iron", "xp: smith(1.25)");
    ingredient("copper", "xp: broken");
    ingredient("zero", "xp: smith(0)");
    ingredient("none", "");
    var bronze =
        new Alloy(
            "bronze",
            "Bronze",
            new AlloyData(
                iron,
                new net.tfminecraft.advancedcrafting.objects.data.StatData(),
                new HashMap<>(),
                "smith(1.25)"));
    AlloyManager.addAlloy(bronze);
    var noxp =
        new Alloy(
            "none",
            "None",
            new AlloyData(
                IngredientLoader.getByString("none"),
                new net.tfminecraft.advancedcrafting.objects.data.StatData(),
                new HashMap<>(),
                null));
    AlloyManager.addAlloy(noxp);
    station
        .getCurrentMaterials()
        .putAll(
            Map.of(
                "ingredient.iron",
                2,
                "ingredient.copper",
                1,
                "ingredient.zero",
                1,
                "ingredient.none",
                1,
                "ingredient.missing",
                1,
                "alloy.bronze",
                3,
                "alloy.none",
                1,
                "alloy.missing",
                1,
                "unknown.value",
                1));
    try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      bukkit.when(() -> Bukkit.dispatchCommand(any(), anyString())).thenReturn(true);
      invoke(station, "giveXP", new Class[] {Player.class}, p);
      bukkit.verify(
          () ->
              Bukkit.dispatchCommand(
                  any(), eq("mmocore admin exp give " + p.getName() + " smith 6.25")));
    }
  }

  @Test
  void alloyMajorityNamesModelsHistoryAndGemSocketsReachFinalItem() throws Exception {
    var p = server.addPlayer();
    var iron = ingredient("iron", "");
    var location = loc();
    QualityLoader.map.put("fine", new Quality("fine", yaml("name: Fine\namount: 0\nvalue: 0")));
    SocketGroupLoader.map.put(
        "gemstones", new SocketGroup("gemstones", yaml("slots:\n  fine: ['Red']")));
    for (String name : List.of("Bronze", "§x", "§x§1§2§3§4§5§6Bronze")) {
      var data =
          new AlloyData(
              iron,
              new net.tfminecraft.advancedcrafting.objects.data.StatData(),
              new HashMap<>(),
              null);
      var alloy = new Alloy("bronze", name, data);
      AlloyManager.addAlloy(alloy);
      var station =
          new CraftingStation(
              location,
              recipe("recipe: ['metal.2']\nmodel-type: metal"),
              new HashMap<>(Map.of("alloy.bronze", 1, "ingredient.iron", 1)),
              new HashMap<>());
      try (var nbt = mockStatic(NBTItem.class);
          var models = mockStatic(LegacyModelData.class);
          var mmos =
              mockConstruction(
                  LiveMMOItem.class,
                  withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                  (mmo, ctx) -> {
                    when(mmo.getData(ItemStats.NAME)).thenReturn(new StringData("Old"));
                    var history = mock(StatHistory.class);
                    when(history.getOriginalData()).thenReturn(new NameData("Original"));
                    when(mmo.computeStatHistory(ItemStats.NAME)).thenReturn(history);
                    when(mmo.newBuilder().build()).thenReturn(new ItemStack(Material.IRON_SWORD));
                  })) {
        assertEquals(StationFeedback.SUCCESS, station.craft(p, 75.));
        var mmo = mmos.constructed().getFirst();
        verify(mmo).setStatHistory(eq(ItemStats.NAME), any());
        verify(mmo).setData(eq(ItemStats.GEM_SOCKETS), any(GemSocketsData.class));
      }
    }
  }

  @Test
  void appearanceSupportsUnnamedIngredientAndOptionalOrAlternateModelTypes() throws Exception {
    var p = server.addPlayer();
    var location = loc();
    var unnamed =
        new Ingredient("plain", yaml("path: v.iron_ingot\ntype: metal")) {
          @Override
          public ItemStack build() {
            return new ItemStack(Material.IRON_INGOT);
          }
        };
    IngredientLoader.oList.add(unnamed);
    QualityLoader.map.put("fine", new Quality("fine", yaml("name: Fine\namount: 0\nvalue: 0")));
    var alloy =
        new Alloy(
            "bronze",
            "§x§1§2§3§4§5§6Bronze",
            new AlloyData(
                unnamed,
                new net.tfminecraft.advancedcrafting.objects.data.StatData(),
                new HashMap<>(),
                null));
    AlloyManager.addAlloy(alloy);
    for (String extra :
        List.of(
            "recipe: ['metal.1']",
            "recipe: ['metal.1']\nmodel-type: wood",
            "recipe: ['metal.1']\nname: '§x§1§2§3§4§5§6%material% Sword'"))
      for (String key : List.of("ingredient.plain", "alloy.bronze")) {
        var station =
            new CraftingStation(
                location, recipe(extra), new HashMap<>(Map.of(key, 1)), new HashMap<>());
        try (var nbt = mockStatic(NBTItem.class);
            var models = mockStatic(LegacyModelData.class);
            var mmos =
                mockConstruction(
                    LiveMMOItem.class,
                    withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                    (mmo, ctx) -> {
                      when(mmo.getData(ItemStats.NAME)).thenReturn(new StringData("Old"));
                      when(mmo.computeStatHistory(ItemStats.NAME)).thenReturn(null);
                      when(mmo.newBuilder().build()).thenReturn(new ItemStack(Material.IRON_SWORD));
                    })) {
          assertEquals(StationFeedback.SUCCESS, station.craft(p, 50.));
        }
      }
    SchemeLoader.models.clear();
    var noModel = ingredient("unmodeled", "");
    var station =
        new CraftingStation(
            location,
            recipe("recipe: ['metal.1']"),
            new HashMap<>(Map.of("ingredient.unmodeled", 1)),
            new HashMap<>());
    try (var nbt = mockStatic(NBTItem.class);
        var mmos =
            mockConstruction(
                LiveMMOItem.class,
                withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                (mmo, ctx) -> {
                  when(mmo.getData(ItemStats.NAME)).thenReturn(new StringData("Old"));
                  when(mmo.computeStatHistory(ItemStats.NAME)).thenReturn(null);
                  when(mmo.newBuilder().build()).thenReturn(new ItemStack(Material.IRON_SWORD));
                })) {
      assertEquals(StationFeedback.SUCCESS, station.craft(p, 50.));
    }
    assertTrue(
        new CraftingStation(location, recipe(""), new HashMap<>(), new HashMap<>())
            .hasAllMaterials(p));
  }

  @Test
  void damagedStationCannotCraftFromAnUnknownMaterialKindAndRefundsKnownInputs() throws Exception {
    var p = server.addPlayer();
    var location = loc();
    var station =
        new CraftingStation(
            location, recipe(""), new HashMap<>(Map.of("unknown.removed", 1)), new HashMap<>());
    QualityLoader.map.put("fine", new Quality("fine", yaml("name: Fine\namount: 0\nvalue: 0")));
    try (var nbt = mockStatic(NBTItem.class);
        var mmos =
            mockConstruction(
                LiveMMOItem.class,
                withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                (mmo, ctx) -> {
                  when(mmo.getData(ItemStats.NAME)).thenReturn(new StringData("Old"));
                  when(mmo.computeStatHistory(ItemStats.NAME)).thenReturn(null);
                  when(mmo.newBuilder().build()).thenReturn(new ItemStack(Material.IRON_SWORD));
                })) {
      assertEquals(StationFeedback.NOT_INGREDIENT, station.craft(p, 50.));
    }
    ingredient("iron", "");
    station.getCurrentMaterials().put("ingredient.iron", 2);
    assertDoesNotThrow(() -> station.drop());
    assertDoesNotThrow(() -> station.drop(1));
    assertEquals(2, location.getWorld().getEntities().size());
  }

  @Test
  void extraUnknownMaterialDoesNotReplaceSelectedModel() throws Exception {
    var p = server.addPlayer();
    var iron = ingredient("iron", "");
    var location = loc();
    var station =
        new CraftingStation(
            location,
            recipe("recipe: ['metal.1']\nmodel-type: metal"),
            new HashMap<>(Map.of("ingredient.iron", 1, "unknown.removed", 1)),
            new HashMap<>());
    QualityLoader.map.put("fine", new Quality("fine", yaml("name: Fine\namount: 0\nvalue: 0")));
    try (var nbt = mockStatic(NBTItem.class);
        var models = mockStatic(LegacyModelData.class);
        var mmos =
            mockConstruction(
                LiveMMOItem.class,
                withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                (mmo, ctx) -> {
                  when(mmo.getData(ItemStats.NAME)).thenReturn(new StringData("Old"));
                  when(mmo.computeStatHistory(ItemStats.NAME)).thenReturn(null);
                  when(mmo.newBuilder().build()).thenReturn(new ItemStack(Material.IRON_SWORD));
                })) {
      assertEquals(StationFeedback.SUCCESS, station.craft(p, 50.));
    }
  }

  @Test
  void refundSkipsUnknownKindsWhilePreservingKnownQuantities() throws Exception {
    var location = loc();
    ingredient("iron", "");
    var station = new CraftingStation(location);
    station.getCurrentMaterials().putAll(Map.of("ingredient.iron", 2, "unknown.removed", 1));
    assertDoesNotThrow(() -> station.drop());
    assertDoesNotThrow(() -> station.drop(1));
    assertEquals(2, location.getWorld().getEntities().size());
  }
}
