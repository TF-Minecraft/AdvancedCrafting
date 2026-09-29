package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.database.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.*;
import net.tfminecraft.advancedcrafting.utils.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;

class AlloyManagerCoverageTest extends CoverageSupport {
  @SuppressWarnings("unchecked")
  <K, V> Map<K, V> map(AlloyManager m, String name) throws Exception {
    var f = AlloyManager.class.getDeclaredField(name);
    f.setAccessible(true);
    return (Map<K, V>) f.get(m);
  }

  PlayerInteractEvent event(Player p, Block b) {
    return new PlayerInteractEvent(
        p,
        Action.RIGHT_CLICK_BLOCK,
        p.getInventory().getItemInMainHand(),
        b,
        BlockFace.UP,
        EquipmentSlot.HAND);
  }

  BlockAPI blocks(Block b) {
    Cache.alloyStation = "v.blast_furnace";
    var api = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
    tlibs.when(TLibs::getBlockAPI).thenReturn(api);
    when(api.getChecker().checkBlock(b, Cache.alloyStation)).thenReturn(true);
    return api;
  }

  void clearCooldown(AlloyManager m) throws Exception {
    map(m, "cooldown").clear();
  }

  @Test
  void interactionValidatesStructureMaterialPermissionsCapacityAndCooldown() throws Exception {
    var m = new AlloyManager();
    var p = server.addPlayer();
    var b = server.addSimpleWorld("world").getBlockAt(0, 1, 0);
    var api = blocks(b);
    assertFalse(m.hasStation(b.getLocation()));
    assertNull(m.get(b.getLocation()));
    assertNull(AlloyManager.getAlloyById(null));
    AlloyManager.removeAlloy("absent");
    m.addIngredient(new PlayerInteractEvent(p, Action.LEFT_CLICK_BLOCK, null, b, BlockFace.UP));
    when(api.getChecker().checkBlock(b, Cache.alloyStation)).thenReturn(false);
    m.addIngredient(event(p, b));
    assertFalse(m.isValidAlloyStation(b));
    when(api.getChecker().checkBlock(b, Cache.alloyStation)).thenReturn(true);
    when(api.getChecker().checkBlock(b.getRelative(BlockFace.DOWN), Cache.alloyStation))
        .thenReturn(true);
    m.addIngredient(event(p, b));
    assertFalse(m.hasStation(b.getLocation()));
    when(api.getChecker().checkBlock(b.getRelative(BlockFace.DOWN), Cache.alloyStation))
        .thenReturn(false);
    p.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
    m.addIngredient(event(p, b));
    assertTrue(p.nextMessage().contains("not an ingredient"));
    var base = ingredient("iron", "base: true\npermission: smith");
    p.getInventory().setItemInMainHand(base.build());
    m.addIngredient(event(p, b));
    assertFalse(m.hasStation(b.getLocation()));
    clearCooldown(m);
    m.addIngredient(event(p, b));
    assertTrue(p.nextMessage().contains("permission"));
    p.setOp(true);
    clearCooldown(m);
    p.getInventory().setItemInMainHand(base.build());
    m.addIngredient(event(p, b));
    assertTrue(m.hasStation(b.getLocation()));
    assertEquals(1, m.get(b.getLocation()).getElementAmount());
    assertEquals(0, p.getInventory().getItemInMainHand().getAmount());
    clearCooldown(m);
    p.getInventory().setItemInMainHand(base.build());
    m.addIngredient(event(p, b));
    assertEquals(1, m.get(b.getLocation()).getElementAmount());
    for (String id : List.of("a", "b", "c", "d", "e")) {
      clearCooldown(m);
      p.getInventory().setItemInMainHand(ingredient(id, "").build());
      m.addIngredient(event(p, b));
    }
    assertEquals(5, m.get(b.getLocation()).getElementAmount());
    m.removeStation(m.get(b.getLocation()));
    clearCooldown(m);
    m.addIngredient(event(p, b));
    assertEquals(0, m.get(b.getLocation()).getElementAmount());
    clearCooldown(m);
    p.getInventory().setItemInMainHand(base.build());
    m.addIngredient(event(p, b));
    net.tfminecraft.advancedcrafting.loaders.TypeLoader.map.put(
        "wood", new IngredientType("wood", yaml("name: Wood")));
    Cache.combinations.put(base.getIngredientData().getType(), List.of());
    clearCooldown(m);
    p.getInventory().setItemInMainHand(ingredient("wood", "type: wood").build());
    m.addIngredient(event(p, b));
    assertEquals(1, m.get(b.getLocation()).getElementAmount());
    map(m, "cooldown").put(p, 0L);
    m.addIngredient(event(p, b));
  }

