package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.objects.stats.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class MathCoverageTest extends CoverageSupport {
  @Test
  void templateScalingFiltersAddsBaseStatsAndRendersPreview() throws Exception {
    var template =
        new StatTemplate(
            "sword",
            yaml(
                "name: Sword\n"
                    + "stats: [attack, speed, unused, armor]\n"
                    + "factors:\n"
                    + "  attack: 1.5\n"
                    + "  speed: 0.5\n"
                    + "base-stats: ['attack(1)', 'armor(4)', 'health(2)']"));
    assertEquals("sword", template.getId());
    assertEquals("Sword", template.getName());
    assertNotNull(template.getIcon());
    assertEquals(List.of("attack", "speed", "unused", "armor"), template.getStats());
    assertTrue(template.allowsIngredientStat("ATTACK"));
    assertFalse(template.allowsIngredientStat("health"));
    assertEquals(1, template.getFactor("health"));
    assertEquals(1.5, template.getFactor("attack"));
    assertEquals(0, template.getBaseAmount("missing"));
    assertEquals(1, template.getBaseAmount("ATTACK"));
    template.setRevision(5);
    assertEquals(5, template.getRevision());
    assertTrue(template.buildRevisionContent().contains("attack(1.5)"));
    var source = new StatData(List.of("attack(3)", "speed(-2)", "health(7)"));
    assertTrue(StatTemplateMath.hasOverlap(source, template));
    assertFalse(StatTemplateMath.hasOverlap(new StatData(), template));
    var result = StatTemplateMath.filterAndApply(source, template);
    assertEquals(6, result.getAmount(new StatModifier("attack", 0)));
    assertEquals(2, result.getAmount(new StatModifier("health", 0)));
    assertEquals(4, StatTemplateMath.getPreviewLines(source, template).size());
    assertEquals("", StatTemplateMath.formatPercentSuffix(1));
    assertTrue(StatTemplateMath.formatPercentSuffix(1.5).contains("+50%"));
    assertTrue(StatTemplateMath.formatPercentSuffix(.5).contains("-50%"));
    assertTrue(StatTemplateMath.formatPercentSuffix(1.001).contains("0%"));
    assertEquals(.005, StatTemplateMath.applyFactor(.01, .5));
    assertEquals(0, StatTemplateMath.applyFactor(0, 3));
    assertEquals(2, StatTemplateMath.applyGlobalOffset(2, "missing"));
    Cache.globalStatOffsets.put("attack", 0.);
    assertEquals(2, StatTemplateMath.applyGlobalOffset(2, "attack"));
    assertEquals(3, StatTemplateMath.applyScaledValueForPreview(2, "attack", template));
    Cache.globalStatOffsets.put("attack", 100.);
    assertEquals(.02, StatTemplateMath.applyGlobalOffset(2, "ATTACK"));
    assertEquals(3, StatTemplateMath.applyScaledValueForPreview(2, "attack", template));
    assertEquals(0, StatTemplateMath.getFactor(template, "attack") - 1.5);
    assertNotNull(new StatTemplate("invalid", yaml("icon: bad")).getName());
    assertNotNull(new StatTemplate("default", yaml("")).getIcon());
  }

  @Test
  void bucketAveragesCountMissingStatsAsZeroAndSumSeparateBuckets() throws Exception {
    ingredient("iron", "stats: ['armor(6)', 'speed(2)']");
    ingredient("copper", "stats: ['armor(2)']");
    ingredient("gem", "stats: ['armor(3)']\nstat-merge-key: gemstones");
    ingredient("missingtype", "type: invalid");
    var result =
        BucketStatAverager.compute(
            Map.of("ingredient.iron", 1, "ingredient.copper", 3, "ingredient.gem", 1));
    assertEquals(6, result.getAmount(new StatModifier("armor", 0)));
    assertEquals(.5, result.getAmount(new StatModifier("speed", 0)));
    for (Map<String, Integer> materials :
        Arrays.asList(
            null,
            Map.<String, Integer>of(),
            Map.of("bad", 1),
            Map.of("thing.iron", 1),
            Map.of("ingredient.unknown", 1),
            Map.of("alloy.unknown", 1),
            Map.of("ingredient.missingtype", 1),
            Map.of("ingredient.iron", 0))) {
      assertFalse(BucketStatAverager.compute(materials).hasModifiers());
    }
    assertFalse(BucketStatAverager.computeFromInputs(null).hasModifiers());
    assertFalse(BucketStatAverager.computeFromInputs(List.of()).hasModifiers());
    result =
        BucketStatAverager.computeFromInputs(
            Arrays.asList(
                null,
                new CraftInput("ingredient", "iron", 0, 0),
                new CraftInput(null, "iron", 1, 0),
                new CraftInput("INGREDIENT", "IRON", 1, 0),
                new CraftInput("ingredient", "iron", 2, 0)));
    assertEquals(6, result.getAmount(new StatModifier("armor", 0)));
  }

  @Test
  void professionPermissionsRequireExactTierAndSupportUnrestrictedMaterials() throws Exception {
    Player player = mock(Player.class);
    Cache.permissionPrefix = "professions.";
    assertEquals("professions.smith", ProfessionPermissions.flatPermission("SMITH"));
    assertEquals("professions.smith_2", ProfessionPermissions.fullPermission("SMITH", 2));
    for (String ns : Arrays.asList(null, " ")) {
      assertFalse(ProfessionPermissions.hasIngredientPerm(player, ns));
      assertFalse(ProfessionPermissions.hasAnyNamespacePerm(player, ns));
      assertFalse(ProfessionPermissions.hasExactTierPerm(player, ns, 2));
    }
    assertFalse(ProfessionPermissions.hasIngredientPerm(null, "smith"));
    assertFalse(ProfessionPermissions.hasAnyNamespacePerm(null, "smith"));
    assertFalse(ProfessionPermissions.hasExactTierPerm(null, "smith", 2));
    assertFalse(ProfessionPermissions.hasIngredientPerm(player, "smith"));
    assertFalse(ProfessionPermissions.hasAnyNamespacePerm(player, "smith"));
    assertFalse(ProfessionPermissions.hasExactTierPerm(player, "smith", 0));
    when(player.hasPermission("professions.smith_4")).thenReturn(true);
    when(player.hasPermission("professions.smith")).thenReturn(true);
    assertTrue(ProfessionPermissions.hasIngredientPerm(player, "smith"));
    assertTrue(ProfessionPermissions.hasAnyNamespacePerm(player, "smith"));
    assertTrue(ProfessionPermissions.hasExactTierPerm(player, "smith", 4));
    assertFalse(ProfessionPermissions.hasExactTierPerm(player, "smith", 2));
    assertEquals("", ProfessionPermissions.getDisplayName(null));
    assertEquals("unknown", ProfessionPermissions.getDisplayName("unknown"));
    Cache.permissionNamespaces.put("smith", new PermissionNamespace("smith", "Smithing"));
    assertEquals("Smithing", ProfessionPermissions.getDisplayName("SMITH"));
    assertTrue(ProfessionPermissions.missingNamespaceMessage("smith").contains("Smithing"));
    assertTrue(ProfessionPermissions.missingExactTierMessage("smith", 3).contains("tier 3"));
    assertTrue(
        ProfessionPermissions.missingIngredientPermissionMessage("smith").contains("Smithing"));
    Ingredient base = ingredient("iron", "base: true\ntier: 3\npermission: smith");
    Ingredient catalyst = ingredient("copper", "tier: 2");
    Ingredient unranked = ingredient("wood", "base: true");
    assertEquals(3, ProfessionPermissions.resolveTier(base));
    assertEquals(3, ProfessionPermissions.resolveTier("iron"));
    assertEquals(0, ProfessionPermissions.resolveTier(catalyst));
    assertEquals(0, ProfessionPermissions.resolveTier(unranked));
    assertEquals(2, ProfessionPermissions.resolveIngredientTier(catalyst));
    assertEquals(0, ProfessionPermissions.resolveIngredientTier(unranked));
    assertEquals(0, ProfessionPermissions.resolveTier((Ingredient) null));
    assertEquals(0, ProfessionPermissions.resolveIngredientTier((Ingredient) null));
    assertEquals(0, ProfessionPermissions.resolveTier((IngredientData) null));
    assertEquals(0, ProfessionPermissions.resolveIngredientTier((IngredientData) null));
    assertTrue(ProfessionPermissions.canUseIngredient(player, base));
    assertTrue(ProfessionPermissions.canUseIngredient(player, catalyst));
    assertFalse(ProfessionPermissions.canUseIngredient(null, base));
    assertFalse(ProfessionPermissions.canUseIngredient(player, null));
    assertFalse(ProfessionPermissions.canUseIngredient(player, mock(Ingredient.class)));
    assertEquals(0, ProfessionPermissions.resolveAlloyTier("unknown"));
  }

  @Test
  void mainTypeWinsTierSelectionAndUnknownInputsFallBack() throws Exception {
    var recipe = recipe("");
    ingredient("iron", "base: true\ntier: 3");
    ingredient("copper", "base: true\ntier: 2");
    var materials = new LinkedHashMap<String, Integer>();
    materials.put("ingredient.iron", 1);
    materials.put("ingredient.copper", 2);
    materials.put("unknown.thing", 10);
    assertEquals("ingredient.copper", MajorityTierResolver.resolveMajorityKey(recipe, materials));
    assertEquals(2, MajorityTierResolver.resolveTier(recipe, materials));
    assertEquals(
        3,
        MajorityTierResolver.resolveTier(
            recipe, List.of(new CraftInput("INGREDIENT", "IRON", 1, 0))));
    assertEquals("", MajorityTierResolver.resolveMajorityKey(null, materials));
    assertEquals("", MajorityTierResolver.resolveMajorityKey(recipe, null));
    assertEquals("", MajorityTierResolver.resolveMajorityKey(recipe, Map.of()));
    assertEquals(0, MajorityTierResolver.resolveTier(null, List.of()));
    assertEquals(0, MajorityTierResolver.resolveTier(recipe, (List<CraftInput>) null));
    assertEquals(0, MajorityTierResolver.resolveTier(recipe, List.of()));
    for (String key :
        Arrays.asList(null, " ", "bad", "other.unknown", "ingredient.unknown", "alloy.unknown"))
      assertEquals(0, MajorityTierResolver.resolveTierFromKey(key));
    for (String key :
        List.of("bad", "other.unknown", "ingredient.unknown", "alloy.unknown", "ingredient.iron"))
      assertEquals(key, MajorityTierResolver.resolveMajorityKey(recipe, Map.of(key, 0)));
  }
}
