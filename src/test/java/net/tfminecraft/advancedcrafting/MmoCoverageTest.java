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
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.stats.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class MmoCoverageTest extends CoverageSupport {
  @Test
  void externalStatsReplaceOwnedValuesWithoutDuplicatingOriginalLayer() {
    var mmo = mock(MMOItem.class);
    var armor = mock(ItemStat.class);
    var speed = mock(ItemStat.class);
    var other = mock(ItemStat.class);
    when(MMOItems.plugin.getStats().get("ARMOR")).thenReturn(armor);
    when(MMOItems.plugin.getStats().get("SPEED")).thenReturn(speed);
    when(MMOItems.plugin.getStats().get("OTHER")).thenReturn(other);
    when(MMOItems.plugin.getStats().get("MISSING")).thenReturn(null);
    var hist = mock(StatHistory.class);
    var original = new DoubleData(5);
    when(hist.getOriginalData()).thenReturn(original);
    when(mmo.computeStatHistory(armor)).thenReturn(hist);
    var nonnumeric = mock(StatHistory.class);
    when(nonnumeric.getOriginalData()).thenReturn(new StringData("text"));
    when(mmo.computeStatHistory(other)).thenReturn(nonnumeric);
    var stats =
        new StatData(
            List.of("armor(3)", "ARMOR(4)", "durability(20)", "max_item_damage(40)", "missing(2)"));
    var managed = Set.of("armor", "speed", "other", "missing", "durability");
    MMOStatApplicator.applyExternalLayer(mmo, stats, managed, true);
    assertEquals(0, original.getValue());
    verify(hist).clearExternalData();
    verify(hist).registerExternalData(any(DoubleData.class));
    verify(mmo).setData(eq(ItemStats.MAX_DURABILITY), any(DoubleData.class));
    verify(mmo).setData(eq(ItemStats.CUSTOM_DURABILITY), any(DoubleData.class));
    MMOStatApplicator.applyExternalLayer(mmo, null, null, true);
    MMOStatApplicator.applyExternalLayer(mmo, new StatData(), Set.of("max_item_damage"));
    assertTrue(MMOStatApplicator.isDurabilityStat("MAX_ITEM_DAMAGE"));
    assertTrue(MMOStatApplicator.isDurabilityStat("DURABILITY"));
    assertFalse(MMOStatApplicator.isDurabilityStat("armor"));
  }

  @Test
  void craftedRefreshPreservesAmountSynchronizesProvenanceAndAppliesTier() throws Exception {
    Cache.debugStatRefresh = false;
    var item = new ItemStack(Material.IRON_SWORD, 3);
    var ing = ingredient("iron", "base: true\ntier: 3\nstats: ['armor(4)']");
    ing.setRevision(2);
    var template = new StatTemplate("blade", yaml("stats: [armor]"));
    template.setRevision(2);
    StatTemplateLoader.get().put("blade", template);
    var recipe = recipe("stat-template: blade");
    RecipeLoader.map.put("sword", recipe);
    var provenance =
        new CraftProvenance(
            "sword", "fine", List.of(new CraftInput("ingredient", "iron", 2, 1)), 1);
    provenance.applyTo(item);
    var rebuilt = new ItemStack(Material.IRON_SWORD);
    var nbt = mock(NBTItem.class);
    try (var nbts = mockStatic(NBTItem.class);
        var skins = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class);
        var models = mockStatic(net.tfminecraft.advancedcrafting.util.LegacyModelData.class);
        var autoUpdate = mockStatic(IaAutoUpdate.class);
        var mmos =
            mockConstruction(
                LiveMMOItem.class,
                withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                (mock, ctx) -> when(mock.newBuilder().build()).thenReturn(rebuilt))) {
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
      skins
          .when(
              () ->
                  net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.apply(
                      any(ItemStack.class), any(ItemStack.class)))
          .thenAnswer(call -> call.getArgument(1));
      var result = CraftStatRefresher.refreshIfOutdated(item);
      assertTrue(result.isChanged());
      assertNull(result.getError());
      assertEquals(1, result.getOutdatedInputs().size());
      assertEquals(3, result.getItem().getAmount());
      assertEquals(2, CraftProvenance.readFrom(result.getItem()).getStatTemplateRevision());
      assertFalse(CraftProvenance.readFrom(result.getItem()).isOutdated());
      assertTrue(AcItemRefresher.isManaged(item));
      assertTrue(AcItemRefresher.isOutdated(item));
      assertEquals(rebuilt, AcItemRefresher.refreshIfOutdated(item));
      assertFalse(CraftStatRefresher.refresh(result.getItem()).isChanged());
      assertFalse(CraftStatRefresher.refreshIfOutdated(result.getItem()).isChanged());
      RecipeLoader.map.clear();
      assertTrue(CraftStatRefresher.refresh(item, true).getError().contains("unknown recipe"));
      var empty = recipe("");
      RecipeLoader.map.put("sword", empty);
      new CraftProvenance("sword", "", List.of(), 0).applyTo(item);
      assertTrue(CraftStatRefresher.refresh(item, true).isChanged());
    }
    assertFalse(CraftStatRefresher.refresh(null).isChanged());
    assertFalse(CraftStatRefresher.refresh(new ItemStack(Material.AIR)).isChanged());
    assertFalse(CraftStatRefresher.refresh(new ItemStack(Material.PAPER)).isChanged());
    assertTrue(CraftStatRefresher.RefreshResult.updated(item, null).getOutdatedInputs().isEmpty());
  }

  @Test
  void debugReportsComputedOriginalAndExternalStatLayers() throws Exception {
    Cache.debugStatRefresh = false;
    StatRefreshDebug.logBefore(null, null, null, null);
    StatRefreshDebug.logAfter(null, null);
    Cache.debugStatRefresh = true;
    var ingredient = ingredient("iron", "");
    ingredient.setRevision(2);
    var template =
        new StatTemplate("blade", yaml("stats: [armor, speed, health, durability, missing]"));
    StatTemplateLoader.get().put("blade", template);
    var recipe = recipe("stat-template: blade");
    RecipeLoader.map.put("sword", recipe);
    var provenance =
        new CraftProvenance(
            "sword",
            "",
            List.of(
                new CraftInput("ingredient", "iron", 1, 0),
                new CraftInput("ingredient", "iron", 1, 0)),
            0);
    var item = new ItemStack(Material.IRON_SWORD);
    var armor = mock(ItemStat.class);
    var speed = mock(ItemStat.class);
    var health = mock(ItemStat.class);
    when(MMOItems.plugin.getStats().get("ARMOR")).thenReturn(armor);
    when(MMOItems.plugin.getStats().get("SPEED")).thenReturn(speed);
    when(MMOItems.plugin.getStats().get("HEALTH")).thenReturn(health);
    when(MMOItems.plugin.getStats().get("MISSING")).thenReturn(null);
    try (var nbts = mockStatic(NBTItem.class);
        var mmos =
            mockConstruction(
                LiveMMOItem.class,
                (mock, ctx) -> {
                  when(mock.getData(armor)).thenReturn(new DoubleData(4));
                  var hist = mock(StatHistory.class);
                  when(hist.getOriginalData()).thenReturn(new DoubleData(2));
                  when(mock.computeStatHistory(armor)).thenReturn(hist);
                  var other = mock(StatHistory.class);
                  when(other.getOriginalData()).thenReturn(new StringData("text"));
                  when(mock.computeStatHistory(speed)).thenReturn(other);
                })) {
      StatRefreshDebug.logBefore(
          item, provenance, recipe, new StatData(List.of("armor(5)", "speed(1)")));
      StatRefreshDebug.logAfter(item, recipe);
      StatRefreshDebug.logBefore(item, provenance, recipe, null);
      StatRefreshDebug.logBefore(item, provenance, recipe(""), new StatData());
    } finally {
      Cache.debugStatRefresh = false;
    }
  }
}
