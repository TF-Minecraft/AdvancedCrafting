package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.objects.schemes.*;
import net.tfminecraft.advancedcrafting.objects.stats.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.junit.jupiter.api.Test;

class DomainCoverageTest extends CoverageSupport {
  @Test
  void statMergingCopiesInputsAndAveragesByUnits() {
    var source = new StatData(List.of("attack(1.23)", "speed(-2)"));
    assertTrue(source.hasModifiers());
    assertFalse(new StatData().hasModifiers());
    var m = source.getModifiers().getFirst();
    assertEquals("attack", m.getType());
    assertEquals(1.23, m.copy().getAmount());
    m.setAmount(2);
    m.modify(.127);
    assertEquals(2.13, m.getAmount());
    assertEquals(0, source.getAmount(new StatModifier("unknown", 4)));
    source.addModifier(new StatModifier("ATTACK", 1));
    source.mergeFrom(new StatData(List.of("speed(1)", "armor(4)")));
    assertEquals(3.13, source.getAmount(new StatModifier("attack", 0)));
    assertEquals(-1, source.getAmount(new StatModifier("speed", 0)));
    var merge = new MergeStatModifier("attack");
    assertEquals(0, merge.create().getAmount());
    assertEquals(0, merge.createWithDenominator(0).getAmount());
    merge.addWeighted(3, 2);
    merge.modify(6);
    assertEquals("attack", merge.getType());
    assertEquals(3, merge.getAmount());
    assertEquals(12, merge.getValue());
    assertEquals(4, merge.create().getAmount());
    assertEquals(2, merge.createWithDenominator(6).getAmount());
    assertEquals(
        3.13, new MergeStatModifier(source.getModifiers().getFirst()).create().getAmount());
  }

  @Test
  void schemeModelsAndIngredientDefaultsAndOverrides() throws Exception {
    var defaults = ingredient("iron", "");
    var d = defaults.getIngredientData();
    assertEquals(1, d.getWeight());
    assertEquals(1, d.getValue());
    assertFalse(d.canBeBase());
    assertEquals(0, d.getTier());
    assertFalse(d.hasTier());
    assertFalse(d.hasPermission());
    assertNull(d.getPermission());
    assertFalse(d.hasXP());
    assertNull(d.getXP());
    assertEquals("metal", d.getStatMergeBucketId());
    assertEquals("metal", d.getType().getId());
    assertEquals("Metal", d.getType().getName());
    assertFalse(d.statIsProtected(new StatModifier("attack", 1)));
    assertTrue(d.buildRevisionContent().contains("tier=0"));
    assertTrue(defaults.hasHex());
    assertEquals("#FFFFFF", defaults.getHex());
    assertEquals("v.iron_ingot", defaults.getPath());
    defaults.setRevision(4);
    assertEquals(4, defaults.getRevision());
    var full =
        ingredient(
            "steel",
            "weight: 3\n"
                + "value: 5\n"
                + "base: true\n"
                + "tier: 4\n"
                + "permission: ' Smith '\n"
                + "stat-merge-key: ' METALS '\n"
                + "xp: smith.5\n"
                + "stats: ['armor(2)']\n"
                + "hits: ['strike.2']\n"
                + "protected-stats: [armor]\n"
                + "hex: none");
    d = full.getIngredientData();
    assertEquals(3, d.getWeight());
    assertEquals(5, d.getValue());
    assertTrue(d.canBeBase());
    assertEquals(4, d.getTier());
    assertTrue(d.hasTier());
    assertTrue(d.hasPermission());
    assertEquals("smith", d.getPermission());
    assertEquals("metals", d.getStatMergeBucketId());
    assertTrue(d.hasXP());
    assertEquals("smith.5", d.getXP());
    assertTrue(d.statIsProtected(new StatModifier("armor", 2)));
    assertEquals(2, d.getHits().get(HitLoader.getByString("strike")));
    assertFalse(full.hasHex());
    assertTrue(d.buildRevisionContent().contains("hits=strike.2"));
    assertEquals(
        "legacy",
        ingredient("old", "permission: ' '\npermission-namespace: LEGACY")
            .getIngredientData()
            .getPermission());
    assertFalse(
        ingredient("blank", "permission-namespace: ' '\nstat-merge-key: ' '")
            .getIngredientData()
            .hasPermission());
    assertNull(
        ingredient("unknown", "type: unknown\nmodel-scheme: missing")
            .getIngredientData()
            .getStatMergeBucketId());
    var colour = d.getScheme().getColourScheme();
    assertEquals("default", colour.getId());
    assertEquals("v.IRON_INGOT", colour.getItem());
    assertEquals(List.of(2), colour.getModels());
    assertEquals(List.of("#FFFFFF"), colour.getHexCodes());
    assertEquals(2, colour.randomModel());
    assertEquals("#FFFFFF", colour.randomColour());
    assertEquals("default", d.getScheme().getId());
    assertEquals(List.of("Iron"), d.getScheme().getNames());
    assertEquals("default", d.getModelScheme().getId());
    assertEquals(List.of("sword(7)"), d.getModelScheme().getModels());
    assertEquals("7", d.getModelScheme().getModel("SWORD"));
    assertNull(d.getModelScheme().getModel("axe"));
    var namespace = new PermissionNamespace("SMITH", "Smithing");
    assertEquals("smith", namespace.getId());
    assertEquals("Smithing", namespace.getDisplay());
  }

