package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.data.random.RandomStatData;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.advancedcrafting.objects.data.CraftProvenance;
import net.tfminecraft.advancedcrafting.utils.AcItemRefresher;
import net.tfminecraft.advancedcrafting.utils.CraftTierLore;
import net.tfminecraft.advancedcrafting.utils.IaAutoUpdate;
import net.tfminecraft.advancedcrafting.utils.PDCKeys;
import net.tfminecraft.advancedcrafting.utils.RequiredClassSync;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class RequiredClassSyncTest extends CoverageSupport {
  static final List<String> OLD = List.of("Crusader", "Templar", "Cleric", "Juggernaut");
  static final List<String> NOW = List.of("Crusader", "Templar", "Cleric", "Juggernaut", "Warden");

  // --- update(): the stat change on a real MMOItem ---

  @Test
  void oldListBecomesTemplateListInTemplateOrder() throws Exception {
    var mmo = warhammer();
    mmo.setData(ItemStats.REQUIRED_CLASS, new StringListData(OLD));
    assertEquals(OLD, update(mmo, NOW));
    assertEquals(NOW, classes(mmo));
  }

  @Test
  void matchingSetsIgnoreOrderSpacingAndRepeats() throws Exception {
    var mmo = warhammer();
    var data = new StringListData(List.of(" Warden", "Juggernaut ", "Cleric", "Templar", "Crusader", "Cleric"));
    mmo.setData(ItemStats.REQUIRED_CLASS, data);
    assertNull(update(mmo, NOW));
    assertSame(data, mmo.getData(ItemStats.REQUIRED_CLASS));
    assertEquals(Set.of("Warden", "Cleric"), RequiredClassSync.classSet(List.of(" Warden ", "", "Cleric", "Warden")));
  }

  @Test
  void templateWithoutClassesRemovesTheRestriction() throws Exception {
    var mmo = warhammer();
    mmo.setData(ItemStats.REQUIRED_CLASS, new StringListData(OLD));
    assertEquals(OLD, update(mmo, List.of()));
    assertFalse(mmo.hasData(ItemStats.REQUIRED_CLASS));
    // An item without classes and a template without classes already match.
    assertNull(update(mmo, List.of()));
  }

  @Test
  void otherStatsAndGemHistoriesStayTheSame() throws Exception {
    var mmo = warhammer();
    var damage = new DoubleData(12);
    mmo.setData(ItemStats.ATTACK_DAMAGE, damage);
    var damageHistory = new StatHistory(mmo, ItemStats.ATTACK_DAMAGE, new DoubleData(9));
    var gem = UUID.randomUUID();
    var gemDamage = new DoubleData(3);
    damageHistory.registerGemstoneData(gem, gemDamage);
    mmo.setStatHistory(ItemStats.ATTACK_DAMAGE, damageHistory);
    mmo.setData(ItemStats.REQUIRED_CLASS, new StringListData(OLD));
    var classHistory = new StatHistory(mmo, ItemStats.REQUIRED_CLASS, new StringListData(OLD));
    var gemClass = new StringListData(List.of("Bard"));
    classHistory.registerGemstoneData(gem, gemClass);
    mmo.setStatHistory(ItemStats.REQUIRED_CLASS, classHistory);

    assertEquals(OLD, update(mmo, NOW));

    // Only the class history's original layer changes; its gem layer stays.
    assertEquals(NOW, ((StringListData) classHistory.getOriginalData()).getList());
    assertSame(gemClass, classHistory.getGemstoneData(gem));
    assertEquals(NOW, classes(mmo));
    assertSame(damage, mmo.getData(ItemStats.ATTACK_DAMAGE));
    assertSame(damageHistory, mmo.getStatHistory(ItemStats.ATTACK_DAMAGE));
    assertSame(gemDamage, damageHistory.getGemstoneData(gem));
    assertEquals(9, ((DoubleData) damageHistory.getOriginalData()).getValue());
    assertEquals(Set.of(ItemStats.ATTACK_DAMAGE, ItemStats.REQUIRED_CLASS), mmo.getStats());
    // The history's original layer is what is compared, so the next refresh leaves it alone.
    assertNull(update(mmo, NOW));
  }

  // --- sync(): the item refresh ---

  @Test
  void oldWarhammerGainsWardenThroughAnMmoItemsRebuild() throws Exception {
    var item = crafted(2);
    CraftTierLore.applyPdc(item, 0, 3);
    item.editMeta(meta -> meta.getPersistentDataContainer().set(PDCKeys.alloyId(), PersistentDataType.STRING, "kept"));
    var nbt = nbt("Crusader, Templar, Cleric, Juggernaut", false);
    template(nbt, NOW);
    var rebuilt = new ItemStack(Material.IRON_AXE);
    try (var nbts = mockStatic(NBTItem.class);
        var skins = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class);
        var models = mockStatic(net.tfminecraft.advancedcrafting.util.LegacyModelData.class);
        var autoUpdate = mockStatic(IaAutoUpdate.class);
        var mmos = live(new StringListData(OLD), null, rebuilt)) {
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
      skins
          .when(
              () ->
                  net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.apply(
                      any(ItemStack.class), any(ItemStack.class)))
          .thenAnswer(call -> call.getArgument(1));

      var result = RequiredClassSync.sync(item);

      assertSame(rebuilt, result);
      assertEquals(2, result.getAmount());
      var tags = result.getItemMeta().getPersistentDataContainer();
      assertEquals(3, tags.get(PDCKeys.craftMajorityTier(), PersistentDataType.INTEGER));
      assertEquals("kept", tags.get(PDCKeys.alloyId(), PersistentDataType.STRING));
      assertNotNull(CraftProvenance.readFrom(result));
      assertTrue(result.getItemMeta().hasLore());
      var mmo = mmos.constructed().get(0);
      verify(mmo).setData(eq(ItemStats.REQUIRED_CLASS), argThat(d -> NOW.equals(((StringListData) d).getList())));
      verify(mmo, times(1)).setData(any(), any());
      verify(mmo, never()).removeData(any());
    }
  }

  @Test
  void templateWithoutClassesRebuildsWithoutTheRestriction() throws Exception {
    var item = crafted(1);
    var nbt = nbt("Crusader, Templar", false);
    template(nbt, null);
    var rebuilt = new ItemStack(Material.IRON_AXE);
    try (var nbts = mockStatic(NBTItem.class);
        var skins = mockStatic(net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.class);
        var models = mockStatic(net.tfminecraft.advancedcrafting.util.LegacyModelData.class);
        var autoUpdate = mockStatic(IaAutoUpdate.class);
        var mmos = live(new StringListData(List.of("Crusader", "Templar")), null, rebuilt)) {
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
      skins
          .when(
              () ->
                  net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver.apply(
                      any(ItemStack.class), any(ItemStack.class)))
          .thenAnswer(call -> call.getArgument(1));

      var result = RequiredClassSync.sync(item);

      assertSame(rebuilt, result);
      assertNull(
          result.getItemMeta().getPersistentDataContainer().get(PDCKeys.craftMajorityTier(), PersistentDataType.INTEGER));
      var mmo = mmos.constructed().get(0);
      verify(mmo).removeData(ItemStats.REQUIRED_CLASS);
      verify(mmo, never()).setData(any(), any());
    }
  }

  @Test
  void matchingItemIsNotReadOrRebuilt() throws Exception {
    var item = crafted(1);
    var nbt = nbt("Juggernaut,Crusader,  Templar , Cleric, Warden", false);
    template(nbt, NOW);
    try (var nbts = mockStatic(NBTItem.class); var mmos = live(null, null, null)) {
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
      assertSame(item, RequiredClassSync.sync(item));
      assertSame(item, AcItemRefresher.refreshIfOutdated(item));
      assertTrue(mmos.constructed().isEmpty());
    }
  }

  @Test
  void matchingHistoryIsReadButNotRebuilt() throws Exception {
    var item = crafted(1);
    var nbt = nbt("Crusader, Templar, Cleric, Juggernaut, Warden, Bard", true);
    template(nbt, NOW);
    var history = mock(StatHistory.class);
    when(history.getOriginalData()).thenReturn(new StringListData(NOW));
    try (var nbts = mockStatic(NBTItem.class); var mmos = live(null, history, null)) {
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
      assertSame(item, RequiredClassSync.sync(item));
      var mmo = mmos.constructed().get(0);
      // The stubbed builder would return null, so reaching the rebuild would fail the sync.
      verify(mmo, never()).setData(any(), any());
      verify(mmo, never()).removeData(any());
      verify(history, never()).setOriginalData(any());
    }
  }

  @Test
  void itemsWithoutATemplateToFollowAreLeftAlone() throws Exception {
    assertNull(RequiredClassSync.sync(null));
    var air = new ItemStack(Material.AIR);
    assertSame(air, RequiredClassSync.sync(air));
    var item = crafted(1);
    var unknown = nbt("Crusader", false);
    when(MMOItems.plugin.getTemplates().getTemplate(unknown)).thenReturn(null);
    var vanilla = mock(NBTItem.class);
    try (var nbts = mockStatic(NBTItem.class); var mmos = live(null, null, null)) {
      // Not an AdvancedCrafting craft: never read.
      var plain = new ItemStack(Material.IRON_AXE);
      assertSame(plain, RequiredClassSync.sync(plain));
      nbts.verifyNoInteractions();
      // Unknown or removed MMOItems template.
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(unknown);
      assertSame(item, RequiredClassSync.sync(item));
      // Not an MMOItems item at all.
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(vanilla);
      assertSame(item, RequiredClassSync.sync(item));
      assertTrue(mmos.constructed().isEmpty());
    }
  }

  private static MMOItem warhammer() {
    return new MMOItem(mock(Type.class), "CUSTOM_WARHAMMER");
  }

  private static List<String> classes(MMOItem mmo) {
    return ((StringListData) mmo.getData(ItemStats.REQUIRED_CLASS)).getList();
  }

  private static List<String> update(MMOItem mmo, List<String> wanted) {
    return RequiredClassSync.update(mmo, wanted);
  }

  private static ItemStack crafted(int amount) {
    var item = new ItemStack(Material.IRON_AXE, amount);
    new CraftProvenance("warhammer", "fine", List.of(), 0).applyTo(item);
    return item;
  }

  private static NBTItem nbt(String classes, boolean history) {
    var nbt = mock(NBTItem.class);
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getType()).thenReturn("CUSTOM_WARHAMMER");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("PARSUSITE");
    when(nbt.getString("MMOITEMS_REQUIRED_CLASS")).thenReturn(classes);
    when(nbt.hasTag("HSTRY_REQUIRED_CLASS")).thenReturn(history);
    return nbt;
  }

  private static void template(NBTItem nbt, List<String> classes) {
    var template = mock(MMOItemTemplate.class);
    Map<ItemStat, RandomStatData> base = new HashMap<>();
    if (classes != null) base.put(ItemStats.REQUIRED_CLASS, new StringListData(classes));
    when(template.getBaseItemData()).thenReturn(base);
    when(MMOItems.plugin.getTemplates().getTemplate(nbt)).thenReturn(template);
  }

  private static MockedConstruction<LiveMMOItem> live(
      StringListData classes, StatHistory history, ItemStack rebuilt) {
    return mockConstruction(
        LiveMMOItem.class,
        withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
        (mock, ctx) -> {
          when(mock.getStatHistory(any())).thenReturn(history);
          when(mock.getData(ItemStats.REQUIRED_CLASS)).thenReturn(classes);
          when(mock.newBuilder().build()).thenReturn(rebuilt);
        });
  }
}
