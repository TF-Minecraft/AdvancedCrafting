package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.nio.file.Files;
import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.objects.crafting.CraftingRecipe;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.schemes.ModelScheme;
import net.tfminecraft.advancedcrafting.util.LegacyModelData;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

class ArmourLookMigratorTest extends CoverageSupport {
  /** Stands in for TLibs' {@code ia} NBT tag ("namespace.id"). */
  static final NamespacedKey IA = new NamespacedKey("test", "ia");
  /** Stands in for the ItemsAdder {@code itemsadder} compound. */
  static final NamespacedKey COMPOUND = new NamespacedKey("test", "itemsadder");
  /** Stands in for the custom model data component, which MockBukkit does not implement. */
  static final NamespacedKey MODEL = new NamespacedKey("test", "model");

  MockedStatic<NBTItem> nbts;
  MockedStatic<LegacyModelData> models;
  CraftingRecipe light;
  CraftingRecipe infantry;

  @BeforeEach
  void world() throws Exception {
    models = mockStatic(LegacyModelData.class);
    models
        .when(() -> LegacyModelData.set(any(ItemMeta.class), any()))
        .thenAnswer(
            call -> {
              ItemMeta meta = call.getArgument(0);
              Integer value = call.getArgument(1);
              if (value == null) meta.getPersistentDataContainer().remove(MODEL);
              else meta.getPersistentDataContainer().set(MODEL, PersistentDataType.INTEGER, value);
              return null;
            });
    models
        .when(() -> LegacyModelData.has(any(ItemMeta.class)))
        .thenAnswer(call -> ((ItemMeta) call.getArgument(0)).getPersistentDataContainer().has(MODEL));
    models
        .when(() -> LegacyModelData.get(any(ItemMeta.class)))
        .thenAnswer(
            call ->
                ((ItemMeta) call.getArgument(0))
                    .getPersistentDataContainer()
                    .get(MODEL, PersistentDataType.INTEGER));
    nbts = mockStatic(NBTItem.class);
    nbts.when(() -> NBTItem.get(any(ItemStack.class)))
        .thenAnswer(
            call -> {
              ItemStack item = call.getArgument(0);
              var pdc = item.getItemMeta().getPersistentDataContainer();
              String ia = pdc.get(IA, PersistentDataType.STRING);
              var nbt = mock(NBTItem.class);
              when(nbt.hasTag("ia")).thenReturn(ia != null);
              when(nbt.getString("ia")).thenReturn(ia);
              when(nbt.hasTag("itemsadder")).thenReturn(ia != null || pdc.has(COMPOUND));
              return nbt;
            });
    // The merge swaps the look and stamps the ItemsAdder id, like ArmorMerger.
    when(items.getArmorMerger().merge(any(ItemStack.class), any(), anyString()))
        .thenAnswer(
            call -> {
              ItemStack item = call.getArgument(0);
              String id = ((String) call.getArgument(2)).substring(3);
              item.setType(Material.LEATHER_HELMET);
              var meta = item.getItemMeta();
              meta.getPersistentDataContainer().set(IA, PersistentDataType.STRING, id.replace(':', '.'));
              item.setItemMeta(meta);
              return item;
            });
    SchemeLoader.models.put(
        "iron",
        new ModelScheme(
            "iron",
            yaml(
                "models: ['helmet(v.chainmail_helmet.0)', 'light_helmet(ia.tfmc_armor:iron_light_helmet)',"
                    + " 'infantry_helmet(ia.tfmc_armor:base_metal_iron_infantry_helmet)']")));
    SchemeLoader.models.put(
        "bronze",
        new ModelScheme(
            "bronze",
            yaml(
                "models: ['helmet(ia.tfmc_armor:bronze_helmet)',"
                    + " 'light_helmet(ia.tfmc_armor:bronze_light_helmet)']")));
    SchemeLoader.models.put("plain", new ModelScheme("plain", yaml("models: []")));
    ingredient("iron", "model-scheme: iron");
    ingredient("bronze", "model-scheme: bronze");
    ingredient("plain", "model-scheme: plain");
    light = armour("light_helmet");
    infantry = armour("infantry_helmet");
    Cache.armourLookMigration = true;
    Cache.legacyModels.clear();
    Cache.legacyModels.put("infantry_helmet", List.of("v.carved_pumpkin.1"));
  }