  @Test
  void forgeConsumesLavaOnlyForReadyPermittedStationsAndTracksNaming() throws Exception {
    var m = new AlloyManager();
    var p = server.addPlayer();
    var b = server.addSimpleWorld("world").getBlockAt(0, 1, 0);
    blocks(b);
    var e = event(p, b);
    m.forgeAlloy(e);
    assertTrue(e.isCancelled());
    var base = ingredient("iron", "base: true\npermission: smith");
    var catalyst = ingredient("copper", "");
    var station = new AlloyStation(b.getLocation());
    station.addIngredient(base);
    map(m, "stations").put(b.getLocation(), station);
    m.forgeAlloy(event(p, b));
    assertTrue(m.hasStation(b.getLocation()));
    station.addIngredient(catalyst);
    m.forgeAlloy(event(p, b));
    assertTrue(m.hasStation(b.getLocation()));
    p.setOp(true);
    try (var nbts = mockStatic(NBTItem.class);
        var custom = mockStatic(CustomStack.class);
        var forgers = mockConstruction(AlloyForger.class)) {
      var nbt = mock(NBTItem.class);
      nbts.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
      p.getInventory().setItemInMainHand(new ItemStack(Material.LAVA_BUCKET));
      when(nbt.hasType()).thenReturn(true);
      m.addIngredient(event(p, b));
      assertEquals(0, forgers.constructed().size());
      clearCooldown(m);
      when(nbt.hasType()).thenReturn(false);
      custom.when(() -> CustomStack.byItemStack(any())).thenReturn(mock(CustomStack.class));
      m.addIngredient(event(p, b));
      assertEquals(0, forgers.constructed().size());
      clearCooldown(m);
      custom.when(() -> CustomStack.byItemStack(any())).thenReturn(null);
      m.addIngredient(event(p, b));
      assertEquals(Material.BUCKET, p.getInventory().getItemInMainHand().getType());
      assertFalse(m.hasStation(b.getLocation()));
    }
    var alloy = mock(Alloy.class);
    var named = new NamableAlloy(alloy, new ItemStack(Material.IRON_INGOT));
    map(m, "stations").put(b.getLocation(), station);
    p.getInventory().setItemInMainHand(new ItemStack(Material.LAVA_BUCKET));
    try (var forgers =
        mockConstruction(AlloyForger.class, (mock, ctx) -> when(mock.forge(p)).thenReturn(named))) {
      m.forgeAlloy(event(p, b));
      assertSame(named, map(m, "naming").get(p));
    }
    when(plugin.isEnabled()).thenReturn(true);
    m.start();
    server.getScheduler().performTicks(1201);
    assertFalse(map(m, "naming").containsKey(p));
  }

  @Test
  void namingRenamesBothTrackedDropAndMatchingInventoryStack() throws Exception {
    var m = new AlloyManager();
    var p = server.addPlayer();
    m.nameAlloy(p, "new");
    assertTrue(p.nextMessage().contains("no alloy"));
    var base = ingredient("iron", "base: true");
    var alloy =
        spy(new Alloy("old", "Old", new AlloyData(base, new StatData(), new HashMap<>(), null)));
    var old = new ItemStack(Material.IRON_INGOT);
    var renamed = new ItemStack(Material.GOLD_INGOT);
    doReturn(renamed).when(alloy).build();
    AlloyManager.addAlloy(alloy);
    map(m, "naming").put(p, new NamableAlloy(alloy, old));
    m.nameAlloy(p, "invalid1");
    assertTrue(map(m, "naming").containsKey(p));
    p.getInventory().setItem(1, new ItemStack(Material.PAPER));
    p.getInventory().setItem(3, old);
    try (var db = mockConstruction(AlloyDatabase.class)) {
      m.nameAlloy(p, "new_alloy");
      assertNull(AlloyManager.getAlloyById("old"));
      assertSame(alloy, AlloyManager.getAlloyById("new_alloy"));
      assertEquals(Material.GOLD_INGOT, p.getInventory().getItem(3).getType());
      assertEquals(Material.GOLD_INGOT, old.getType());
      verify(db.constructed().getFirst()).editAlloy(alloy, "old");
    }
    var other = spy(new Alloy("other", "Other", alloy.getData()));
    doReturn(renamed).when(other).build();
    map(m, "naming").put(p, new NamableAlloy(other, new ItemStack(Material.DIAMOND)));
    try (var db = mockConstruction(AlloyDatabase.class)) {
      m.nameAlloy(p, "another");
      assertFalse(map(m, "naming").containsKey(p));
    }
  }

