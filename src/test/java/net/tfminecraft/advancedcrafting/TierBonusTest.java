package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.managers.InventoryManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.objects.stats.*;
import net.tfminecraft.advancedcrafting.utils.*;
import net.tfminecraft.advancedcrafting.utils.TierBonusCalculator.TierSource;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** Stat template {@code tier-bonus}: sword damage that grows with the leather and wool tier. */
class TierBonusTest extends CoverageSupport {
  static final StatModifier DAMAGE = new StatModifier("attack_damage", 0);

  static StatTemplate sword() throws Exception {
    return new StatTemplate(
        "sword",
        yaml(
            "stats: [attack_damage]\n"
                + "tier-bonus:\n"
                + "  leather: attack_damage(0.125)\n"
                + "  WOOL: ATTACK_DAMAGE(0.125)\n"
                + "tier-bonus-neutral: 2"));
  }

  Ingredient animal(String id, String type, int tier) throws Exception {
    var result = new Ingredient(id, yaml("path: v.leather\ntype: " + type + "\ntier: " + tier));
    IngredientLoader.oList.add(result);
    return result;
  }

  void animals() throws Exception {
    TypeLoader.map.put("leather", new IngredientType("leather", yaml("name: Leather")));
    TypeLoader.map.put("wool", new IngredientType("wool", yaml("name: Wool")));
    for (int tier = 1; tier <= 4; tier++) {
      animal("leather" + tier, "leather", tier);
      animal("wool" + tier, "wool", tier);
    }
    ingredient("iron", "tier: 1\nstats: ['attack_damage(3.5)']");
  }

  double damage(CraftingRecipe craft, Map<String, Integer> materials) {
    return CraftStatCalculator.compute(craft, materials).getAmount(DAMAGE);
  }

  @Test
  void templateParsesBonusesAroundTheNeutralTier() throws Exception {
    var sword = sword();
    assertTrue(sword.hasTierBonuses());
    assertTrue(sword.hasTierBonus("LEATHER"));
    assertTrue(sword.hasTierBonus("wool"));
    assertFalse(sword.hasTierBonus("metal"));
    assertFalse(sword.hasTierBonus(null));
    assertEquals(-.125, sword.getTierBonus("leather", 1).getAmount());
    assertEquals("attack_damage", sword.getTierBonus("wool", 1).getType());
    assertEquals(.25, sword.getTierBonus("Wool", 4).getAmount());
    assertNull(sword.getTierBonus("leather", 2), "the neutral tier adds nothing");
    assertNull(sword.getTierBonus("metal", 4));
    assertTrue(
        sword
            .buildRevisionContent()
            .endsWith(
                ";tierBonus=leather:attack_damage(0.125),wool:attack_damage(0.125)"
                    + ";tierBonusNeutral=2.0"));

    var plain = new StatTemplate("plain", yaml("stats: [attack_damage]"));
    assertFalse(plain.hasTierBonuses());
    assertFalse(plain.buildRevisionContent().contains("tierBonus"), "other templates keep revision");

    var noNeutral =
        new StatTemplate("spear", yaml("stats: [reach]\ntier-bonus:\n  leather: reach(0.05)"));
    assertEquals(.05, noNeutral.getTierBonus("leather", 1).getAmount(), "neutral defaults to 0");

    var broken =
        new StatTemplate(
            "broken",
            yaml(
                "stats: [armor]\n"
                    + "tier-bonus:\n"
                    + "  leather: broken\n"
                    + "  wool:\n"
                    + "    nested: 1\n"
                    + "  paper: armor(x)\n"
                    + "  feather: speed(2)"));
    assertFalse(broken.hasTierBonus("leather"));
    assertFalse(broken.hasTierBonus("wool"));
    assertFalse(broken.hasTierBonus("paper"));
    assertTrue(broken.hasTierBonus("feather"), "kept, with a warning that speed is not in stats");
  }

