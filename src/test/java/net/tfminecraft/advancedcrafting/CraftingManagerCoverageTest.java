package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.enums.StationFeedback;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class CraftingManagerCoverageTest extends CoverageSupport {
  @SuppressWarnings("unchecked")
  <K, V> Map<K, V> map(CraftingManager m, String name) throws Exception {
    var f = CraftingManager.class.getDeclaredField(name);
    f.setAccessible(true);
    return (Map<K, V>) f.get(m);
  }

  Player player() {
    var p = spy(server.addPlayer());
    doNothing()
        .when(p)
        .spawnParticle(
            any(Particle.class),
            any(Location.class),
            anyInt(),
            anyDouble(),
            anyDouble(),
            anyDouble());
    doNothing()
        .when(p)
        .spawnParticle(
            any(Particle.class),
            any(Location.class),
            anyInt(),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            any(org.bukkit.block.data.BlockData.class));
    return p;
  }

  PlayerInteractEvent event(Player p, Block b, Action action) {
    return new PlayerInteractEvent(
        p, action, p.getInventory().getItemInMainHand(), b, BlockFace.UP, EquipmentSlot.HAND);
  }

  Block block() {
    var b = mock(Block.class);
    var loc = new Location(server.addSimpleWorld("world"), 0, 1, 0);
    when(b.getLocation()).thenAnswer(a -> loc.clone());
    when(b.getWorld()).thenReturn(loc.getWorld());
    return b;
  }

  BlockAPI blocks(Block b) {
    Cache.craftingStation = "v.anvil";
    var api = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
    tlibs.when(TLibs::getBlockAPI).thenReturn(api);
    when(api.getChecker().checkBlock(b, Cache.craftingStation)).thenReturn(true);
    return api;
  }

  CraftingStation station(CraftingManager m, Block b) {
    var s = mock(CraftingStation.class);
    when(s.getLoc()).thenAnswer(a -> b.getLocation());
    m.set(new HashMap<>(Map.of(b.getLocation(), s)));
    return s;
  }

  @Test
  void openingStationCreatesSelectionThenReportsMaterialFeedback() throws Exception {
    var p = player();
    var b = block();
    var api = blocks(b);
    var m = new CraftingManager();
    assertFalse(m.hasStation(b.getLocation()));
    assertNull(m.get(b.getLocation()));
    assertTrue(m.getStations().isEmpty());
    try (var menus = mockConstruction(InventoryManager.class)) {
      m.openStation(event(p, b, Action.LEFT_CLICK_BLOCK));
      m.openStation(event(p, null, Action.RIGHT_CLICK_BLOCK));
      Cache.craftingStation = null;
      m.openStation(event(p, b, Action.RIGHT_CLICK_BLOCK));
      Cache.craftingStation = "v.anvil";
      when(api.getChecker().checkBlock(b, Cache.craftingStation)).thenReturn(false);
      m.openStation(event(p, b, Action.RIGHT_CLICK_BLOCK));
      when(api.getChecker().checkBlock(b, Cache.craftingStation)).thenReturn(true);
      m.openStation(event(p, b, Action.RIGHT_CLICK_BLOCK));
      assertTrue(m.hasStation(b.getLocation()));
      assertEquals(1, m.getStations().size());
      verify(menus.constructed().getFirst()).categoryView(p);
      m.openStation(event(p, b, Action.RIGHT_CLICK_BLOCK));
      assertEquals(1, menus.constructed().size());
      map(m, "cooldown").put(p, 0L);
      m.openStation(event(p, b, Action.RIGHT_CLICK_BLOCK));
      assertEquals(2, menus.constructed().size());
      var s = station(m, b);
      when(s.hasRecipe()).thenReturn(true);
      Cache.brandingTool = "v.stick";
      map(m, "cooldown").clear();
      m.openStation(event(p, b, Action.RIGHT_CLICK_BLOCK));
      Cache.brandingTool = null;
      map(m, "cooldown").clear();
      m.openStation(event(p, b, Action.RIGHT_CLICK_BLOCK));
      p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_INGOT));
      for (StationFeedback fb :
          List.of(
              StationFeedback.NOT_INGREDIENT,
              StationFeedback.WRONG_TYPE,
              StationFeedback.CAPACITY,
              StationFeedback.NO_PERMS,
              StationFeedback.SUCCESS)) {
        map(m, "cooldown").clear();
        when(s.addMaterial(eq(p), any())).thenReturn(fb);
        m.openStation(event(p, b, Action.RIGHT_CLICK_BLOCK));
      }
      verify(s, times(5)).addMaterial(eq(p), any());
    }
  }

  @Test
  void brandingCancelsFinishesOrReportsIncompleteCraftAndToolsRouteHits() throws Exception {
    var p = player();
    var b = block();
    blocks(b);
    var m = new CraftingManager();
    var s = station(m, b);
    p.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
    Cache.brandingTool = "v.stick";
    when(items.getChecker().checkItemWithPath(any(), eq(Cache.brandingTool))).thenReturn(true);
    m.applyHit(event(p, b, Action.RIGHT_CLICK_BLOCK));
    m.applyHit(event(p, null, Action.LEFT_CLICK_BLOCK));
    m.applyHit(event(p, b, Action.LEFT_CLICK_BLOCK));
    verify(s, never()).craft(p);
    when(s.hasRecipe()).thenReturn(true);
    for (StationFeedback fb :
        List.of(
            StationFeedback.LACKING_HITS,
            StationFeedback.LACKING_ITEMS,
            StationFeedback.NONE,
            StationFeedback.SUCCESS)) {
      when(s.craft(p)).thenReturn(fb);
      m.applyHit(event(p, b, Action.LEFT_CLICK_BLOCK));
    }
    assertFalse(m.hasStation(b.getLocation()));
    s = station(m, b);
    p.setSneaking(true);
    m.applyHit(event(p, b, Action.LEFT_CLICK_BLOCK));
    verify(s).cancel();
    assertFalse(m.hasStation(b.getLocation()));
    p.setSneaking(false);
    s = station(m, b);
    Cache.brandingTool = null;
    try (var nbts = mockStatic(NBTItem.class)) {
      var nbt = mock(NBTItem.class);
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
      for (StationFeedback fb :
          List.of(
              StationFeedback.LACKING_ITEMS,
              StationFeedback.WRONG_TYPE,
              StationFeedback.NONE,
              StationFeedback.CAPACITY,
              StationFeedback.SUCCESS)) {
        when(s.hit(eq(p), any())).thenReturn(fb);
        m.applyHit(event(p, b, Action.LEFT_CLICK_BLOCK));
      }
      when(nbt.hasType()).thenReturn(true);
      when(nbt.getType()).thenReturn("v");
      when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("iron_axe");
      when(s.hit(eq(p), any())).thenReturn(StationFeedback.CAPACITY);
      var e = event(p, b, Action.LEFT_CLICK_BLOCK);
      m.applyHit(e);
      assertTrue(e.isCancelled());
      when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("missing");
      m.applyHit(event(p, b, Action.LEFT_CLICK_BLOCK));
      p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
      m.applyHit(event(p, b, Action.LEFT_CLICK_BLOCK));
      m.set(new HashMap<>());
      m.applyHit(event(p, b, Action.LEFT_CLICK_BLOCK));
    }
  }

  @Test
  void adminPendingChecksReadinessAndCompletesWithoutNormalMaterialAdmission() throws Exception {
    when(plugin.isEnabled()).thenReturn(true);
    var p = player();
    var b = block();
    blocks(b);
    var m = new CraftingManager();
    m.setAdminCraftPending(p, 70);
    assertTrue(
        (Boolean)
            invoke(m, "tryCompleteAdminCraft", new Class[] {Player.class, Block.class}, p, b));
    var s = station(m, b);
    invoke(m, "tryCompleteAdminCraft", new Class[] {Player.class, Block.class}, p, b);
    when(s.hasRecipe()).thenReturn(true);
    invoke(m, "tryCompleteAdminCraft", new Class[] {Player.class, Block.class}, p, b);
    when(s.hasAllMaterials(p)).thenReturn(true);
    for (StationFeedback fb :
        List.of(StationFeedback.LACKING_ITEMS, StationFeedback.NONE, StationFeedback.SUCCESS)) {
      m.setAdminCraftPending(p, 70);
      when(s.craft(p, 70.)).thenReturn(fb);
      map(m, "cooldown").clear();
      m.openStation(event(p, b, Action.RIGHT_CLICK_BLOCK));
    }
    assertFalse(m.hasStation(b.getLocation()));
    server.getScheduler().performTicks(601);
    m.setAdminCraftPending(p, 50);
    var pending = map(m, "adminCraftPending").get(p.getUniqueId());
    var expiry = pending.getClass().getDeclaredField("expiresAtMs");
    expiry.setAccessible(true);
    expiry.setLong(pending, 0L);
    assertNull(invoke(m, "getValidAdminCraftPending", new Class[] {Player.class}, p));
    m.setAdminCraftPending(p, 50);
    server.getScheduler().performTicks(601);
    assertNull(invoke(m, "getValidAdminCraftPending", new Class[] {Player.class}, p));
  }

  @Test
  void replacedAdminPendingSurvivesOlderTimeout() throws Exception {
    when(plugin.isEnabled()).thenReturn(true);
    var p = player();
    var m = new CraftingManager();
    m.setAdminCraftPending(p, 20);
    server.getScheduler().performTicks(300);
    m.setAdminCraftPending(p, 80);
    server.getScheduler().performTicks(301);
    assertNotNull(invoke(m, "getValidAdminCraftPending", new Class[] {Player.class}, p));
  }

  @Test
  void recipeSelectionHonorsNamespaceAndPreventsReplacingChosenRecipe() throws Exception {
    var p = player();
    var b = block();
    var m = new CraftingManager();
    var station = station(m, b);
    map(m, "currentStation").put(p, station);
    var e = mock(InventoryClickEvent.class);
    when(e.getWhoClicked()).thenReturn(p);
    var view = mock(InventoryView.class);
    when(e.getView()).thenReturn(view);
    when(view.getTitle()).thenReturn(InventoryManager.STAT_PREVIEW_TITLE);
    m.invenClick(e);
    verify(e).setCancelled(true);
    when(view.getTitle()).thenReturn("Other");
    m.invenClick(e);
    try (var menus = mockConstruction(InventoryManager.class)) {
      when(view.getTitle()).thenReturn("§7Select Category");
      m.invenClick(e);
      when(e.getCurrentItem()).thenReturn(new ItemStack(Material.PAPER));
      m.invenClick(e);
      var icon = new ItemStack(Material.PAPER);
      var meta = icon.getItemMeta();
      meta.getPersistentDataContainer()
          .set(new NamespacedKey(plugin, "ac_category"), PersistentDataType.STRING, "weapons");
      icon.setItemMeta(meta);
      when(e.getCurrentItem()).thenReturn(icon);
      m.invenClick(e);
      verify(menus.constructed().getFirst()).recipeView(p, CategoryLoader.getByString("weapons"));
    }
    when(view.getTitle()).thenReturn("§7Select Recipe");
    when(e.getCurrentItem()).thenReturn(null);
    m.invenClick(e);
    when(e.getCurrentItem()).thenReturn(new ItemStack(Material.PAPER));
    m.invenClick(e);
    var recipe = recipe("permission-namespace: smith");
    RecipeLoader.map.put("sword", recipe);
    var icon = new ItemStack(Material.PAPER);
    var meta = icon.getItemMeta();
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "ac_recipe"), PersistentDataType.STRING, "sword");
    icon.setItemMeta(meta);
    when(e.getCurrentItem()).thenReturn(icon);
    m.invenClick(e);
    verify(station, never()).setRecipe(any());
    p.setOp(true);
    m.invenClick(e);
    verify(station).setRecipe(recipe);
    when(station.hasRecipe()).thenReturn(true);
    m.invenClick(e);
    verify(station).setRecipe(recipe);
    RecipeLoader.map.put("sword", recipe(""));
    m.invenClick(e);
  }

  @Test
  void breakingWithToolPreservesStationWhileOtherBreaksRefund() throws Exception {
    var p = player();
    var b = block();
    blocks(b);
    var m = new CraftingManager();
    var s = station(m, b);
    p.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
    Cache.brandingTool = "v.stick";
    when(items.getChecker().checkItemWithPath(any(), eq(Cache.brandingTool))).thenReturn(true);
    var e = new BlockBreakEvent(b, p);
    m.breakStation(e);
    assertTrue(e.isCancelled());
    verify(s, never()).drop();
    Cache.brandingTool = null;
    try (var nbts = mockStatic(NBTItem.class)) {
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(mock(NBTItem.class));
      m.breakStation(new BlockBreakEvent(b, p));
      verify(s).drop();
      assertFalse(m.hasStation(b.getLocation()));
      m.breakStation(new BlockBreakEvent(b, p));
    }
    s = station(m, b);
    m.breakStation(new BlockBreakEvent(b, null));
    verify(s).drop();
  }

  @Test
  void cancelledBreakMustKeepMaterials() {
    var p = player();
    var b = block();
    blocks(b);
    var m = new CraftingManager();
    var s = station(m, b);
    var e = new BlockBreakEvent(b, p);
    e.setCancelled(true);
    m.breakStation(e);
    verify(s, never()).drop();
    assertTrue(m.hasStation(b.getLocation()));
  }

  @Test
  void furnitureBreakHonorsConfiguredIdToolsAndClosesAffectedMenus() throws Exception {
    var p = player();
    var b = block();
    var m = new CraftingManager();
    var e = mock(FurnitureBreakEvent.class);
    when(e.getPlayer()).thenReturn(p);
    Cache.craftingStation = null;
    m.onIaFurnitureBreak(e);
    Cache.craftingStation = "v.anvil";
    m.onIaFurnitureBreak(e);
    Cache.craftingStation = "iaf(forge:anvil)";
    m.onIaFurnitureBreak(e);
    when(e.getNamespacedID()).thenReturn("other:anvil");
    m.onIaFurnitureBreak(e);
    when(e.getNamespacedID()).thenReturn("FORGE:ANVIL");
    Cache.craftingStation = "iaf(forge:anvil";
    m.onIaFurnitureBreak(e);
    Cache.craftingStation = "iaf(forge:anvil)";
    p.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
    Cache.brandingTool = "v.stick";
    when(items.getChecker().checkItemWithPath(any(), eq(Cache.brandingTool))).thenReturn(true);
    m.onIaFurnitureBreak(e);
    verify(e).setCancelled(true);
    p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    m.onIaFurnitureBreak(e);
    var entity = mock(Entity.class);
    when(entity.getLocation()).thenAnswer(a -> b.getLocation());
    when(e.getBukkitEntity()).thenReturn(entity);
    m.onIaFurnitureBreak(e);
    var st = station(m, b);
    map(m, "currentStation").put(p, st);
    p.openInventory(Bukkit.createInventory(null, 9, "§7Select Category"));
    var other = player();
    var same = mock(CraftingStation.class);
    when(same.getLoc()).thenAnswer(a -> b.getLocation());
    map(m, "currentStation").put(other, same);
    other.openInventory(Bukkit.createInventory(null, 9, "§7Select Recipe"));
    var unrelated = player();
    var elsewhere = mock(CraftingStation.class);
    map(m, "currentStation").put(unrelated, elsewhere);
    var elsewhere2 = mock(CraftingStation.class);
    when(elsewhere2.getLoc()).thenAnswer(a -> b.getLocation().add(4, 0, 0));
    map(m, "currentStation").put(player(), elsewhere2);
    m.onIaFurnitureBreak(e);
    verify(st).drop();
    assertFalse(m.hasStation(b.getLocation()));
    assertEquals(2, map(m, "currentStation").size());
    var noWorld = new Location(null, 2, 3, 4);
    var orphan = mock(CraftingStation.class);
    m.set(new HashMap<>(Map.of(noWorld, orphan)));
    map(m, "currentStation").put(p, orphan);
    invoke(m, "discardBrokenStation", new Class[] {Location.class}, noWorld);
    verify(orphan).drop();
    when(e.getPlayer()).thenReturn(null);
    m.onIaFurnitureBreak(e);
    Cache.craftingStation = null;
    assertNull(invoke(m, "getConfiguredIaFurnitureId", new Class[] {}));
    Cache.craftingStation = "invalid";
    assertNull(invoke(m, "getConfiguredIaFurnitureId", new Class[] {}));
    assertFalse((Boolean) invoke(m, "isStationTool", new Class[] {ItemStack.class}, (Object) null));
  }

  @Test
  void unrelatedToolsBlocksAndOfflineTimeoutsDoNotConsumeStation() throws Exception {
    when(plugin.isEnabled()).thenReturn(true);
    var p = player();
    var b = block();
    var api = blocks(b);
    var m = new CraftingManager();
    var st = station(m, b);
    Cache.brandingTool = "v.stick";
    p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
    when(items.getChecker().checkItemWithPath(any(), eq(Cache.brandingTool))).thenReturn(false);
    try (var nbt = mockStatic(NBTItem.class)) {
      nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(mock(NBTItem.class));
      when(st.hit(eq(p), any())).thenReturn(StationFeedback.WRONG_TYPE);
      m.applyHit(event(p, b, Action.LEFT_CLICK_BLOCK));
      when(api.getChecker().checkBlock(b, Cache.craftingStation)).thenReturn(false);
      m.breakStation(new BlockBreakEvent(b, p));
      verify(st).drop();
    }
    m.setAdminCraftPending(p, 20);
    doReturn(false).when(p).isOnline();
    server.getScheduler().performTicks(601);
    assertNull(invoke(m, "getValidAdminCraftPending", new Class[] {Player.class}, p));
  }
}
