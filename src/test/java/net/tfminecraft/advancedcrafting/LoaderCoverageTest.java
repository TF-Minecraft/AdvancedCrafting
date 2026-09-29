package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.junit.jupiter.api.Test;

class LoaderCoverageTest extends CoverageSupport {
  File config(String name, String text) throws Exception {
    var file = temp.resolve(name);
    Files.writeString(file, text);
    return file.toFile();
  }

  @Test
  void yamlLoadersPopulateRegistriesAndOfferMissingLookups() throws Exception {
    var schemes = new SchemeLoader();
    schemes.loadColourSchemes(config("colour.yml", "blue:\n  models: [5]\n  colours: [aabbcc]"));
    schemes.loadNamingSchemes(config("name.yml", "blue:\n  colour-scheme: blue\n  names: [Blue]"));
    schemes.loadModelSchemes(config("model.yml", "blue:\n  models: ['sword(5)']"));
    assertNotNull(SchemeLoader.getNamingSchemes().get("blue"));
    assertNotNull(SchemeLoader.getColourSchemes().get("blue"));
    assertNull(SchemeLoader.getNamingSchemeByString("missing"));
    assertNull(SchemeLoader.getColourSchemeByString("missing"));
    assertNull(SchemeLoader.getModelSchemeByString("missing"));
    var types = new TypeLoader();
    types.loadIngredientTypes(config("types.yml", "wood:\n  name: Wood"));
    types.loadHitTypes(config("hits.yml", "bend:\n  name: Bend"));
    assertNotNull(TypeLoader.getIngredientTypes().get("wood"));
    assertNull(TypeLoader.getIngredientTypeByString("unknown"));
    assertNull(TypeLoader.getHitTypeByString("unknown"));
    new HitLoader().load(config("tools.yml", "bend:\n  name: Bend\n  type: bend\n  tool: v.stick"));
    assertNotNull(HitLoader.get().get("bend"));
    assertNotNull(HitLoader.getByTool("V.STICK"));
    assertNull(HitLoader.getByString("missing"));
    assertNull(HitLoader.getByTool("missing"));
    new QualityLoader()
        .load(
            config(
                "qualities.yml",
                "poor:\n"
                    + "  name: Poor\n"
                    + "  amount: 0\n"
                    + "  value: 0\n"
                    + "fine:\n"
                    + "  name: Fine\n"
                    + "  amount: 10\n"
                    + "  value: 1"));
    assertEquals("fine", QualityLoader.getByAmount(10).getId());
    assertEquals("poor", QualityLoader.getByAmount(9).getId());
    assertNull(QualityLoader.getByAmount(-1));
    assertNotNull(QualityLoader.get().get("fine"));
    assertNotNull(QualityLoader.getByString("fine"));
    assertNull(QualityLoader.getByString("missing"));
    new CategoryLoader()
        .load(config("categories.yml", "armor:\n  name: Armor\n  permission: armor"));
    assertEquals("armor", CategoryLoader.get().get("armor").getPermission());
    assertNull(CategoryLoader.getByString("missing"));
    new StatTemplateLoader().load(config("stats.yml", "blade:\n  name: Blade\n  stats: [attack]"));
    assertEquals(1, StatTemplateLoader.getAll().size());
    assertNotNull(StatTemplateLoader.get().get("blade"));
    assertNotNull(StatTemplateLoader.getByString("BLADE"));
    assertNull(StatTemplateLoader.getByString(null));
    new SocketGroupLoader().load(config("socket.yml", "gemstones:\n  slots:\n    fine: [unknown]"));
    assertNotNull(SocketGroupLoader.get().get("gemstones"));
    assertNotNull(SocketGroupLoader.getByString("gemstones"));
    assertNull(SocketGroupLoader.getByString("missing"));
    new RecipeLoader()
        .load(
            config(
                "recipes.yml",
                "blade:\n"
                    + "  name: Blade\n"
                    + "  category: armor\n"
                    + "  stat-template: blade\n"
                    + "  socket-group: gemstones"));
    assertSame(
        StatTemplateLoader.getByString("blade"), RecipeLoader.get().get("blade").getStatTemplate());
    assertNotNull(RecipeLoader.getByString("blade"));
    assertNull(RecipeLoader.getByString("missing"));
    new IngredientLoader()
        .load(config("ingredients.yml", "iron:\n  type: metal\n  path: v.iron_ingot"));
    assertEquals(1, IngredientLoader.get().size());
    assertNotNull(IngredientLoader.getByString("IRON"));
    assertNull(IngredientLoader.getByString("missing"));
    assertSame(
        IngredientLoader.getByString("iron"),
        new ConversionLoader()
            .load(config("conversions.yml", "conversions: ['v.iron_ingot(iron)']"))
            .get("v.iron_ingot"));
    File missing = temp.resolve("missing.yml").toFile();
    schemes.loadColourSchemes(missing);
    schemes.loadNamingSchemes(missing);
    schemes.loadModelSchemes(missing);
    types.loadIngredientTypes(missing);
    types.loadHitTypes(missing);
    new HitLoader().load(missing);
    new QualityLoader().load(missing);
    new CategoryLoader().load(missing);
    new StatTemplateLoader().load(missing);
    new SocketGroupLoader().load(missing);
    new IngredientLoader().load(missing);
    new RecipeLoader().load(missing);
    assertTrue(new ConversionLoader().load(missing).isEmpty());
  }

