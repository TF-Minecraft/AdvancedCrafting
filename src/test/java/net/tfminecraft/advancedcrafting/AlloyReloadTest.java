package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.enums.StationFeedback;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingStation;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;
import net.tfminecraft.advancedcrafting.objects.schemes.*;
import net.tfminecraft.advancedcrafting.utils.PDCKeys;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/** {@code ac reload} builds new types, hits and schemes; alloys loaded before it must keep working. */
class AlloyReloadTest extends CoverageSupport {
  ItemStack alloyItem(String id) {
    var i = new ItemStack(Material.IRON_INGOT);
    var m = i.getItemMeta();
    m.getPersistentDataContainer().set(PDCKeys.alloyId(), PersistentDataType.STRING, id);
    i.setItemMeta(m);
    return i;
  }

  Alloy alloy(String id, IngredientType type, HashMap<CraftingHit, Integer> hits) {
    var data =
        new AlloyData(
            SchemeLoader.colours.get("default"),
            2,
            type,
            SchemeLoader.models.get("default"),
            new StatData(),
            hits,
            null,
            null,
            1,
            null);
    var alloy = new Alloy(id, id, data);
    AlloyManager.addAlloy(alloy);
    return alloy;
  }

  void reloadTypesAndHits() throws Exception {
    TypeLoader.map.put("metal", new IngredientType("metal", yaml("name: Metal")));
    TypeLoader.hMap.put("hammer", new HitType("hammer", yaml("name: Hammer")));
    HitLoader.map.put(
        "strike", new CraftingHit("strike", yaml("name: Strike\ntype: hammer\ntool: v.iron_axe")));
  }

  @Test
  void alloyLoadedBeforeReloadStillFillsAStation() throws Exception {
    alloy(
        "darksteel",
        TypeLoader.map.get("metal"),
        new HashMap<>(Map.of(HitLoader.map.get("strike"), 2)));
    reloadTypesAndHits();
    var p = server.addPlayer();
    p.setOp(true);
    var station = new CraftingStation(new Location(server.addSimpleWorld("world"), 1, 2, 3));
    station.setRecipe(recipe("recipe: ['metal.1']"));
    var item = alloyItem("darksteel");
    p.getInventory().setItemInMainHand(item);
    assertEquals(StationFeedback.SUCCESS, station.addMaterial(p, item));
    assertTrue(station.hasAllMaterials(p));
    assertEquals(2, station.getHits().get(HitLoader.map.get("strike")).getNeeded());
    // `ac give equipment ... alloy.<id>` fills a station this way.
    var given =
        new CraftingStation(
            station.getLoc(),
            recipe("recipe: ['metal.4']"),
            new HashMap<>(Map.of("alloy.darksteel", 4)),
            new HashMap<>());
    assertEquals(4, given.getTypes().get(TypeLoader.map.get("metal")).getCurrent());
  }

  @Test
  void relinkSwapsInReloadedObjectsAndKeepsMissingOnes() throws Exception {
    var oldStrike = HitLoader.map.get("strike");
    var gone = new CraftingHit("gone", yaml("name: Gone\ntype: hammer\ntool: v.stick"));
    var hits = new HashMap<CraftingHit, Integer>();
    hits.put(oldStrike, 2);
    hits.put(gone, 1);
    hits.put(null, 4);
    var darksteel = alloy("darksteel", TypeLoader.map.get("metal"), hits).getData();
    var typeless = alloy("typeless", null, new HashMap<>()).getData();
    TypeLoader.map.put("wood", new IngredientType("wood", yaml("name: Wood")));
    var wooden = alloy("wooden", TypeLoader.map.get("wood"), new HashMap<>()).getData();
    var oldWood = TypeLoader.map.remove("wood");
    reloadTypesAndHits();
    SchemeLoader.colours.put(
        "default", new ColourScheme("default", yaml("models: [3]\ncolours: ['#000000']")));
    SchemeLoader.models.put("default", new ModelScheme("default", yaml("models: ['sword(8)']")));

    AlloyManager.relinkAlloys();

    assertSame(TypeLoader.map.get("metal"), darksteel.getType());
    assertSame(SchemeLoader.colours.get("default"), darksteel.getColourScheme());
    assertSame(SchemeLoader.models.get("default"), darksteel.getModelScheme());
    assertSame(hits, darksteel.getHits());
    assertEquals(3, hits.size());
    for (var hit : hits.keySet()) {
      if (hit != null && hit.getId().equals("strike")) assertSame(HitLoader.map.get("strike"), hit);
    }
    assertNotSame(oldStrike, HitLoader.map.get("strike"));
    assertEquals(2, hits.get(HitLoader.map.get("strike")));
    assertEquals(1, hits.get(gone));
    assertEquals(4, hits.get(null));
    assertNull(typeless.getType());
    assertSame(oldWood, wooden.getType());
  }

  @Test
  void typesAndHitsCompareById() throws Exception {
    var metal = new IngredientType("metal", yaml("name: Metal"));
    var renamed = new IngredientType("metal", yaml("name: Iron"));
    assertEquals(metal, renamed);
    assertEquals(metal.hashCode(), renamed.hashCode());
    assertNotEquals(metal, new IngredientType("wood", yaml("name: Metal")));
    assertNotEquals(metal, "metal");

    var hammer = new HitType("hammer", yaml("name: Hammer"));
    assertEquals(hammer, new HitType("hammer", yaml("name: Mallet")));
    assertEquals(hammer.hashCode(), new HitType("hammer", yaml("name: Mallet")).hashCode());
    assertNotEquals(hammer, new HitType("chisel", yaml("name: Hammer")));
    assertNotEquals(hammer, "hammer");

    var strike = new CraftingHit("strike", yaml("name: Strike\ntype: hammer\ntool: v.iron_axe"));
    var moved = new CraftingHit("strike", yaml("name: Blow\ntype: hammer\ntool: v.stick"));
    assertEquals(strike, moved);
    assertEquals(strike.hashCode(), moved.hashCode());
    assertNotEquals(strike, new CraftingHit("tap", yaml("name: Strike\ntype: hammer")));
    assertNotEquals(strike, "strike");
  }
}