  @Test
  void swordDamageGrowsWithLeatherAndWoolTier() throws Exception {
    animals();
    StatTemplateLoader.get().put("sword", sword());
    var craft = recipe("stat-template: sword");
    double[] expected = {3.25, 3.5, 3.75, 4.0};
    for (int tier = 1; tier <= 4; tier++) {
      assertEquals(
          expected[tier - 1],
          damage(
              craft,
              Map.of(
                  "ingredient.iron", 4, "ingredient.leather" + tier, 2, "ingredient.wool" + tier, 2)),
          "tier " + tier);
    }
    // Mixed leather averages to tier 1.5: -0.0625 + 0 = 3.4375, rounded half-up
    assertEquals(
        3.44,
        damage(
            craft,
            Map.of(
                "ingredient.iron", 4,
                "ingredient.leather1", 1,
                "ingredient.leather2", 1,
                "ingredient.wool2", 2)));
    // Craft records (refresh path) give the same result
    assertEquals(
        4.0,
        CraftStatCalculator.compute(
                craft,
                List.of(
                    new CraftInput("ingredient", "iron", 4, 0),
                    new CraftInput("ingredient", "leather4", 2, 0),
                    new CraftInput("ingredient", "wool4", 2, 0)))
            .getAmount(DAMAGE));
  }

  @Test
  void materialsWithoutATypeOrTierAreIgnored() throws Exception {
    animals();
    animal("paper", "leather", 0);
    animal("ghost", "missing", 3);
    StatTemplateLoader.get().put("sword", sword());
    var craft = recipe("stat-template: sword");
    var materials = new HashMap<String, Integer>();
    materials.put("ingredient.iron", 4);
    materials.put("ingredient.leather4", 0);
    materials.put("nodot", 2);
    materials.put("thing.leather4", 2);
    materials.put("ingredient.unknown", 2);
    materials.put("alloy.unknown", 2);
    materials.put("ingredient.paper", 2);
    materials.put("ingredient.ghost", 2);
    assertEquals(3.5, damage(craft, materials));

    var stats = new StatData();
    TierBonusCalculator.apply(stats, null, Map.of("ingredient.leather4", 2));
    TierBonusCalculator.apply(
        stats, new StatTemplate("plain", yaml("stats: [x]")), Map.of("ingredient.leather4", 2));
    TierBonusCalculator.apply(stats, sword(), null);
    assertFalse(stats.hasModifiers());
    assertNull(TierSource.of((IngredientData) null));
    assertNull(TierSource.of((AlloyData) null));
    assertTrue(BucketStatAverager.toMaterials(null).isEmpty());
  }

  @Test
  void alloysCountWithTheirTypeAndTier() throws Exception {
    animals();
    var hide = animal("hide", "leather", 3);
    AlloyManager.addAlloy(
        new Alloy("tanned", "Tanned", new AlloyData(hide, new StatData(), new HashMap<>(), null)));
    StatTemplateLoader.get().put("sword", sword());
    var craft = recipe("stat-template: sword");
    assertEquals(
        3.75, damage(craft, Map.of("ingredient.iron", 4, "alloy.tanned", 2, "ingredient.wool3", 2)));
  }

  @Test
  void previewAddsTheHoveredMaterialsBonus() throws Exception {
    var sword = sword();
    var source = new StatData(List.of("critical_strike_chance(5)"));
    assertSame(source, TierBonusCalculator.withBonus(source, sword, null));
    assertSame(source, TierBonusCalculator.withBonus(source, sword, new TierSource("metal", 4)));
    assertSame(source, TierBonusCalculator.withBonus(source, sword, new TierSource("leather", 2)));
    var shown = TierBonusCalculator.withBonus(source, sword, new TierSource("leather", 4));
    assertEquals(.25, shown.getAmount(DAMAGE));
    assertFalse(source.hasModifier(DAMAGE), "the material's own stats are not changed");

    when(plugin.getServer()).thenReturn(server);
    new StatTemplateLoader()
        .load(
            java.nio.file.Files.writeString(
                    temp.resolve("tier-stats.yml"),
                    "sword:\n"
                        + "  icon: v.paper\n"
                        + "  stats: [attack_damage]\n"
                        + "  tier-bonus:\n"
                        + "    leather: attack_damage(0.125)\n"
                        + "  tier-bonus-neutral: 2")
                .toFile());
    var p = server.addPlayer();
    var menu = new InventoryManager();
    menu.templatePreviewView(p, source, new TierSource("leather", 4));
    assertEquals(Material.PAPER, p.getOpenInventory().getTopInventory().getItem(0).getType());
    var lore = p.getOpenInventory().getTopInventory().getItem(0).getItemMeta().getLore();
    assertTrue(lore.getFirst().contains("+0.25"), lore.toString());
    menu.templatePreviewView(p, source, new TierSource("leather", 2));
    assertTrue(p.nextMessage().contains("no stats"));
  }
}
