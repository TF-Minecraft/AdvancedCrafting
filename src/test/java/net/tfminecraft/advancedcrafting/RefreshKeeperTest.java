package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.kyori.adventure.text.Component;
import net.tfminecraft.advancedcrafting.util.LegacyModelData;
import net.tfminecraft.advancedcrafting.utils.IaAutoUpdate;
import net.tfminecraft.advancedcrafting.utils.RefreshKeeper;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class RefreshKeeperTest extends CoverageSupport {
  static final NamespacedKey MODEL = new NamespacedKey("test", "model");
  static final NamespacedKey GEMS = new NamespacedKey("geminfusion", "socket_rarities");

  MockedStatic<ItemSkinPreserver> skins;
  MockedStatic<LegacyModelData> models;
  MockedStatic<IaAutoUpdate> autoUpdate;

  @BeforeEach
  void mocks() {
    autoUpdate = mockStatic(IaAutoUpdate.class);
    autoUpdate
        .when(() -> IaAutoUpdate.isExposed(any(ItemStack.class)))
        .thenAnswer(call -> ((ItemStack) call.getArgument(0)).getType() == Material.LEATHER_HELMET);
    skins = mockStatic(ItemSkinPreserver.class);
    skins
        .when(() -> ItemSkinPreserver.apply(any(ItemStack.class), any(ItemStack.class)))
        .thenAnswer(call -> call.getArgument(1));
    // MockBukkit has no custom model data component; a PDC entry stands in for it.
    models = mockStatic(LegacyModelData.class);
    models
        .when(() -> LegacyModelData.set(any(ItemMeta.class), any()))
        .thenAnswer(
            call -> {
              ((ItemMeta) call.getArgument(0))
                  .getPersistentDataContainer()
                  .set(MODEL, PersistentDataType.INTEGER, call.getArgument(1));
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
  }

  @AfterEach
  void close() {
    skins.close();
    models.close();
    autoUpdate.close();
  }

  static NBTItem wear(Integer current, Integer max) {
    var nbt = mock(NBTItem.class);
    when(nbt.hasTag("MMOITEMS_DURABILITY")).thenReturn(current != null);
    when(nbt.hasTag("MMOITEMS_MAX_DURABILITY")).thenReturn(max != null);
    when(nbt.getInteger("MMOITEMS_DURABILITY")).thenReturn(current == null ? 0 : current);
    when(nbt.getInteger("MMOITEMS_MAX_DURABILITY")).thenReturn(max == null ? 0 : max);
    return nbt;
  }

  static MMOItem rebuilt(Double max) {
    var mmo = mock(MMOItem.class);
    when(mmo.hasData(ItemStats.MAX_DURABILITY)).thenReturn(max != null);
    if (max != null) when(mmo.getData(ItemStats.MAX_DURABILITY)).thenReturn(new DoubleData(max));
    return mmo;
  }

  static Double kept(Integer current, Integer oldMax, Double newMax) {
    var mmo = rebuilt(newMax);
    RefreshKeeper.keepWear(wear(current, oldMax), mmo);
    var captor = ArgumentCaptor.forClass(DoubleData.class);
    verify(mmo, atMost(1)).setData(eq(ItemStats.CUSTOM_DURABILITY), captor.capture());
    return captor.getAllValues().isEmpty() ? null : captor.getValue().getValue();
  }

  @Test
  void theDamageTakenCarriesOverToTheNewMaximum() {
    assertEquals(461.0, kept(478, 500, 483.0));
    assertEquals(483.0, kept(500, 500, 483.0), "an undamaged item stays undamaged");
    assertEquals(520.0, kept(500, 500, 520.0));
    assertEquals(1.0, kept(5, 500, 400.0), "a refresh never breaks an item");
    assertEquals(0.0, kept(0, 500, 400.0), "a broken item stays broken");
    assertNull(kept(null, 500, 483.0));
    assertNull(kept(478, null, 483.0));
    assertNull(kept(478, 500, null));
  }

  @Test
  void theLookNameAndForeignDataComeBackOnTheRebuiltItem() {
    var old = new ItemStack(Material.LEATHER_HELMET);
    var meta = (LeatherArmorMeta) old.getItemMeta();
    meta.setColor(Color.fromRGB(0xFFFFF3));
    meta.displayName(Component.text("Thalendorian Armor Iron Helmet"));
    meta.getPersistentDataContainer().set(GEMS, PersistentDataType.STRING, "{\"a\":\"common\"}");
    meta.getPersistentDataContainer().set(PlainKeys.QUALITY, PersistentDataType.STRING, "old");
    LegacyModelData.set(meta, 10194);
    old.setItemMeta(meta);

    var fresh = new ItemStack(Material.CHAINMAIL_HELMET);
    var freshMeta = fresh.getItemMeta();
    freshMeta.getPersistentDataContainer().set(PlainKeys.QUALITY, PersistentDataType.STRING, "new");
    fresh.setItemMeta(freshMeta);

    var result = RefreshKeeper.keepAppearance(old, fresh);
    assertEquals(Material.LEATHER_HELMET, result.getType());
    var kept = (LeatherArmorMeta) result.getItemMeta();
    assertEquals(Color.fromRGB(0xFFFFF3), kept.getColor());
    assertEquals(Component.text("Thalendorian Armor Iron Helmet"), kept.displayName());
    assertEquals(10194, LegacyModelData.get(kept));
    assertEquals("{\"a\":\"common\"}", kept.getPersistentDataContainer().get(GEMS, PersistentDataType.STRING));
    assertEquals(
        "new",
        kept.getPersistentDataContainer().get(PlainKeys.QUALITY, PersistentDataType.STRING),
        "the rebuilt item's own data wins");
    autoUpdate.verify(() -> IaAutoUpdate.protect(result));

    // A plain item without a model or name keeps what the rebuild made.
    var plain = new ItemStack(Material.IRON_SWORD);
    var plainResult = RefreshKeeper.keepAppearance(plain, new ItemStack(Material.IRON_SWORD));
    assertFalse(LegacyModelData.has(plainResult.getItemMeta()));
    assertFalse(plainResult.getItemMeta().hasDisplayName());
    autoUpdate.verify(() -> IaAutoUpdate.protect(plainResult), never());
  }

  @Test
  void itemsWithoutMetaOrWithMismatchedMetaAreLeftToTheRebuild() {
    var noMeta = mock(ItemStack.class);
    when(noMeta.getType()).thenReturn(Material.IRON_SWORD);
    var fresh = new ItemStack(Material.IRON_SWORD);
    assertSame(fresh, RefreshKeeper.keepAppearance(noMeta, fresh));

    var old = new ItemStack(Material.LEATHER_HELMET);
    var rebuiltNoMeta = mock(ItemStack.class);
    when(rebuiltNoMeta.getType()).thenReturn(Material.LEATHER_HELMET);
    assertSame(rebuiltNoMeta, RefreshKeeper.keepAppearance(old, rebuiltNoMeta));

    // An old leather look whose rebuilt meta is not leather keeps the rebuilt colour handling.
    var plainMeta = new ItemStack(Material.IRON_HELMET).getItemMeta();
    var odd = mock(ItemStack.class);
    when(odd.getType()).thenReturn(Material.LEATHER_HELMET);
    when(odd.getItemMeta()).thenReturn(plainMeta);
    assertSame(odd, RefreshKeeper.keepAppearance(old, odd));
    verify(odd).setItemMeta(plainMeta);
  }

  /** A key the rebuilt item sets itself. */
  static final class PlainKeys {
    static final NamespacedKey QUALITY = new NamespacedKey("advancedcrafting", "ac_craft_quality");
  }
}