  @AfterEach
  void closeNbt() {
    nbts.close();
    models.close();
  }

  CraftingRecipe armour(String id) throws Exception {
    var recipe =
        new CraftingRecipe(
            id,
            yaml(
                "name: '%material% Helmet'\ncategory: weapons\ntemplate: HELMETS.TEST\ntype: helmet\n"
                    + "recipe: ['metal.4']"));
    RecipeLoader.map.put(id, recipe);
    return recipe;
  }

  /** A piece crafted from {@code metal} that wears a vanilla look. */
  ItemStack crafted(String recipe, String metal, Material material, Integer model) {
    var item = new ItemStack(material);
    var meta = item.getItemMeta();
    LegacyModelData.set(meta, model);
    item.setItemMeta(meta);
    new CraftProvenance(recipe, "fine", List.of(new CraftInput("ingredient", metal, 4, 0)), 0)
        .applyTo(item);
    // Older crafts carry no scheme tag.
    meta = item.getItemMeta();
    meta.getPersistentDataContainer().remove(PDCKeys.craftModelScheme());
    item.setItemMeta(meta);
    return item;
  }

  static ItemStack withIa(ItemStack item, String ia) {
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(IA, PersistentDataType.STRING, ia);
    item.setItemMeta(meta);
    return item;
  }

  static String tag(ItemStack item, NamespacedKey key) {
    return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
  }

  static String ia(ItemStack item) {
    return tag(item, IA);
  }

  @Test
  void oldDefaultLooksTakeTheRecipeModelAndKeepARecordOfTheOldOne() {
    var old = crafted("light_helmet", "iron", Material.CHAINMAIL_HELMET, null);
    var migrated = ArmourLookMigrator.migrate(old);

    assertNotSame(old, migrated);
    assertEquals(Material.CHAINMAIL_HELMET, old.getType(), "the original stack is left alone");
    assertNull(tag(old, PDCKeys.craftModelScheme()));
    assertEquals("tfmc_armor.iron_light_helmet", ia(migrated));
    assertEquals("v.chainmail_helmet.0", tag(migrated, PDCKeys.previousModel()));
    assertEquals("iron", tag(migrated, PDCKeys.craftModelScheme()));
    assertEquals("light_helmet", CraftProvenance.readFrom(migrated).getRecipeId());

    // Migrated pieces are left alone from then on.
    assertSame(migrated, ArmourLookMigrator.migrate(migrated));

    // An ItemsAdder type model counts as the old look too.
    var bronze = withIa(crafted("light_helmet", "bronze", Material.LEATHER_HELMET, 5), "tfmc_armor.bronze_helmet");
    var newBronze = ArmourLookMigrator.migrate(bronze);
    assertEquals("tfmc_armor.bronze_light_helmet", ia(newBronze));
    assertEquals("ia.tfmc_armor:bronze_helmet", tag(newBronze, PDCKeys.previousModel()));
    assertEquals("bronze", tag(newBronze, PDCKeys.craftModelScheme()));
  }

  @Test
  void skinsAndOtherLooksStayButStillGetTheMetalTag() {
    var skinned =
        withIa(
            crafted("light_helmet", "iron", Material.LEATHER_HELMET, 10194),
            "tfmc_submissions.geofflive_thalendorian_armor_iron_helmet");
    var tagged = ArmourLookMigrator.migrate(skinned);
    assertNotSame(skinned, tagged);
    assertEquals("tfmc_submissions.geofflive_thalendorian_armor_iron_helmet", ia(tagged));
    assertEquals(Material.LEATHER_HELMET, tagged.getType());
    assertNull(tag(tagged, PDCKeys.previousModel()));
    assertEquals("iron", tag(tagged, PDCKeys.craftModelScheme()));
    assertSame(tagged, ArmourLookMigrator.migrate(tagged));
    verify(items.getArmorMerger(), never()).merge(any(ItemStack.class), any(), anyString());

    // A vanilla skin of another model, a vanilla material carrying an ItemsAdder compound, and a
    // stale ia tag on the old material all stay as they are.
    var otherModel = ArmourLookMigrator.migrate(crafted("light_helmet", "iron", Material.CHAINMAIL_HELMET, 3));
    assertEquals(Material.CHAINMAIL_HELMET, otherModel.getType());
    var compoundItem = crafted("light_helmet", "iron", Material.CHAINMAIL_HELMET, null);
    var meta = compoundItem.getItemMeta();
    meta.getPersistentDataContainer().set(COMPOUND, PersistentDataType.STRING, "x");
    compoundItem.setItemMeta(meta);
    assertEquals(Material.CHAINMAIL_HELMET, ArmourLookMigrator.migrate(compoundItem).getType());
    var stale = withIa(crafted("light_helmet", "iron", Material.CHAINMAIL_HELMET, null), "old.skin");
    assertEquals("old.skin", ia(ArmourLookMigrator.migrate(stale)));
    var otherMaterial = ArmourLookMigrator.migrate(crafted("light_helmet", "iron", Material.IRON_HELMET, null));
    assertEquals(Material.IRON_HELMET, otherMaterial.getType());
  }