  @Test
  void recipeKeysCanonicalizeAndRetainDuplicateCatalysts() {
    var r = new AlloyRecipe("IRON", List.of("ZINC", "copper", "ZINC"));
    assertEquals("iron", r.getBaseId());
    assertEquals(List.of("copper", "zinc", "zinc"), r.getCatalystIds());
    assertThrows(UnsupportedOperationException.class, () -> r.getCatalystIds().add("x"));
    assertEquals("iron|copper,zinc,zinc", r.comboKey());
    assertEquals("copper,zinc,zinc", r.catalystsJson());
    assertEquals("iron__copper__zinc__zinc", r.fileBaseName());
    assertEquals(
        temp.resolve("iron/iron__copper__zinc__zinc.idx").toFile(),
        r.resolveIndexFile(temp.toFile()));
    assertEquals(r, AlloyRecipe.fromComboKey(r.comboKey().toUpperCase()));
    assertEquals(r.hashCode(), AlloyRecipe.fromComboKey(r.comboKey()).hashCode());
    assertFalse(r.equals("iron"));
    assertFalse(r.matches(null));
    assertFalse(r.matches(new AlloyRecipe("copper", r.getCatalystIds())));
    assertFalse(r.matches(new AlloyRecipe("iron", List.of())));
    assertNull(AlloyRecipe.fromComboKey(null));
    assertNull(AlloyRecipe.fromComboKey(" "));
    assertEquals("iron", AlloyRecipe.fromComboKey("IRON").comboKey());
    assertEquals("iron", AlloyRecipe.fromComboKey("IRON|").fileBaseName());
    var input = new CraftInput("ingredient", "iron", 3, 2);
    input.setRevision(4);
    assertEquals("ingredient", input.getKind());
    assertEquals("iron", input.getId());
    assertEquals(3, input.getAmount());
    assertEquals(4, input.getRevision());
    assertNull(new CraftInput().getId());
  }

  @Test
  void recipesSocketGroupsAndQualityExposeConfiguration() throws Exception {
    var group = new SocketGroup("default", yaml("slots:\n  fine: [red, blue]"));
    assertEquals("default", group.getId());
    assertEquals(List.of("red", "blue"), group.getSlots("fine"));
    assertTrue(group.getSlots(null).isEmpty());
    assertTrue(group.getSlots("bad").isEmpty());
    assertEquals(1, group.getAllSlots().size());
    assertTrue(new SocketGroup("empty", yaml("")).getAllSlots().isEmpty());
    var recipe =
        recipe(
            "recipe: ['metal.2']\n"
                + "permission-namespace: SMITH\n"
                + "icon: v.iron_sword\n"
                + "model-type: sword\n"
                + "stat-template: missing");
    assertEquals("sword", recipe.getId());
    assertEquals("%material% Sword", recipe.getName());
    assertEquals("Sword", recipe.getCleanedName());
    assertEquals("SWORD.TEST", recipe.getTemplate());
    assertEquals("v.iron_sword", recipe.getIconPath());
    assertEquals("v.iron_sword", recipe.resolveMenuIconPath());
    assertEquals("smith", recipe.getType());
    assertEquals("metal", recipe.getMainType());
    assertEquals("sword", recipe.getModelType());
    assertEquals("gemstones", recipe.getSocketGroupId());
    assertEquals("smith", recipe.getPermissionNamespace());
    assertTrue(recipe.hasPermissionNamespace());
    assertEquals("missing", recipe.getStatTemplateId());
    assertNull(recipe.getStatTemplate());
    assertEquals(Map.of("metal", 2), recipe.getRecipe());
    assertEquals("weapons", recipe.getCategoryId());
    assertEquals("m.SWORD.TEST", recipe("").resolveMenuIconPath());
    assertEquals("m.SWORD.TEST", recipe("icon: ' '").resolveMenuIconPath());
    assertFalse(recipe("").hasPermissionNamespace());
    assertFalse(recipe("permission-namespace: ' '").hasPermissionNamespace());
    recipe("icon: invalid");
    var quality = new Quality("fine", yaml("name: Fine\namount: 4\nvalue: 3"));
    assertEquals("fine", quality.getId());
    assertEquals("Fine", quality.getName());
    assertEquals(4, quality.getAmount());
    assertEquals(3, quality.getValue());
    assertFalse(quality.isValid(3));
    assertTrue(quality.isValid(4));
    var hit = HitLoader.getByString("strike");
    assertEquals("strike", hit.getId());
    assertEquals("Strike", hit.getName());
    assertEquals("v.iron_axe", hit.getTool());
    assertEquals("hammer", hit.getType().getId());
    assertEquals("Hammer", hit.getType().getName());
    var category = CategoryLoader.getByString("weapons");
    assertEquals("weapons", category.getId());
    assertEquals("Weapons", category.getName());
    assertTrue(category.getRecipes().contains(recipe));
    assertEquals("none", category.getPermission());
  }

  @Test
  void configuredCategoryPermissionMustNotBeDiscarded() throws Exception {
    assertEquals(
        "professions.smith",
        new RecipeCategory("smith", yaml("name: Smith\npermission: professions.smith"))
            .getPermission());
  }
}
