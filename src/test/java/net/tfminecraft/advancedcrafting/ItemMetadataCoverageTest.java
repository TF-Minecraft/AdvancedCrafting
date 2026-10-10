package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.objects.stats.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class ItemMetadataCoverageTest extends CoverageSupport {
  ItemStack tagged(NamespacedKey key, String value) {
    var item = new ItemStack(Material.IRON_INGOT);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
    item.setItemMeta(meta);
    return item;
  }

  @Test
  void ingredientBuildingPreservesCustomNamesAndTracksLoreRevision() throws Exception {
    var ingredient = ingredient("iron", "base: true\ntier: 3\nstats: ['attack(3)']");
    ingredient.setRevision(2);
    var item = ingredient.build();
    assertEquals(Material.IRON_INGOT, item.getType());
    assertTrue(item.getItemMeta().hasDisplayName());
    assertEquals(AcItemTags.Kind.INGREDIENT, AcItemTags.getKind(item));
    assertEquals("iron", AcItemTags.getId(item));
    assertEquals(2, AcItemTags.getStoredRevision(item));
    assertEquals(0, AcItemTags.getLoreStart(item));
    assertEquals(3, AcItemTags.getLoreLen(item));
    assertTrue(AcItemTags.hasStatsLoreFlag(item));
    assertTrue(AcItemTags.getStatsLore(item));
    var meta = item.getItemMeta();
    meta.setDisplayName("Custom");
    meta.setLore(List.of("Story"));
    item.setItemMeta(meta);
    ingredient.buildTo(item);
    assertEquals("Custom", item.getItemMeta().getDisplayName());
    assertEquals("Story", item.getItemMeta().getLore().getFirst());
    assertEquals(2, AcItemTags.getLoreStart(item));
    var bare = new ItemStack(Material.IRON_INGOT);
    assertNull(AcItemTags.getKind(null));
    assertNull(AcItemTags.getKind(bare));
    assertNull(AcItemTags.getId(bare));
    assertEquals(0, AcItemTags.getStoredRevision(bare));
    assertEquals(-1, AcItemTags.getLoreStart(bare));
    assertEquals(-1, AcItemTags.getLoreLen(bare));
    assertFalse(AcItemTags.hasStatsLoreFlag(null));
    assertFalse(AcItemTags.hasStatsLoreFlag(bare));
    assertFalse(AcItemTags.getStatsLore(bare));
    bare = tagged(PDCKeys.alloyId(), "bronze");
    assertEquals(AcItemTags.Kind.ALLOY, AcItemTags.getKind(bare));
    assertEquals("bronze", AcItemTags.getId(bare));
    assertEquals(0, AcItemTags.getStoredRevision(bare));
    assertEquals(-1, AcItemTags.getLoreStart(bare));
    assertEquals(-1, AcItemTags.getLoreLen(bare));
    assertFalse(AcItemTags.getStatsLore(bare));
    Cache.showIngredientStats = false;
    AcItemTags.write(bare, 7, new IngredientLore.Block(1, 2));
    assertFalse(AcItemTags.getStatsLore(bare));
    AcItemTags.write((ItemStack) null, 0, new IngredientLore.Block(0, 0));
    AcItemTags.write(new ItemStack(Material.AIR), 0, new IngredientLore.Block(0, 0));
  }

  @Test
  void loreSplicingPreservesAdjacentTextAndSupportsAllTierFormats() throws Exception {
    var base = ingredient("iron", "base: true\ntier: 2\nstats: ['armor(2)']").getIngredientData();
    for (List<String> initial :
        List.of(List.<String>of(), List.of("§7 ", "keep"), List.of("keep"))) {
      var lore = new ArrayList<>(initial);
      var block = IngredientLore.applyTypeAndRole(lore, base);
      assertEquals(3, block.length);
      assertTrue(lore.get(block.start).contains("Type:"));
      var replaced = IngredientLore.spliceTypeAndRole(lore, block.start, block.length, base);
      assertEquals(block.start, replaced.start);
    }
    var lore = new ArrayList<String>();
    lore.add(null);
    IngredientLore.appendTypeAndRole(lore, base);
    assertTrue(lore.getFirst().contains("Type:"));
    assertEquals(0, IngredientLore.spliceTypeAndRole(lore, -1, -2, base).start);
    int size = lore.size();
    assertEquals(size, IngredientLore.spliceTypeAndRole(lore, 100, 2, base).start);
    assertEquals(
        3,
        IngredientLore.applyAlloyLore(new ArrayList<>(), base.getType(), 4, base.getStatData())
            .length);
    assertEquals(
        2, IngredientLore.spliceAlloyLore(new ArrayList<>(), 0, 2, base.getType(), 1, null).length);
    assertEquals(
        2,
        IngredientLore.applyAlloyLore(new ArrayList<>(), base.getType(), 1, new StatData()).length);
    Cache.showIngredientStats = false;
    assertEquals(2, IngredientLore.applyTypeAndRole(new ArrayList<>(), base).length);
    for (int tier : List.of(1, 2, 3, 4, 5))
      assertTrue(IngredientLore.formatTierLine(tier).contains("Tier"));
    assertTrue(IngredientLore.formatCatalystLine().contains("Catalyst"));
    var noTier = ingredient("copper", "base: true").getIngredientData();
    assertTrue(IngredientLore.applyTypeAndRole(new ArrayList<>(), noTier).length > 0);
    assertEquals(1, IngredientLore.resolveAlloyTier(null, "none"));
    assertEquals(1, IngredientLore.resolveAlloyTier(new AlloyRecipe("missing", List.of()), "none"));
    assertEquals(1, IngredientLore.resolveAlloyTier(new AlloyRecipe("copper", List.of()), "none"));
    assertEquals(2, IngredientLore.resolveAlloyTier(new AlloyRecipe("iron", List.of()), "steel"));
  }

  @Test
  void scrapProvenanceAndCraftStackRecognizeIndependentTags() throws Exception {
    ItemStack bare = new ItemStack(Material.IRON_NUGGET);
    ScrapProvenance.applyTo(null, "iron");
    ScrapProvenance.applyTo(bare, null);
    ScrapProvenance.applyTo(bare, " ");
    ScrapProvenance.applyTo(new ItemStack(Material.AIR), "iron");
    assertNull(ScrapProvenance.readBaseId(null));
    assertNull(ScrapProvenance.readBaseId(bare));
    ScrapProvenance.applyTo(bare, "IRON");
    assertEquals("iron", ScrapProvenance.readBaseId(bare));
    for (ItemStack item :
        Arrays.asList(null, new ItemStack(Material.AIR), new ItemStack(Material.IRON_INGOT))) {
      var stack = new CraftStack(item);
      assertFalse(stack.isIngredient());
      assertFalse(stack.isAlloy());
      assertFalse(stack.isCrafted());
      assertNull(stack.getIngredient());
      assertNull(stack.getAlloy());
      assertFalse(stack.hasOutdatedInputs());
      assertTrue(stack.getOutdatedInputs().isEmpty());
    }
    var ingredient = ingredient("iron", "");
    var stack = new CraftStack(ingredient.build());
    assertTrue(stack.isIngredient());
    assertSame(ingredient, stack.getIngredient());
    assertFalse(stack.isCrafted());
    assertFalse(stack.hasOutdatedInputs());
    var alloy =
        new Alloy(
            "bronze", "Bronze", new AlloyData(ingredient, new StatData(), new HashMap<>(), null));
    AlloyManager.addAlloy(alloy);
    stack = new CraftStack(tagged(PDCKeys.alloyId(), "bronze"));
    assertTrue(stack.isAlloy());
    assertSame(alloy, stack.getAlloy());
  }

  @Test
  void craftProvenanceRoundTripsAndDetectsIngredientAndTemplateRevisions() throws Exception {
    var ingredient = ingredient("iron", "");
    ingredient.setRevision(3);
    var alloy =
        new Alloy(
            "bronze", "Bronze", new AlloyData(ingredient, new StatData(), new HashMap<>(), null));
    alloy.setRevision(2);
    AlloyManager.addAlloy(alloy);
    var template = new StatTemplate("blade", yaml("stats: [attack]"));
    template.setRevision(4);
    StatTemplateLoader.get().put("blade", template);
    var recipe = recipe("stat-template: blade");
    RecipeLoader.map.put("sword", recipe);
    var quality = new Quality("fine", yaml("name: Fine"));
    var materials =
        new HashMap<String, Integer>(
            Map.of(
                "invalid",
                1,
                "ingredient.iron",
                2,
                "ingredient.missing",
                1,
                "alloy.bronze",
                1,
                "alloy.missing",
                1,
                "other.thing",
                1));
    var provenance = CraftProvenance.from(recipe, materials, quality);
    assertEquals("sword", provenance.getRecipeId());
    assertEquals("fine", provenance.getQualityId());
    assertEquals(5, provenance.getInputs().size());
    assertEquals(4, provenance.getStatTemplateRevision());
    assertFalse(provenance.isOutdated());
    ingredient.setRevision(4);
    assertTrue(provenance.isOutdated());
    assertEquals(1, provenance.getOutdatedInputs().size());
    var item = new ItemStack(Material.IRON_SWORD);
    provenance.applyTo(item);
    assertEquals(5, CraftProvenance.readFrom(item).getInputs().size());
    var stack = new CraftStack(item);
    assertTrue(stack.isCrafted());
    assertTrue(stack.hasOutdatedInputs());
    assertEquals(1, stack.getOutdatedInputs().size());
    provenance.syncInputRevisions();
    assertFalse(provenance.isOutdated());
    template.setRevision(5);
    assertTrue(provenance.isStatTemplateOutdated());
    assertTrue(provenance.isOutdated());
    provenance.syncRevisions();
    assertFalse(provenance.isOutdated());
    RecipeLoader.map.clear();
    provenance.syncStatTemplateRevision();
    assertEquals(0, provenance.getStatTemplateRevision());
    assertFalse(provenance.isStatTemplateOutdated());
    assertEquals("", CraftProvenance.from(recipe(""), new HashMap<>(), null).getQualityId());
    provenance.applyTo(null);
    provenance.applyTo(new ItemStack(Material.AIR));
    assertNull(CraftProvenance.readFrom(null));
    assertNull(CraftProvenance.readFrom(new ItemStack(Material.IRON_SWORD)));
    assertNull(CraftProvenance.readFrom(tagged(PDCKeys.alloyId(), "bronze")));
    var minimal = tagged(PDCKeys.craftRecipe(), "sword");
    var parsed = CraftProvenance.readFrom(minimal);
    assertEquals("", parsed.getQualityId());
    assertTrue(parsed.getInputs().isEmpty());
    assertEquals(0, parsed.getStatTemplateRevision());
    var meta = minimal.getItemMeta();
    meta.getPersistentDataContainer().set(PDCKeys.craftInputs(), PersistentDataType.STRING, "null");
    minimal.setItemMeta(meta);
    assertTrue(CraftProvenance.readFrom(minimal).getInputs().isEmpty());
    // Malformed records (null entries, kinds or ids) are dropped instead of breaking every refresh.
    meta = minimal.getItemMeta();
    meta.getPersistentDataContainer()
        .set(
            PDCKeys.craftInputs(),
            PersistentDataType.STRING,
            "[null,{\"id\":\"iron\",\"n\":1},{\"k\":\"ingredient\",\"n\":1},"
                + "{\"k\":\"ingredient\",\"id\":\"iron\",\"n\":2,\"r\":1}]");
    minimal.setItemMeta(meta);
    var kept = CraftProvenance.readFrom(minimal).getInputs();
    assertEquals(1, kept.size());
    assertEquals("iron", kept.get(0).getId());
    // A record whose recipe no longer resolves loses its old scheme tag.
    meta = minimal.getItemMeta();
    meta.getPersistentDataContainer().set(PDCKeys.craftModelScheme(), PersistentDataType.STRING, "old");
    minimal.setItemMeta(meta);
    CraftProvenance.readFrom(minimal).applyTo(minimal);
    assertNull(
        minimal.getItemMeta().getPersistentDataContainer().get(PDCKeys.craftModelScheme(), PersistentDataType.STRING));
    assertNotNull(new CraftProvenance().getInputs());
  }

  @Test
  void tierLoreUsesFreeLinesAndRefreshesTheRecordedSlot() {
    for (var initial :
        List.of(List.<String>of(), List.of(" "), List.of("keep", " "), List.of("keep"))) {
      var lore = new ArrayList<>(initial);
      int at = CraftTierLore.insertTierLine(lore, 2);
      assertTrue(lore.get(at).contains("Tier"));
      CraftTierLore.updateTierLine(lore, at, 3);
      assertTrue(lore.get(at).contains("III"));
    }
    var lore = new ArrayList<String>();
    lore.add(null);
    assertEquals(0, CraftTierLore.insertTierLine(lore, 1));
    assertEquals(-1, CraftTierLore.insertTierLine(lore, 0));
    CraftTierLore.updateTierLine(lore, -1, 2);
    CraftTierLore.updateTierLine(lore, 2, 0);
    CraftTierLore.updateTierLine(lore, 4, 4);
    assertEquals(5, lore.size());
    for (ItemStack item : Arrays.asList(null, new ItemStack(Material.PAPER))) {
      CraftTierLore.applyPdc(item, 0, 1);
      CraftTierLore.applyTierLine(item, 1);
      CraftTierLore.refreshTierLine(item, 1);
    }
    var item = tagged(PDCKeys.craftRecipe(), "sword");
    CraftTierLore.applyPdc(item, -1, 1);
    CraftTierLore.applyPdc(item, 0, 0);
    CraftTierLore.applyTierLine(item, 0);
    CraftTierLore.refreshTierLine(item, 0);
    CraftTierLore.refreshTierLine(item, 1);
    CraftTierLore.applyTierLine(item, 1);
    CraftTierLore.applyTierLine(item, 2);
    CraftTierLore.refreshTierLine(item, 3);
    assertTrue(item.getItemMeta().getLore().getFirst().contains("III"));
    var meta = item.getItemMeta();
    meta.setLore(null);
    item.setItemMeta(meta);
    CraftTierLore.refreshTierLine(item, 4);
    assertTrue(item.getItemMeta().getLore().getFirst().contains("IV"));
  }

  @Test
  void itemLoreRefreshPreservesStackSizeAndUserText() throws Exception {
    var ingredient = ingredient("iron", "base: true\ntier: 2");
    ingredient.setRevision(1);
    var item = ingredient.build();
    item.setAmount(7);
    assertFalse(AcItemLoreRefresher.refreshIfOutdated(item).isChanged());
    ingredient.setRevision(2);
    assertTrue(AcItemLoreRefresher.isOutdated(item));
    var result = AcItemLoreRefresher.refreshIfOutdated(item);
    assertTrue(result.isChanged());
    assertNull(result.getError());
    assertEquals(7, result.getItem().getAmount());
    assertEquals(2, AcItemTags.getStoredRevision(result.getItem()));
    item = tagged(PDCKeys.ingredientId(), "iron");
    assertTrue(AcItemLoreRefresher.isOutdated(item));
    assertTrue(AcItemLoreRefresher.refresh(item).isChanged());
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(PDCKeys.loreStart(), PersistentDataType.INTEGER, 0);
    item.setItemMeta(meta);
    assertTrue(AcItemLoreRefresher.isOutdated(item));
    Cache.showIngredientStats = false;
    assertTrue(AcItemLoreRefresher.isOutdated(result.getItem()));
    assertTrue(
        AcItemLoreRefresher.refresh(tagged(PDCKeys.ingredientId(), "missing"))
            .getError()
            .contains("unknown ingredient"));
    for (ItemStack invalid :
        Arrays.asList(
            null,
            new ItemStack(Material.AIR),
            new ItemStack(Material.PAPER),
            tagged(PDCKeys.craftRecipe(), "x"))) {
      assertFalse(AcItemLoreRefresher.isOutdated(invalid));
      assertFalse(AcItemLoreRefresher.refreshIfOutdated(invalid).isChanged());
      assertFalse(AcItemLoreRefresher.refresh(invalid).isChanged());
    }
    assertTrue(AcItemRefresher.isManaged(ingredient.build()));
    assertTrue(AcItemRefresher.isOutdated(item));
    assertNotSame(item, AcItemRefresher.refreshIfOutdated(item));
    for (ItemStack invalid :
        Arrays.asList(null, new ItemStack(Material.AIR), new ItemStack(Material.PAPER))) {
      assertFalse(AcItemRefresher.isManaged(invalid));
      assertFalse(AcItemRefresher.isOutdated(invalid));
      assertSame(invalid, AcItemRefresher.refreshIfOutdated(invalid));
    }
  }
}
