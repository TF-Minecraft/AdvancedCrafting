package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.tfminecraft.advancedcrafting.lifecycle.AlloyDiscoveredEvent;
import net.tfminecraft.advancedcrafting.lifecycle.CraftLifecycle;
import net.tfminecraft.advancedcrafting.lifecycle.PlayerAlloyForgeTracker;
import net.tfminecraft.advancedcrafting.managers.AlloyManager;
import net.tfminecraft.advancedcrafting.managers.CommandManager;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.data.AlloyData;
import net.tfminecraft.advancedcrafting.objects.data.AlloyRecipe;
import net.tfminecraft.advancedcrafting.utils.PDCKeys;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AlloyRecipeCommandTest extends CoverageSupport {
  CommandManager manager;
  PlayerAlloyForgeTracker tracker;

  @BeforeEach
  void setupTracker() {
    manager = new CommandManager();
    tracker = new PlayerAlloyForgeTracker(temp.toFile());
    CraftLifecycle.init(tracker);
  }

  @AfterEach
  void clearTracker() {
    CraftLifecycle.init(null);
  }

  Command cmd() {
    var c = mock(Command.class);
    when(c.getName()).thenReturn("alloy");
    return c;
  }

  boolean alloy(CommandSender sender, String... args) {
    return manager.onCommand(sender, cmd(), "alloy", args);
  }

  Alloy addAlloy(String id, String name, AlloyRecipe recipe) {
    var alloy = mock(Alloy.class);
    var data = mock(AlloyData.class);
    when(alloy.getId()).thenReturn(id);
    when(alloy.getName()).thenReturn(name);
    when(alloy.getData()).thenReturn(data);
    when(data.getRecipe()).thenReturn(recipe);
    AlloyManager.addAlloy(alloy);
    return alloy;
  }

  ItemStack alloyItem(String id, String displayName) {
    var item = new ItemStack(Material.IRON_INGOT);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(PDCKeys.alloyId(), PersistentDataType.STRING, id);
    if (displayName != null) meta.setDisplayName(displayName);
    item.setItemMeta(meta);
    return item;
  }

  List<String> drain(PlayerMock p) {
    var out = new java.util.ArrayList<String>();
    String line;
    while ((line = p.nextMessage()) != null) out.add(line);
    return out;
  }

  @Test
  void listShowsOnlyForgedAlloysThatStillExist() {
    var p = server.addPlayer("Smith");
    assertTrue(alloy(p, "recipes"));
    assertTrue(p.nextMessage().contains("haven't forged any alloys"));

    addAlloy("bronze", "§6Bronze", new AlloyRecipe("copper_ingot", List.of("tin_ingot")));
    addAlloy("darksteel", "§8Darksteel", new AlloyRecipe("steel_ingot", List.of("coal")));
    addAlloy("secret", "§5Secret", new AlloyRecipe("iron_ingot", List.of("coal")));
    tracker.recordForge(p.getUniqueId(), "darksteel");
    tracker.recordForge(p.getUniqueId(), "bronze");
    tracker.recordForge(p.getUniqueId(), "deleted_alloy");

    assertTrue(alloy(p, "RECIPES"));
    assertTrue(p.nextMessage().contains("(2)"));
    Component line = p.nextComponentMessage();
    String plain = PlainTextComponentSerializer.plainText().serialize(line);
    assertEquals("Bronze, Darksteel", plain);
    assertFalse(plain.contains("Secret"));
    var clicks = new java.util.ArrayList<String>();
    collectClicks(line, clicks);
    assertEquals(List.of("/alloy recipe bronze", "/alloy recipe darksteel"), clicks);
  }

  static void collectClicks(Component component, List<String> out) {
    ClickEvent click = component.clickEvent();
    if (click != null) out.add(click.value());
    for (Component child : component.children()) collectClicks(child, out);
  }

  @Test
  void recipeShowsBaseAndCatalystsOnlyToTheForger() throws Exception {
    var p = server.addPlayer("Forger");
    var other = server.addPlayer("Stranger");
    ingredient("steel_ingot", "");
    var broken = ingredient("broken_gem", "");
    when(items.getCreator().getItemFromPath("v.iron_ingot"))
        .thenAnswer(i -> new ItemStack(Material.IRON_INGOT))
        .thenThrow(new IllegalStateException("no MMOItems"));
    assertNotNull(broken);
    addAlloy(
        "dark_steel",
        "§8Dark Steel",
        new AlloyRecipe("steel_ingot", List.of("broken_gem", "black_coal")));
    tracker.recordForge(p.getUniqueId(), "dark_steel");

    assertTrue(alloy(p, "recipe", "Dark", "Steel"));
    var lines = drain(p);
    assertEquals(4, lines.size(), lines.toString());
    assertTrue(lines.get(0).contains("Recipe for") && lines.get(0).contains("Dark Steel"));
    assertTrue(lines.get(1).contains("Base:") && lines.get(1).contains("Iron Ingot"), lines.get(1));
    assertTrue(lines.get(2).contains("Black Coal") && lines.get(2).contains("Broken Gem"), lines.get(2));
    assertTrue(lines.get(3).contains("any order"));

    assertTrue(alloy(other, "recipe", "dark_steel"));
    assertEquals(List.of("§cYou don't know the recipe for §fdark_steel§c."), drain(other));
    assertTrue(alloy(other, "recipe", "No", "Such", "Alloy"));
    assertEquals(List.of("§cYou don't know the recipe for §fNo Such Alloy§c."), drain(other));
  }

  @Test
  void recipeHandlesMissingRecipeAndNoCatalysts() {
    var p = server.addPlayer("Legacy");
    addAlloy("old", "§7Old", null);
    addAlloy("plain", "§7Plain", new AlloyRecipe("iron_ingot", List.of()));
    tracker.recordForge(p.getUniqueId(), "old");
    tracker.recordForge(p.getUniqueId(), "plain");
    assertTrue(alloy(p, "recipe", "old"));
    assertTrue(p.nextMessage().contains("never written down"));
    assertTrue(alloy(p, "recipe", "plain"));
    var lines = drain(p);
    assertTrue(lines.get(1).contains("Iron Ingot"));
    assertTrue(lines.get(2).endsWith("none"));
  }

  @Test
  void heldAlloyIsShownOnlyWhenForged() {
    var p = server.addPlayer("Holder");
    addAlloy("bronze", "§6Bronze", new AlloyRecipe("copper_ingot", List.of("tin_ingot")));

    assertTrue(alloy(p, "recipe"));
    assertTrue(p.nextMessage().contains("Hold an alloy"));

    p.getInventory().setItemInMainHand(alloyItem("bronze", "§6Bronze"));
    assertTrue(alloy(p, "recipe"));
    String refusal = p.nextMessage();
    assertTrue(refusal.contains("You don't know the recipe for"), refusal);
    assertTrue(refusal.contains("§6Bronze") && refusal.contains("Forge it yourself"), refusal);

    p.getInventory().setItemInMainHand(alloyItem("unloaded", null));
    assertTrue(alloy(p, "recipe"));
    assertTrue(p.nextMessage().contains("this alloy"));

    tracker.recordForge(p.getUniqueId(), "bronze");
    p.getInventory().setItemInMainHand(alloyItem("bronze", "§6Bronze"));
    assertTrue(alloy(p, "recipe"));
    var lines = drain(p);
    assertTrue(lines.get(0).contains("Bronze"));
    assertTrue(lines.get(1).contains("Copper Ingot"));
    assertTrue(lines.get(2).contains("Tin Ingot"));
  }

  @Test
  void consoleAndMissingTrackerAreHandled() {
    var console = mock(CommandSender.class);
    assertTrue(alloy(console, "recipes"));
    assertTrue(alloy(console, "recipe", "bronze"));
    verify(console, times(2)).sendMessage("§cPlayers only.");
    assertFalse(alloy(console, "name", "x"));
    assertFalse(alloy(console));

    var p = server.addPlayer("NoTracker");
    addAlloy("bronze", "§6Bronze", new AlloyRecipe("copper_ingot", List.of("tin_ingot")));
    tracker.recordForge(p.getUniqueId(), "bronze");
    CraftLifecycle.init(null);
    assertTrue(alloy(p, "recipes"));
    assertTrue(p.nextMessage().contains("haven't forged"));
    assertTrue(alloy(p, "recipe", "bronze"));
    assertTrue(p.nextMessage().contains("don't know"));
    assertTrue(manager.onTabComplete(p, cmd(), "alloy", new String[] {"recipe", ""}).isEmpty());
    CraftLifecycle.recordForge(p.getUniqueId(), "ignored");
  }

  @Test
  void tabCompletionOffersOnlyOwnAlloys() {
    var p = server.addPlayer("Tabber");
    addAlloy("bronze", "§6Bronze", new AlloyRecipe("copper_ingot", List.of("tin_ingot")));
    addAlloy("brass", "§eBrass", new AlloyRecipe("copper_ingot", List.of("zinc")));
    tracker.recordForge(p.getUniqueId(), "bronze");
    assertEquals(
        List.of("recipe", "recipes"),
        manager.onTabComplete(p, cmd(), "alloy", new String[] {"rec"}));
    assertEquals(
        List.of("bronze"),
        manager.onTabComplete(p, cmd(), "alloy", new String[] {"recipe", "br"}));
    assertTrue(manager.onTabComplete(p, cmd(), "alloy", new String[] {"recipes", ""}).isEmpty());
    var console = mock(CommandSender.class);
    assertTrue(
        manager.onTabComplete(console, cmd(), "alloy", new String[] {"recipe", ""}).isEmpty());
  }

  @Test
  void trackerReadsAreSafeAndReadOnly() {
    var id = java.util.UUID.randomUUID();
    assertFalse(tracker.hasForged(null, "x"));
    assertFalse(tracker.hasForged(id, null));
    assertFalse(tracker.hasForged(id, " "));
    assertTrue(tracker.getForgedIds(null).isEmpty());
    tracker.recordForge(id, "Zinc_Bronze");
    tracker.recordForge(id, "brass");
    assertTrue(tracker.hasForged(id, "ZINC_BRONZE"));
    assertEquals(List.of("brass", "zinc_bronze"), List.copyOf(tracker.getForgedIds(id)));
    assertThrows(UnsupportedOperationException.class, () -> tracker.getForgedIds(id).add("x"));
  }

  @Test
  void namingTimeoutStillRecordsTheDiscoverer() throws Exception {
    var online = server.addPlayer("Online");
    var offline = server.addPlayer("Offline");
    var events = new java.util.ArrayList<String>();
    server
        .getPluginManager()
        .registerEvent(
            AlloyDiscoveredEvent.class,
            new org.bukkit.event.Listener() {},
            org.bukkit.event.EventPriority.NORMAL,
            (l, e) -> events.add(((AlloyDiscoveredEvent) e).getAlloyId()),
            org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin());
    var method =
        AlloyManager.class.getDeclaredMethod(
            "namingTimedOut", org.bukkit.entity.Player.class, String.class);
    method.setAccessible(true);
    method.invoke(null, online, "Generated");
    assertTrue(tracker.hasForged(online.getUniqueId(), "generated"));
    assertEquals(List.of("generated"), events);
    offline.disconnect();
    method.invoke(null, offline, "other");
    assertTrue(tracker.hasForged(offline.getUniqueId(), "other"));
    assertEquals(List.of("generated"), events);
  }
}
