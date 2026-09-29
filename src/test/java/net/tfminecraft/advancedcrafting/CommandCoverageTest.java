package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.database.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class CommandCoverageTest extends CoverageSupport {
  Command cmd(String name) {
    var c = mock(Command.class);
    when(c.getName()).thenReturn(name);
    return c;
  }

  boolean run(CommandManager m, CommandSender sender, String... args) {
    return m.onCommand(sender, cmd("ac"), "ac", args);
  }

  @Test
  void dispatchRequiresAdminAndPlayerWhereApplicable() throws Exception {
    var manager = new CommandManager();
    var p = server.addPlayer();
    var console = mock(CommandSender.class);
    assertFalse(run(manager, p));
    assertFalse(run(manager, p, "unknown"));
    assertFalse(manager.onCommand(console, cmd("other"), "x", new String[] {"name", "x"}));
    assertFalse(manager.onCommand(p, cmd("other"), "x", new String[] {"name", "x"}));
    for (String[] args :
        List.of(
            new String[] {"reload"},
            new String[] {"sync", "recipes"},
            new String[] {"refresh"},
            new String[] {"inspect"},
            new String[] {"give", "alloy", "iron"},
            new String[] {"alloy", "info", "iron"},
            new String[] {"craft", "10"})) assertTrue(run(manager, p, args));
    p.setOp(true);
    when(console.hasPermission(AdminPermissions.PERMISSION)).thenReturn(true);
    assertTrue(run(manager, p, "reload"));
    verify(plugin).reloadMessage(p);
    assertTrue(run(manager, console, "reload"));
    verify(plugin).reload();
    assertTrue(run(manager, console, "refresh"));
    assertTrue(run(manager, p, "refresh"));
    assertTrue(run(manager, console, "inspect"));
    assertTrue(run(manager, console, "craft", "10"));
    assertTrue(run(manager, p, "craft", "bad"));
    try (var sync = mockStatic(AlloyRecipeSync.class);
        var info = mockStatic(AlloyInfoFormatter.class);
        var inspect = mockStatic(CraftInspectFormatter.class);
        var ac = mockStatic(AdvancedCrafting.class, CALLS_REAL_METHODS)) {
      var crafting = mock(CraftingManager.class);
      var alloys = mock(AlloyManager.class);
      ac.when(AdvancedCrafting::getCraftingManager).thenReturn(crafting);
      ac.when(AdvancedCrafting::getAlloyManager).thenReturn(alloys);
      assertTrue(run(manager, p, "sync", "recipes"));
      assertTrue(run(manager, p, "sync", "recipes", "other"));
      assertTrue(run(manager, p, "sync", "recipes", "repair"));
      sync.verify(() -> AlloyRecipeSync.run(p, true));
      assertTrue(run(manager, p, "alloy", "info", "iron"));
      info.verify(() -> AlloyInfoFormatter.send(p, "iron"));
      assertTrue(run(manager, p, "inspect"));
      inspect.verify(() -> CraftInspectFormatter.send(p));
      for (String value : List.of("-5", "50", "150")) assertTrue(run(manager, p, "craft", value));
      verify(crafting).setAdminCraftPending(p, 0);
      verify(crafting).setAdminCraftPending(p, 50);
      verify(crafting).setAdminCraftPending(p, 100);
      assertFalse(manager.onCommand(p, cmd("alloy"), "alloy", new String[] {}));
      assertFalse(manager.onCommand(p, cmd("alloy"), "alloy", new String[] {"other"}));
      assertFalse(manager.onCommand(p, cmd("alloy"), "alloy", new String[] {"name"}));
      assertTrue(manager.onCommand(p, cmd("alloy"), "alloy", new String[] {"name", "steel"}));
      verify(alloys).nameAlloy(p, "steel");
    }
    for (String[] args :
        List.of(
            new String[] {"give", "other", "x"},
            new String[] {"alloy", "other", "x"},
            new String[] {"sync", "other"},
            new String[] {"craft"},
            new String[] {"give"})) assertFalse(run(manager, p, args));
  }

  @Test
  void refreshReportsErrorsUnchangedAndUpdatedStacks() {
    var manager = new CommandManager();
    var p = server.addPlayer();
    p.setOp(true);
    var item = new ItemStack(Material.IRON_SWORD);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(PDCKeys.craftRecipe(), PersistentDataType.STRING, "sword");
    item.setItemMeta(meta);
    p.getInventory().setItemInMainHand(item);
    try (var refresh = mockStatic(CraftStatRefresher.class)) {
      refresh
          .when(() -> CraftStatRefresher.refresh(any(ItemStack.class), eq(true)))
          .thenReturn(CraftStatRefresher.RefreshResult.failed("unknown"));
      assertTrue(run(manager, p, "refresh"));
      assertTrue(p.nextMessage().contains("Refresh failed"));
      refresh
          .when(() -> CraftStatRefresher.refresh(any(ItemStack.class), eq(true)))
          .thenReturn(CraftStatRefresher.RefreshResult.unchanged());
      run(manager, p, "refresh");
      assertTrue(p.nextMessage().contains("No changes"));
      var updated = new ItemStack(Material.DIAMOND_SWORD);
      refresh
          .when(() -> CraftStatRefresher.refresh(any(ItemStack.class), eq(true)))
          .thenReturn(CraftStatRefresher.RefreshResult.updated(updated, List.of()));
      run(manager, p, "refresh");
      assertEquals(updated, p.getInventory().getItemInMainHand());
    }
  }

  @Test
  void alloyGiveSupportsConsoleTargetsSelfAndOverflow() {
    var manager = new CommandManager();
    var p = server.addPlayer("Admin");
    p.setOp(true);
    var target = server.addPlayer("Target");
    var console = mock(CommandSender.class);
    when(console.hasPermission(AdminPermissions.PERMISSION)).thenReturn(true);
    assertTrue(run(manager, p, "give", "alloy", "missing"));
    var alloy = mock(Alloy.class);
    when(alloy.getId()).thenReturn("bronze");
    when(alloy.getName()).thenReturn("Bronze");
    when(alloy.build()).thenAnswer(i -> new ItemStack(Material.IRON_INGOT));
    AlloyManager.addAlloy(alloy);
    assertTrue(run(manager, console, "give", "alloy", "bronze"));
    assertTrue(run(manager, console, "give", "alloy", "bronze", "missing"));
    assertTrue(run(manager, p, "give", "alloy", "bronze"));
    assertEquals(Material.IRON_INGOT, p.getInventory().getItem(0).getType());
    assertTrue(run(manager, p, "give", "alloy", "bronze", "Target"));
    assertEquals(Material.IRON_INGOT, target.getInventory().getItem(0).getType());
    var full = new ItemStack[target.getInventory().getSize()];
    Arrays.fill(full, new ItemStack(Material.STONE, 64));
    target.getInventory().setContents(full);
    run(manager, p, "give", "alloy", "bronze", "Target");
    assertFalse(target.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).isEmpty());
    AlloyManager.removeAlloy("bronze");
    try (var db =
        mockConstruction(
            AlloyDatabase.class, (mock, ctx) -> when(mock.loadAlloy("bronze")).thenReturn(alloy))) {
      assertTrue(run(manager, p, "give", "alloy", "bronze"));
      assertSame(alloy, AlloyManager.getAlloyById("bronze"));
    }
  }

  @Test
  void tabCompletionFiltersCommandsIdsAndOnlinePlayers() {
    var manager = new CommandManager();
    var p = server.addPlayer("Alice");
    assertTrue(manager.onTabComplete(p, cmd("ac"), "ac", new String[] {}).isEmpty());
    p.setOp(true);
    assertEquals(7, manager.onTabComplete(p, cmd("ac"), "ac", new String[] {}).size());
    assertEquals(
        List.of("reload", "refresh"),
        manager.onTabComplete(p, cmd("ac"), "ac", new String[] {"RE"}));
    assertEquals(7, manager.onTabComplete(p, cmd("ac"), "ac", new String[] {null}).size());
    var alloy = mock(Alloy.class);
    when(alloy.getId()).thenReturn("bronze");
    AlloyManager.addAlloy(alloy);
    Map<List<String>, List<String>> cases = new LinkedHashMap<>();
    cases.put(List.of("sync", ""), List.of("recipes"));
    cases.put(List.of("give", ""), List.of("alloy"));
    cases.put(List.of("alloy", ""), List.of("info"));
    cases.put(List.of("craft", "1"), List.of("100"));
    cases.put(List.of("other", ""), List.of());
    cases.put(List.of("sync", "recipes", ""), List.of("repair"));
    cases.put(List.of("sync", "bad", ""), List.of());
    cases.put(List.of("give", "alloy", ""), List.of("bronze"));
    cases.put(List.of("give", "bad", ""), List.of());
    cases.put(List.of("alloy", "info", ""), List.of("bronze"));
    cases.put(List.of("alloy", "bad", ""), List.of());
    cases.put(List.of("other", "bad", ""), List.of());
    cases.put(List.of("give", "alloy", "bronze", "A"), List.of("Alice"));
    cases.put(List.of("give", "bad", "bronze", ""), List.of());
    cases.put(List.of("other", "bad", "bronze", ""), List.of());
    cases.put(List.of("other", "bad", "bronze", "", ""), List.of());
    for (var entry : cases.entrySet())
      assertEquals(
          entry.getValue(),
          manager.onTabComplete(p, cmd("ac"), "ac", entry.getKey().toArray(String[]::new)),
          entry.getKey().toString());
    assertEquals(List.of("name"), manager.onTabComplete(p, cmd("alloy"), "alloy", new String[] {}));
    assertEquals(
        List.of("name"), manager.onTabComplete(p, cmd("alloy"), "alloy", new String[] {"n"}));
    assertTrue(
        manager.onTabComplete(p, cmd("alloy"), "alloy", new String[] {"name", ""}).isEmpty());
    assertTrue(manager.onTabComplete(p, cmd("other"), "x", new String[] {}).isEmpty());
  }

  @Test
  void adminCraftRejectsNonFiniteQuality() {
    var manager = new CommandManager();
    var p = server.addPlayer();
    p.setOp(true);
    try (var ac = mockStatic(AdvancedCrafting.class, CALLS_REAL_METHODS)) {
      var crafting = mock(CraftingManager.class);
      ac.when(AdvancedCrafting::getCraftingManager).thenReturn(crafting);
      for (String value : List.of("NaN", "Infinity", "-Infinity"))
        assertTrue(run(manager, p, "craft", value));
      verifyNoInteractions(crafting);
    }
  }
}
