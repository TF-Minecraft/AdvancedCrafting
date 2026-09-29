package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.stats.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BridgeCoverageTest extends CoverageSupport {
  @Test
  void bridgeValueTotalsRespectRecipesQuantitiesAndMissingDefinitions() throws Exception {
    var base = ingredient("iron", "base: true\ntier: 2\nvalue: 3");
    var gem = ingredient("gem", "value: 4");
    var recipe = new AlloyRecipe("iron", List.of("gem", "unknown"));
    var data = new AlloyData(base, new StatData(), new HashMap<>(), null);
    data.setRecipe(recipe);
    var alloy = new Alloy("bronze", "Bronze", data);
    AlloyManager.addAlloy(alloy);
    assertEquals(0, ThieveryBridge.sumForgeInputValues(null));
    assertEquals(0, ThieveryBridge.sumForgeInputValues(new AlloyRecipe("unknown", List.of())));
    assertEquals(7, ThieveryBridge.sumForgeInputValues(recipe));
    assertEquals(0, ThieveryBridge.sumAlloyIngredientValues(null));
    assertEquals(0, ThieveryBridge.sumAlloyIngredientValues(mock(Alloy.class)));
    assertEquals(7, ThieveryBridge.sumAlloyIngredientValues(alloy));
    assertEquals(0, ThieveryBridge.sumProvenanceInputValues(null));
    assertEquals(0, ThieveryBridge.sumProvenanceInputValues(List.of()));
    assertEquals(
        20,
        ThieveryBridge.sumProvenanceInputValues(
            List.of(
                new CraftInput("ingredient", "iron", 2, 0),
                new CraftInput("ingredient", "missing", 2, 0),
                new CraftInput("alloy", "bronze", 2, 0),
                new CraftInput("alloy", "missing", 2, 0),
                new CraftInput("other", "x", 2, 0))));
    assertSame(base, ThieveryBridge.getIngredientById("iron"));
    assertSame(IngredientLoader.get(), ThieveryBridge.getAllIngredients());
    assertSame(CategoryLoader.get(), ThieveryBridge.getRecipeCategories());
    assertSame(TypeLoader.map.get("metal"), ThieveryBridge.getIngredientType("metal"));
    var craft = recipe("stat-template: blade");
    RecipeLoader.map.put("sword", craft);
    var template = new StatTemplate("blade", yaml(""));
    StatTemplateLoader.get().put("blade", template);
    var quality = new Quality("fine", yaml("name: Fine"));
    QualityLoader.map.put("fine", quality);
    assertSame(craft, ThieveryBridge.getRecipeById("sword"));
    assertSame(template, ThieveryBridge.getStatTemplate("blade"));
    assertSame(quality, ThieveryBridge.getQualityById("fine"));
    assertEquals(
        2,
        ThieveryBridge.resolveMajorityTier(
            craft, List.of(new CraftInput("ingredient", "iron", 2, 0))));
    assertNull(ThieveryBridge.findRecipeByStatTemplate(null));
    assertNull(ThieveryBridge.findRecipeByStatTemplate(" "));
    assertNull(ThieveryBridge.findRecipeByStatTemplate("missing"));
    assertSame(craft, ThieveryBridge.findRecipeByStatTemplate("BLADE"));
    RecipeLoader.map.put("none", recipe(""));
    assertNull(ThieveryBridge.findRecipeByStatTemplate("missing"));
    assertFalse(ThieveryBridge.hasBaseIngredientForType(null, 2));
    assertFalse(ThieveryBridge.hasBaseIngredientForType(" ", 2));
    assertFalse(ThieveryBridge.hasBaseIngredientForType("metal", 0));
    assertTrue(ThieveryBridge.hasBaseIngredientForType("METAL", 2));
    assertFalse(ThieveryBridge.hasBaseIngredientForType("metal", 3));
    assertFalse(ThieveryBridge.hasBaseIngredientForType("wood", 2));
    ingredient("untiered", "base: true");
    assertFalse(ThieveryBridge.hasBaseIngredientForType("metal", 4));
    assertEquals("", ThieveryBridge.normalizeCraftCategoryId(null));
    assertEquals("armor", ThieveryBridge.normalizeCraftCategoryId("ARMOUR"));
    assertEquals("weapons", ThieveryBridge.normalizeCraftCategoryId("WEAPONS"));
  }

  @Test
  void integrationRequiresEnabledPluginAndIngredientManager() throws Exception {
    assertFalse(ThieveryBridge.isPluginReady());
    assertNull(ThieveryBridge.resolveIngredient(new ItemStack(Material.PAPER)));
    assertNull(ThieveryBridge.resolveAlloy(null));
    assertNull(ThieveryBridge.readProvenance(null));
    try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      var pm = mock(org.bukkit.plugin.PluginManager.class);
      bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
      when(pm.getPlugin("AdvancedCrafting")).thenReturn(plugin);
      assertFalse(ThieveryBridge.isPluginReady());
      when(plugin.isEnabled()).thenReturn(true);
      AdvancedCrafting.plugin = null;
      assertFalse(ThieveryBridge.isPluginReady());
      AdvancedCrafting.plugin = plugin;
      assertFalse(ThieveryBridge.isPluginReady());
      var manager = mock(IngredientManager.class);
      when(plugin.getIngredientManager()).thenReturn(manager);
      assertTrue(ThieveryBridge.isPluginReady());
      assertNull(ThieveryBridge.resolveIngredient(null));
      assertNull(ThieveryBridge.resolveIngredient(new ItemStack(Material.AIR)));
      assertNull(ThieveryBridge.resolveAlloy(null));
      assertNull(ThieveryBridge.readProvenance(null));
      var iron = ingredient("iron", "");
      var item = iron.build();
      assertSame(iron, ThieveryBridge.resolveIngredient(item));
      var plain = new ItemStack(Material.IRON_INGOT);
      when(manager.getFromItem(plain)).thenReturn(iron);
      assertSame(iron, ThieveryBridge.resolveIngredient(plain));
      assertNull(ThieveryBridge.resolveAlloy(plain));
      assertNull(ThieveryBridge.readProvenance(plain));
    }
  }

  @Test
  void craftingStatCalculatorRoutesLiveInputsThroughTemplate() throws Exception {
    var template = new StatTemplate("blade", yaml("stats: [armor]\nbase-stats: ['health(4)']"));
    StatTemplateLoader.get().put("blade", template);
    var craft = recipe("stat-template: blade");
    ingredient("iron", "stats: ['armor(6)']");
    assertEquals(
        6,
        CraftStatCalculator.compute(craft, Map.of("ingredient.iron", 1))
            .getModifiers()
            .getFirst()
            .getAmount());
    assertEquals(
        2,
        CraftStatCalculator.compute(craft, List.of(new CraftInput("ingredient", "iron", 1, 0)))
            .getModifiers()
            .size());
    assertFalse(CraftStatCalculator.compute(null, Map.of()).hasModifiers());
    assertFalse(CraftStatCalculator.compute(null, List.of()).hasModifiers());
    assertFalse(CraftStatCalculator.compute(recipe(""), Map.of()).hasModifiers());
    assertEquals(Set.of("armor", "health"), CraftStatCalculator.collectManagedStatIds(craft));
    assertTrue(CraftStatCalculator.collectManagedStatIds(null).isEmpty());
    assertTrue(CraftStatCalculator.collectManagedStatIds(recipe("")).isEmpty());
  }
}