  @Test
  void configuredLegacyModelsReplaceTheMetalTypeModel() {
    var paperLook = crafted("infantry_helmet", "iron", Material.CARVED_PUMPKIN, 1);
    var migrated = ArmourLookMigrator.migrate(paperLook);
    assertEquals("tfmc_armor.base_metal_iron_infantry_helmet", ia(migrated));
    assertEquals("v.carved_pumpkin.1", tag(migrated, PDCKeys.previousModel()));

    // An infantry piece in plain chainmail was skinned that way; the metal's type model does not apply.
    var chainmail = ArmourLookMigrator.migrate(crafted("infantry_helmet", "iron", Material.CHAINMAIL_HELMET, null));
    assertEquals(Material.CHAINMAIL_HELMET, chainmail.getType());
    assertEquals("iron", tag(chainmail, PDCKeys.craftModelScheme()));
  }

  @Test
  void piecesWithoutARecipeModelOrARecordAreOnlyTaggedOrSkipped() throws Exception {
    // A scheme with no model for the recipe (weapons, other materials) only gets the tag.
    var plain = crafted("light_helmet", "plain", Material.CHAINMAIL_HELMET, null);
    var tagged = ArmourLookMigrator.migrate(plain);
    assertEquals(Material.CHAINMAIL_HELMET, tagged.getType());
    assertEquals("plain", tag(tagged, PDCKeys.craftModelScheme()));
    // The plain scheme has no type model either.
    armour("plain_cap");
    assertFalse(
        invokeLegacy(crafted("plain_cap", "plain", Material.CHAINMAIL_HELMET, null), "plain_cap", "plain"));

    // Unknown recipes and inputs resolve no scheme; items without a record are not crafted pieces.
    var unknownRecipe = crafted("gone", "iron", Material.CHAINMAIL_HELMET, null);
    assertSame(unknownRecipe, ArmourLookMigrator.migrate(unknownRecipe));
    var unknownMetal = crafted("light_helmet", "gone", Material.CHAINMAIL_HELMET, null);
    assertSame(unknownMetal, ArmourLookMigrator.migrate(unknownMetal));
    var premade = new ItemStack(Material.CHAINMAIL_HELMET);
    assertSame(premade, ArmourLookMigrator.migrate(premade));
    assertNull(ArmourLookMigrator.migrate(null));
    var air = new ItemStack(Material.AIR);
    assertSame(air, ArmourLookMigrator.migrate(air));
    var noMeta = mock(ItemStack.class);
    when(noMeta.getType()).thenReturn(Material.CHAINMAIL_HELMET);
    assertSame(noMeta, ArmourLookMigrator.migrate(noMeta));

    // Switched off, nothing changes.
    Cache.armourLookMigration = false;
    var old = crafted("light_helmet", "iron", Material.CHAINMAIL_HELMET, null);
    assertSame(old, ArmourLookMigrator.migrate(old));
  }

  boolean invokeLegacy(ItemStack item, String recipe, String scheme) throws Exception {
    var m =
        ArmourLookMigrator.class.getDeclaredMethod(
            "wearsLegacyLook", ItemStack.class, CraftingRecipe.class, ModelScheme.class);
    m.setAccessible(true);
    return (boolean) m.invoke(null, item, RecipeLoader.map.get(recipe), SchemeLoader.models.get(scheme));
  }