  @Test
  void breakDropsExistingStationOnlyAtValidBaseBlock() throws Exception {
    var m = new AlloyManager();
    var p = server.addPlayer();
    var b = server.addSimpleWorld("world").getBlockAt(0, 1, 0);
    var api = blocks(b);
    var station = mock(AlloyStation.class);
    when(station.getLocation()).thenReturn(b.getLocation());
    m.breakStation(new BlockBreakEvent(b, p));
    map(m, "stations").put(b.getLocation(), station);
    when(api.getChecker().checkBlock(b, Cache.alloyStation)).thenReturn(false);
    m.breakStation(new BlockBreakEvent(b, p));
    verify(station, never()).drop();
    when(api.getChecker().checkBlock(b, Cache.alloyStation)).thenReturn(true);
    m.breakStation(new BlockBreakEvent(b, p));
    verify(station).drop();
    assertFalse(m.hasStation(b.getLocation()));
  }

  @Test
  void namingCollisionMustPreserveBothDefinitions() throws Exception {
    var m = new AlloyManager();
    var p = server.addPlayer();
    var base = ingredient("iron", "");
    var a =
        spy(new Alloy("old", "Old", new AlloyData(base, new StatData(), new HashMap<>(), null)));
    doReturn(new ItemStack(Material.IRON_INGOT)).when(a).build();
    var existing = new Alloy("taken", "Taken", a.getData());
    AlloyManager.addAlloy(a);
    AlloyManager.addAlloy(existing);
    map(m, "naming").put(p, new NamableAlloy(a, new ItemStack(Material.IRON_INGOT)));
    try (var db = mockConstruction(AlloyDatabase.class)) {
      m.nameAlloy(p, "taken");
      assertSame(a, AlloyManager.getAlloyById("old"));
      assertSame(existing, AlloyManager.getAlloyById("taken"));
      assertTrue(db.constructed().isEmpty());
    }
    try (var db = mockConstruction(AlloyDatabase.class)) {
      m.nameAlloy(p, "old");
      assertSame(a, AlloyManager.getAlloyById("old"));
      assertFalse(map(m, "naming").containsKey(p));
    }
  }

  @Test
  void cancelledBreakAndOffhandInteractionCannotAlterForge() throws Exception {
    var m = new AlloyManager();
    var p = server.addPlayer();
    var b = server.addSimpleWorld("world").getBlockAt(0, 1, 0);
    blocks(b);
    var station = mock(AlloyStation.class);
    map(m, "stations").put(b.getLocation(), station);
    var breaking = new BlockBreakEvent(b, p);
    breaking.setCancelled(true);
    m.breakStation(breaking);
    verify(station, never()).drop();
    assertTrue(m.hasStation(b.getLocation()));
    var iron = ingredient("iron", "base: true");
    p.getInventory().setItemInMainHand(iron.build());
    var offhand =
        new PlayerInteractEvent(
            p, Action.RIGHT_CLICK_BLOCK, null, b, BlockFace.UP, EquipmentSlot.OFF_HAND);
    offhand.setCancelled(true);
    m.addIngredient(offhand);
    offhand.setCancelled(false);
    m.addIngredient(offhand);
    verify(station, never()).addIngredient(any());
  }

  @Test
  void staleTaggedIngredientDoesNotCrashForgeInteraction() throws Exception {
    var m = new AlloyManager();
    var p = server.addPlayer();
    var b = server.addSimpleWorld("world").getBlockAt(0, 1, 0);
    blocks(b);
    var iron = ingredient("iron", "base: true");
    p.getInventory().setItemInMainHand(iron.build());
    net.tfminecraft.advancedcrafting.loaders.IngredientLoader.oList.clear();
    assertDoesNotThrow(() -> m.addIngredient(event(p, b)));
    assertFalse(m.hasStation(b.getLocation()));
  }
}
