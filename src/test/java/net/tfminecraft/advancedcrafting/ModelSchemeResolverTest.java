package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.objects.schemes.ModelScheme;
import net.tfminecraft.advancedcrafting.utils.ModelSchemeResolver;
import org.junit.jupiter.api.Test;

class ModelSchemeResolverTest extends CoverageSupport {
  static CraftInput in(String kind, String id, int amount) {
    return new CraftInput(kind, id, amount, 0);
  }

  static String scheme(CraftingRecipe recipe, CraftInput... inputs) {
    ModelScheme scheme = ModelSchemeResolver.resolve(recipe, List.of(inputs));
    return scheme == null ? null : scheme.getId();
  }

  Alloy alloy(String id, Ingredient base) {
    var alloy = new Alloy(id, id, new AlloyData(base, new StatData(), new HashMap<>(), null));
    AlloyManager.addAlloy(alloy);
    return alloy;
  }

  @Test
  void recordedCraftsResolveTheSchemeTheirLookCameFromAndAlloysUseTheirBase() throws Exception {
    TypeLoader.map.put("paper", new IngredientType("paper", yaml("name: Paper")));
    SchemeLoader.models.put("iron", new ModelScheme("iron", yaml("models: ['smith(v.iron_sword.1)']")));
    SchemeLoader.models.put("own", new ModelScheme("own", yaml("models: ['sword(v.iron_sword.2)']")));
    SchemeLoader.models.put("cloth", new ModelScheme("cloth", yaml("models: ['smith(v.golden_sword.3)']")));
    ingredient("iron", "model-scheme: iron");
    ingredient("own", "model-scheme: own");
    var cloth = ingredient("cloth", "type: paper\nmodel-scheme: cloth");
    alloy("darksteel", IngredientLoader.getByString("iron"));
    alloy("clothsteel", cloth);
    var noModel = recipe("recipe: ['metal.1', 'paper.1']\nmodel-type: none");
    var byPaper = recipe("recipe: ['metal.1', 'paper.1']\nmodel-type: paper");

    assertNull(ModelSchemeResolver.resolve(null, List.of(in("ingredient", "iron", 1))));
    assertNull(ModelSchemeResolver.resolve(noModel, null));
    assertNull(ModelSchemeResolver.resolve(noModel, List.of()));

    // The main material decides; an alloy wears its base metal's scheme.
    assertEquals("iron", scheme(noModel, in("ingredient", "iron", 3), in("ingredient", "cloth", 1)));
    assertEquals("iron", scheme(noModel, in("alloy", "darksteel", 3)));
    // A model-type material takes over when the main scheme has no model for the recipe ...
    assertEquals("cloth", scheme(byPaper, in("alloy", "darksteel", 3), in("ingredient", "cloth", 1)));
    assertEquals("cloth", scheme(byPaper, in("ingredient", "iron", 3), in("alloy", "clothsteel", 1)));
    // ... but not when it has one of its own.
    assertEquals("own", scheme(byPaper, in("ingredient", "own", 3), in("ingredient", "cloth", 1)));
    // Unknown, malformed and foreign inputs leave the main scheme alone.
    assertEquals(
        "iron",
        scheme(
            byPaper,
            in("ingredient", "iron", 3),
            in("alloy", "darksteel", 1),
            in("ingredient", "gone", 1),
            in("alloy", "gone", 1),
            in("ingredient", "", 1),
            in("misc", "x", 1)));
    assertNull(scheme(noModel, in("ingredient", "gone", 1)));
    // A model-type material whose scheme has no model for the recipe (an unknown scheme falls back to an
    // empty default) or no scheme at all keeps the main scheme.
    SchemeLoader.models.put("default", new ModelScheme("default", yaml("models: []")));
    var blank = ingredient("blank", "type: paper\nmodel-scheme: missing");
    alloy("blankalloy", blank);
    assertEquals("iron", scheme(byPaper, in("ingredient", "iron", 3), in("ingredient", "blank", 1)));
    assertEquals("iron", scheme(byPaper, in("ingredient", "iron", 3), in("alloy", "blankalloy", 1)));
    SchemeLoader.models.remove("default");
    ingredient("schemeless", "type: paper\nmodel-scheme: missing");
    assertEquals("iron", scheme(byPaper, in("ingredient", "iron", 3), in("ingredient", "schemeless", 1)));
    // Malformed inputs are skipped.
    assertEquals(
        "iron",
        ModelSchemeResolver.resolve(
                noModel,
                Arrays.asList(null, in(null, "iron", 1), in("ingredient", null, 1), in("ingredient", "iron", 3)))
            .getId());
    assertNull(scheme(noModel, in("alloy", "gone", 1)));
  }
}
