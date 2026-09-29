package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.util.*;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.util.LegacyModelData;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;

class MenuCoverageTest extends CoverageSupport {
  void serverPlugin() {
    when(plugin.getServer()).thenReturn(server);
  }

  @Test
  void categoryAndRecipeMenusHaveTaggedIconsAndFillers() throws Exception {
    serverPlugin();
    try (var models = mockStatic(LegacyModelData.class)) {
      var p = server.addPlayer();
      var manager = new InventoryManager();
      manager.categoryView(p);
      var inv = p.getOpenInventory().getTopInventory();
      assertEquals(Material.BARRIER, inv.getItem(0).getType());
      assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(1).getType());
      var r = recipe("recipe: ['metal.2']");
      manager.categoryView(p);
      assertEquals(Material.PAPER, p.getOpenInventory().getTopInventory().getItem(0).getType());
      var template = new ItemStack(Material.IRON_SWORD);
      var meta = template.getItemMeta();
      models.when(() -> LegacyModelData.has(any())).thenReturn(true);
      models.when(() -> LegacyModelData.get(any())).thenReturn(7);
      when(items.getCreator().getItemFromPath("m.SWORD.TEST")).thenReturn(template);
      manager.categoryView(p);
      models.verify(() -> LegacyModelData.set(any(), eq(7)));
      manager.recipeView(p, CategoryLoader.getByString("weapons"));
      inv = p.getOpenInventory().getTopInventory();
      assertEquals(Material.IRON_SWORD, inv.getItem(0).getType());
      assertTrue(inv.getItem(0).getItemMeta().getLore().toString().contains("x2"));
      when(items.getCreator().getItemFromPath("m.SWORD.TEST")).thenReturn(null);
      manager.categoryView(p);
      assertEquals(Material.BARRIER, p.getOpenInventory().getTopInventory().getItem(0).getType());
      manager.recipeView(p, CategoryLoader.getByString("weapons"));
      assertEquals(Material.BARRIER, p.getOpenInventory().getTopInventory().getItem(0).getType());
      CategoryLoader.getByString("weapons").getRecipes().clear();
      recipe("icon: ia.missing");
      when(items.getCreator().getItemFromPath("ia.missing"))
          .thenReturn(new ItemStack(Material.DIRT));
      manager.recipeView(p, CategoryLoader.getByString("weapons"));
      assertEquals(Material.BARRIER, p.getOpenInventory().getTopInventory().getItem(0).getType());
      when(items.getCreator().getItemFromPath("ia.missing"))
          .thenReturn(new ItemStack(Material.AIR));
      manager.recipeView(p, CategoryLoader.getByString("weapons"));
      assertEquals(Material.BARRIER, p.getOpenInventory().getTopInventory().getItem(0).getType());
    }
  }

  @Test
  void statPreviewShowsOnlyMatchingTemplatesAndHandlesMissingIcons() throws Exception {
    serverPlugin();
    var p = server.addPlayer();
    var manager = new InventoryManager();
    manager.templatePreviewView(p, new StatData());
    assertTrue(p.nextMessage().contains("no stats"));
    var file =
        Files.writeString(
            temp.resolve("templates.yml"),
            "blade:\n"
                + "  name: Blade\n"
                + "  icon: v.iron_sword\n"
                + "  stats: [armor]\n"
                + "no:\n"
                + "  name: Other\n"
                + "  stats: [health]");
    new StatTemplateLoader().load(file.toFile());
    var source = new StatData(List.of("armor(4)"));
    manager.templatePreviewView(p, source);
    assertEquals(9, p.getOpenInventory().getTopInventory().getSize());
    assertEquals(Material.IRON_SWORD, p.getOpenInventory().getTopInventory().getItem(0).getType());
    when(items.getCreator().getItemFromPath("v.iron_sword")).thenReturn(null);
    manager.templatePreviewView(p, source);
    assertEquals(Material.BARRIER, p.getOpenInventory().getTopInventory().getItem(0).getType());
  }

  @Test
  void previewOfMoreThanOneInventoryOfTemplatesIsBounded() throws Exception {
    serverPlugin();
    var config = new StringBuilder();
    for (int i = 0; i < 55; i++) config.append("t").append(i).append(":\n  stats: [armor]\n");
    new StatTemplateLoader().load(Files.writeString(temp.resolve("large.yml"), config).toFile());
    var p = server.addPlayer();
    new InventoryManager().templatePreviewView(p, new StatData(List.of("armor(2)")));
    assertEquals(54, p.getOpenInventory().getTopInventory().getSize());
  }

  @Test
  void invalidAirTemplateIconUsesBarrier() throws Exception {
    serverPlugin();
    new StatTemplateLoader()
        .load(
            Files.writeString(temp.resolve("air.yml"), "blade:\n  icon: v.air\n  stats: [armor]")
                .toFile());
    var p = server.addPlayer();
    new InventoryManager().templatePreviewView(p, new StatData(List.of("armor(2)")));
    assertEquals(Material.BARRIER, p.getOpenInventory().getTopInventory().getItem(0).getType());
  }
}
