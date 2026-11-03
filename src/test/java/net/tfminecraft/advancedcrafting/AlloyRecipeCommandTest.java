package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.tfminecraft.advancedcrafting.database.AlloyRecipeStore;
import net.tfminecraft.advancedcrafting.lifecycle.CraftLifecycle;
import net.tfminecraft.advancedcrafting.lifecycle.KnownAlloyRecipes;
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
  KnownAlloyRecipes known;
  AlloyRecipeStore store;

  @BeforeEach
  void setupStores() throws Exception {
    manager = new CommandManager();
    tracker = new PlayerAlloyForgeTracker(temp.toFile());
    CraftLifecycle.init(tracker);
    known = new KnownAlloyRecipes(temp.toFile());
    KnownAlloyRecipes.init(known);
    store = new AlloyRecipeStore(temp.resolve("data/alloy-recipes").toFile());
    var field = AdvancedCrafting.class.getDeclaredField("alloyRecipeStore");
    field.setAccessible(true);
    field.set(plugin, store);
  }

  @AfterEach
  void clearStores() {
    CraftLifecycle.init(null);
    KnownAlloyRecipes.init(null);
  }

  Command cmd() {
    var c = mock(Command.class);
    when(c.getName()).thenReturn("alloy");
    return c;
  }

  boolean alloy(CommandSender sender, String... args) {
    return manager.onCommand(sender, cmd(), "alloy", args);
  }

  /** An alloy whose combo the forge index maps to it, as on a live server. */
  Alloy addAlloy(String id, String name, AlloyRecipe recipe) {
    var alloy = mock(Alloy.class);
    var data = mock(AlloyData.class);
    when(alloy.getId()).thenReturn(id);
    when(alloy.getName()).thenReturn(name);
    when(alloy.getData()).thenReturn(data);
    when(data.getRecipe()).thenReturn(recipe);
    AlloyManager.addAlloy(alloy);
    if (recipe != null) store.upsert(recipe, id);
    return alloy;
  }

  static AlloyRecipe combo(String base, String... catalysts) {
    return new AlloyRecipe(base, List.of(catalysts));
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
    var out = new ArrayList<String>();
    String line;
    while ((line = p.nextMessage()) != null) out.add(line);
    return out;
  }

  static void collectClicks(Component component, List<String> out) {
    ClickEvent click = component.clickEvent();
    if (click != null) out.add(click.value());
    for (Component child : component.children()) collectClicks(child, out);
  }

  @Test
  void listShowsOnlyAlloysWhoseRecipeThePlayerForged() {
    var p = server.addPlayer("Smith");
    assertTrue(alloy(p, "recipes"));
    assertTrue(p.nextMessage().contains("haven't forged any alloys"));

    var bronze = combo("copper_ingot", "tin_ingot");
    var darksteel = combo("steel_ingot", "coal");
    addAlloy("bronze", "§6Bronze", bronze);
    addAlloy("darksteel", "§8Darksteel", darksteel);
    addAlloy("secret", "§5Secret", combo("iron_ingot", "coal"));
    addAlloy("lost", "§7Lost", null);
    KnownAlloyRecipes.learn(p.getUniqueId(), darksteel);
    KnownAlloyRecipes.learn(p.getUniqueId(), bronze);
    KnownAlloyRecipes.learn(p.getUniqueId(), combo("gone_ingot", "coal"));

    assertTrue(alloy(p, "RECIPES"));
    assertTrue(p.nextMessage().contains("(2)"));
    Component line = p.nextComponentMessage();
    String plain = PlainTextComponentSerializer.plainText().serialize(line);
    assertEquals("Bronze, Darksteel", plain);
    var clicks = new ArrayList<String>();
    collectClicks(line, clicks);
    assertEquals(List.of("/alloy recipe bronze", "/alloy recipe darksteel"), clicks);
  }

  @Test
  void recipeShowsBaseAndCatalystsOnlyToTheForger() throws Exception {
    var p = server.addPlayer("Forger");
    var other = server.addPlayer("Stranger");
    ingredient("steel_ingot", "");
    ingredient("broken_gem", "");
    when(items.getCreator().getItemFromPath("v.iron_ingot"))
        .thenAnswer(i -> new ItemStack(Material.IRON_INGOT))
        .thenThrow(new IllegalStateException("no MMOItems"));
    var darkSteel = combo("steel_ingot", "broken_gem", "black_coal");
    addAlloy("dark_steel", "§8Dark Steel", darkSteel);
    KnownAlloyRecipes.learn(p.getUniqueId(), darkSteel);

    assertTrue(alloy(p, "recipe", "Dark", "Steel"));
    var lines = drain(p);
    assertEquals(4, lines.size(), lines.toString());
    assertTrue(lines.get(0).contains("Recipe for") && lines.get(0).contains("Dark Steel"));
    assertTrue(lines.get(1).contains("Base:") && lines.get(1).contains("Iron Ingot"), lines.get(1));
    assertTrue(
        lines.get(2).contains("Black Coal") && lines.get(2).contains("Broken Gem"), lines.get(2));
    assertTrue(lines.get(3).contains("any order"));
    String shown = String.join("\n", lines);
    assertFalse(shown.contains("%") || shown.toLowerCase().contains("hit"), shown);

    assertTrue(alloy(other, "recipe", "dark_steel"));
    assertEquals(List.of("§cYou don't know the recipe for §fdark_steel§c."), drain(other));
    assertTrue(alloy(other, "recipe", "No", "Such", "Alloy"));
    assertEquals(List.of("§cYou don't know the recipe for §fNo Such Alloy§c."), drain(other));
  }

  @Test
  void recipeHandlesMissingRecipeNoCatalystsAndStaleIndexes() {
    var p = server.addPlayer("Legacy");
    var plain = combo("iron_ingot");
    addAlloy("plain", "§7Plain", plain);
    KnownAlloyRecipes.learn(p.getUniqueId(), plain);
    assertTrue(alloy(p, "recipe", "plain"));
    var lines = drain(p);
    assertTrue(lines.get(1).contains("Iron Ingot"));
    assertTrue(lines.get(2).endsWith("none"));

    store.upsert(plain, "scrap");
    assertTrue(alloy(p, "recipe", "plain"));
    assertTrue(p.nextMessage().contains("no longer makes"));
    store.deleteByCombo(plain.comboKey());
    assertTrue(alloy(p, "recipe", "plain"));
    assertTrue(p.nextMessage().contains("no longer makes"));

    // A recipe recorded for the player whose alloy file later lost its recipe.
    var old = addAlloy("old", "§7Old", combo("tin_ingot", "coal"));
    KnownAlloyRecipes.learn(p.getUniqueId(), combo("tin_ingot", "coal"));
    assertTrue(alloy(p, "recipe", "old"));
    assertTrue(drain(p).get(0).contains("Recipe for"));
    when(old.getData().getRecipe()).thenReturn(null);
    assertTrue(alloy(p, "recipe", "old"));
    assertTrue(p.nextMessage().contains("don't know"));
  }

  @Test
  void heldAlloyIsShownOnlyWhenForged() {
    var p = server.addPlayer("Holder");
    var bronze = combo("copper_ingot", "tin_ingot");
    addAlloy("bronze", "§6Bronze", bronze);

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

    KnownAlloyRecipes.learn(p.getUniqueId(), bronze);
    p.getInventory().setItemInMainHand(alloyItem("bronze", "§6Bronze"));
    assertTrue(alloy(p, "recipe"));
    var lines = drain(p);
    assertTrue(lines.get(0).contains("Bronze"));
    assertTrue(lines.get(1).contains("Copper Ingot"));
    assertTrue(lines.get(2).contains("Tin Ingot"));
  }

  @Test
  void consoleAndMissingStoreAreHandled() {
    var console = mock(CommandSender.class);
    assertTrue(alloy(console, "recipes"));
    assertTrue(alloy(console, "recipe", "bronze"));
    assertTrue(alloy(console, "name", "x"));
    verify(console, times(3)).sendMessage("§cPlayers only.");
    assertFalse(alloy(console, "other"));
    assertFalse(alloy(console));

    var p = server.addPlayer("NoStore");
    var bronze = combo("copper_ingot", "tin_ingot");
    addAlloy("bronze", "§6Bronze", bronze);
    KnownAlloyRecipes.learn(p.getUniqueId(), bronze);
    KnownAlloyRecipes.init(null);
    KnownAlloyRecipes.learn(p.getUniqueId(), bronze);
    assertTrue(alloy(p, "recipes"));
    assertTrue(p.nextMessage().contains("haven't forged"));
    assertTrue(alloy(p, "recipe", "bronze"));
    assertTrue(p.nextMessage().contains("don't know"));
    assertTrue(manager.onTabComplete(p, cmd(), "alloy", new String[] {"recipe", ""}).isEmpty());
  }

  @Test
  void tabCompletionOffersOnlyOwnAlloys() {
    var p = server.addPlayer("Tabber");
    var bronze = combo("copper_ingot", "tin_ingot");
    addAlloy("bronze", "§6Bronze", bronze);
    addAlloy("brass", "§eBrass", combo("copper_ingot", "zinc"));
    KnownAlloyRecipes.learn(p.getUniqueId(), bronze);
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
  void knowledgeSurvivesRenamesAndIsSeededFromForgeHistory() throws Exception {
    var smith = UUID.randomUUID();
    var bronze = combo("copper_ingot", "tin_ingot");
    var brass = combo("copper_ingot", "zinc");
    var bronzeAlloy = addAlloy("ferrum_vex", "§6Ferrum Vex", bronze);
    addAlloy("brass", "§eBrass", brass);
    addAlloy("no_recipe", "§7No Recipe", null);
    tracker.recordForge(smith, "brass");
    tracker.recordForge(smith, "no_recipe");
    tracker.recordForge(smith, "deleted");
    KnownAlloyRecipes.learn(smith, bronze);

    // Seeded from the forge history on first use, then the new forge is added.
    var file = temp.resolve("known-alloy-recipes/" + smith + ".json");
    assertEquals("[\"copper_ingot|tin_ingot\",\"copper_ingot|zinc\"]", Files.readString(file));

    // Naming changes the id; the combo still matches.
    when(bronzeAlloy.getId()).thenReturn("bright_bronze");
    assertTrue(known.knows(smith, bronzeAlloy));

    // A fresh instance reads the file back instead of re-seeding.
    tracker.recordForge(smith, "other");
    var reread = new KnownAlloyRecipes(temp.toFile());
    assertTrue(reread.knows(smith, AlloyManager.getAlloyById("brass")));

    // Nothing on record: no file is written.
    var newcomer = UUID.randomUUID();
    assertFalse(known.knows(newcomer, bronzeAlloy));
    assertFalse(Files.exists(temp.resolve("known-alloy-recipes/" + newcomer + ".json")));
    CraftLifecycle.init(null);
    assertFalse(new KnownAlloyRecipes(temp.toFile()).knows(newcomer, bronzeAlloy));
  }

  @Test
  void knowledgeGuardsBadInputUnreadableFilesAndFailedSaves() throws Exception {
    var id = UUID.randomUUID();
    var bronze = combo("copper_ingot", "tin_ingot");
    var alloy = addAlloy("bronze", "§6Bronze", bronze);
    var noData = mock(Alloy.class);
    assertFalse(known.knows(null, alloy));
    assertFalse(known.knows(id, null));
    assertFalse(known.knows(id, noData));
    KnownAlloyRecipes.learn(null, bronze);
    KnownAlloyRecipes.learn(id, null);
    assertFalse(Files.exists(temp.resolve("known-alloy-recipes/" + id + ".json")));

    var broken = UUID.randomUUID();
    var brokenFile = temp.resolve("known-alloy-recipes/" + broken + ".json");
    Files.writeString(brokenFile, "{not json");
    assertFalse(known.knows(broken, alloy));
    assertEquals("{not json", Files.readString(brokenFile));

    var odd = UUID.randomUUID();
    Files.writeString(
        temp.resolve("known-alloy-recipes/" + odd + ".json"),
        "[\"COPPER_INGOT|TIN_INGOT\", \" \", null]");
    assertTrue(known.knows(odd, alloy));
    var notArray = UUID.randomUUID();
    Files.writeString(temp.resolve("known-alloy-recipes/" + notArray + ".json"), "{}");
    assertFalse(known.knows(notArray, alloy));

    var gone = new KnownAlloyRecipes(temp.resolve("elsewhere").toFile());
    Files.delete(temp.resolve("elsewhere/known-alloy-recipes"));
    KnownAlloyRecipes.init(gone);
    KnownAlloyRecipes.learn(id, bronze);
    assertTrue(gone.knows(id, alloy));
    assertSame(gone, KnownAlloyRecipes.get());
  }
}