  @Test
  void looksAreMatchedAndDescribedByTheirSchemePath() throws Exception {
    var wears =
        ArmourLookMigrator.class.getDeclaredMethod("wears", ItemStack.class, String.class);
    wears.setAccessible(true);
    var describe = ArmourLookMigrator.class.getDeclaredMethod("describe", ItemStack.class);
    describe.setAccessible(true);
    var plain = new ItemStack(Material.CHAINMAIL_HELMET);

    assertTrue((boolean) wears.invoke(null, plain, "v.chainmail_helmet.0"));
    assertFalse((boolean) wears.invoke(null, plain, "v.chainmail_helmet"));
    assertFalse((boolean) wears.invoke(null, plain, "m.helmets.light_custom_helmet"));
    assertFalse((boolean) wears.invoke(null, plain, "ia.tfmc_armor:iron_light_helmet"));
    assertEquals("v.chainmail_helmet.0", describe.invoke(null, plain));

    var modelled = new ItemStack(Material.CARVED_PUMPKIN);
    var meta = modelled.getItemMeta();
    LegacyModelData.set(meta, 1);
    modelled.setItemMeta(meta);
    assertEquals("v.carved_pumpkin.1", describe.invoke(null, modelled));

    var skinned = withIa(new ItemStack(Material.LEATHER_HELMET), "tfmc_armor.iron_light_helmet");
    assertTrue((boolean) wears.invoke(null, skinned, "ia.TFMC_ARMOR:iron_light_helmet"));
    assertEquals("ia.tfmc_armor:iron_light_helmet", describe.invoke(null, skinned));

    // Malformed ia tags read as no ItemsAdder id.
    for (String broken : List.of("nodot", ".id", "ns.")) {
      var item = withIa(new ItemStack(Material.LEATHER_HELMET), broken);
      assertEquals("v.leather_helmet.0", describe.invoke(null, item));
    }
  }

  @Test
  void modelPathsApplyVanillaAndItemsAdderLooks() throws Exception {
    var scheme = SchemeLoader.models.get("iron");
    assertEquals("ia.tfmc_armor:iron_light_helmet", ModelApplier.modelFor(scheme, light));
    var heavy = armour("heavy_helmet");
    assertEquals("v.chainmail_helmet.0", ModelApplier.modelFor(scheme, heavy));

    var vanilla = ModelApplier.apply(new ItemStack(Material.LEATHER_HELMET), "v.iron_helmet.4");
    assertEquals(Material.IRON_HELMET, vanilla.getType());
    assertEquals(4, LegacyModelData.get(vanilla.getItemMeta()));
    var ia = ModelApplier.apply(new ItemStack(Material.CHAINMAIL_HELMET), "ia.tfmc_armor:iron_light_helmet");
    assertEquals("tfmc_armor.iron_light_helmet", ia(ia));
    var unknown = new ItemStack(Material.CHAINMAIL_HELMET);
    assertSame(unknown, ModelApplier.apply(unknown, "x.something"));
  }

  @Test
  void theConfigListsLegacyModelsPerRecipe() throws Exception {
    var file = temp.resolve("migration.yml");
    Files.writeString(
        file,
        "armour-look-migration:\n  enabled: true\n  legacy-models:\n"
            + "    - Infantry_Helmet(v.carved_pumpkin.1)\n    - infantry_helmet( v.golden_helmet.0 )\n"
            + "    - broken\n    - (v.stone.0)\n    - open(v.stone.0\n");
    Cache.armourLookMigration = false;
    new ConfigLoader().load(file.toFile());
    assertTrue(Cache.armourLookMigration);
    assertEquals(Map.of("infantry_helmet", List.of("v.carved_pumpkin.1", "v.golden_helmet.0")), Cache.legacyModels);

    Files.writeString(file, "scrap-path: x\n");
    new ConfigLoader().load(file.toFile());
    assertFalse(Cache.armourLookMigration);
    assertTrue(Cache.legacyModels.isEmpty());
  }

  @Test
  void theItemRefreshAlsoMigratesLooks() throws Exception {
    // Not outdated: only the look migration runs.
    var old = crafted("light_helmet", "iron", Material.CHAINMAIL_HELMET, null);
    assertEquals("tfmc_armor.iron_light_helmet", ia(AcItemRefresher.refreshIfOutdated(old)));
  }
}