  @Test
  void nullYamlIconsUseDefaultAndDoNotPreventLaterTemplatesLoading() throws Exception {
    String content =
        """
        explicit-null:
          name: Explicit null
          icon: null
        tilde-null:
          name: Tilde null
          icon: ~
        empty-null:
          name: Empty null
          icon:
        omitted:
          name: Omitted icon
        later-template:
          name: Later template
          icon: v.iron_sword
        """;
    // Use Bukkit's actual YAML parser and ConfigurationSection, not a mocked null getter.
    var parsed = yaml(content);
    for (String id : List.of("explicit-null", "tilde-null", "empty-null", "omitted")) {
      var section = parsed.getConfigurationSection(id);
      assertNotNull(section);
      assertNull(section.getString("icon"));
      assertEquals("v.paper", section.getString("icon", "v.paper"));
    }
    new StatTemplateLoader().load(config("nullable-icons.yml", content));
    assertEquals(
        List.of("explicit-null", "tilde-null", "empty-null", "omitted", "later-template"),
        StatTemplateLoader.getAll().stream().map(template -> template.getId()).toList());
    for (String id : List.of("explicit-null", "tilde-null", "empty-null", "omitted")) {
      assertEquals(
          org.bukkit.Material.PAPER, StatTemplateLoader.getByString(id).getIcon().getType());
    }
    assertEquals(
        org.bukkit.Material.IRON_SWORD,
        StatTemplateLoader.getByString("later-template").getIcon().getType());
    verify(items.getCreator(), times(4)).getItemFromPath("v.paper");
    verify(items.getCreator()).getItemFromPath("v.iron_sword");
  }

  @Test
  void configurationClampsPercentagesAndParsesOffsetsCombinationsAndPermissions() throws Exception {
    var loader = new ConfigLoader();
    loader.load(config("defaults.yml", ""));
    assertEquals(2, Cache.alloyForgeBaseSuccess);
    loader.load(
        config(
            "full.yml",
            """
            scrap-path: v.iron_nugget
            alloy-forge:
              base-success-percent: 200
              max-success-percent: -2
              success-bonus-per-sqrt-value: -1
              gem-stat-base-percent: -2
              gem-stat-bonus-per-value: -1
              base-bonus-percent:
                IRON: 10
                copper: 0
            hit-overshoot-warn-message: custom
            global-stat-offsets:
              ATTACK: 100
              speed: 0
            permission-namespaces:
              SMITH:
                display: Smith
            stat-aliases: ['attack->Damage']
            combinations:
              metal: [metal, missing]
              missing: [metal]
            """));
    assertEquals(100, Cache.alloyForgeBaseSuccess);
    assertEquals(100, Cache.alloyForgeMaxSuccess);
    assertEquals(0, Cache.alloyForgeBonusPerSqrtValue);
    assertEquals(0, Cache.gemstoneStatBaseChance);
    assertEquals(0, Cache.gemstoneStatBonusPerValue);
    var metal = ingredient("iron", "");
    assertEquals(10, Cache.getAlloyForgeBaseBonus(metal));
    assertEquals(0, Cache.getAlloyForgeBaseBonus(null));
    assertEquals(100, Cache.globalStatOffsets.get("attack"));
    assertFalse(Cache.globalStatOffsets.containsKey("speed"));
    assertEquals("Smith", Cache.permissionNamespaces.get("smith").getDisplay());
    assertEquals("Damage", StatToString.get("attack"));
    assertTrue(
        Cache.canCombine(metal.getIngredientData().getType(), metal.getIngredientData().getType()));
    var wood = new IngredientType("wood", yaml("name: Wood"));
    assertFalse(Cache.canCombine(metal.getIngredientData().getType(), wood));
    assertTrue(Cache.canCombine(wood, metal.getIngredientData().getType()));
    Cache.combinations.get(metal.getIngredientData().getType()).add(wood);
    assertTrue(Cache.canCombine(metal.getIngredientData().getType(), wood));
    loader.load(
        config(
            "list.yml",
            "hit-overshoot-warn-message: ' '\n"
                + "global-stat-offsets: ['', invalid, 'speed(0)', 'attack(100)', 'broken(x)']"));
    assertEquals(Map.of("attack", 100.), Cache.globalStatOffsets);
    assertTrue(Cache.hitOvershootWarnMessage.contains("%hit%"));
    invoke(loader, "parseGlobalStatOffset", new Class[] {String.class}, (Object) null);
    loader.load(temp.resolve("missing").toFile());
  }

  @Test
  void malformedOffsetCannotAbortConfigurationLoad() throws Exception {
    new ConfigLoader()
        .load(config("broken.yml", "global-stat-offsets: ['attack(10', 'speed(100)']"));
    assertEquals(Map.of("speed", 100.), Cache.globalStatOffsets);
  }

  @Test
  void freshInstallCreatesEveryDirectoryUsedByLoadConfigs() {
    doCallRealMethod().when(plugin).createFolders();
    plugin.createFolders();
    plugin.createFolders();
    for (String dir :
        List.of(
            "naming-schemes",
            "colour-schemes",
            "model-schemes",
            "recipes",
            "data/players",
            "data/stations",
            "data/alloys",
            "data/alloy-recipes")) assertTrue(Files.isDirectory(temp.resolve(dir)), dir);
  }
}
