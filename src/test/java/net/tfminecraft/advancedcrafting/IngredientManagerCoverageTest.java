package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.utils.PDCKeys;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import org.bukkit.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class IngredientManagerCoverageTest extends CoverageSupport {
  @Test
  void conversionResolvesVanillaMmoAndItemsAdderItems() throws Exception {
    var manager = new IngredientManager();
    var iron = ingredient("iron", "");
    var map =
        new HashMap<String, net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient>();
    map.put("v.iron_ingot", iron);
    map.put("m.material.iron", iron);
    map.put("ia.tfmc:iron", iron);
    manager.set(map);
    assertNull(manager.get("missing"));
    var item = new ItemStack(Material.IRON_INGOT);
    try (var nbt = mockStatic(NBTItem.class);
        var custom = mockStatic(CustomStack.class)) {
      var data = mock(NBTItem.class);
      nbt.when(() -> NBTItem.get(item)).thenReturn(data);
      assertSame(iron, manager.getFromItem(item));
      when(data.hasType()).thenReturn(true);
      when(data.getType()).thenReturn("MATERIAL");
      when(data.getString("MMOITEMS_ITEM_ID")).thenReturn("IRON");
      assertSame(iron, manager.getFromItem(item));
      when(data.hasType()).thenReturn(false);
      var stack = mock(CustomStack.class);
      when(stack.getNamespacedID()).thenReturn("tfmc:iron");
      custom.when(() -> CustomStack.byItemStack(item)).thenReturn(stack);
      assertSame(iron, manager.getFromItem(item));
    }
  }

  @Test
  void converterIgnoresOtherBlocksAndShowsPreviewForManagedOrConvertibleMaterials()
      throws Exception {
    var manager = new IngredientManager();
    var player = server.addPlayer();
    var block = server.addSimpleWorld("world").getBlockAt(0, 1, 0);
    Cache.ingredientStation = "v.observer";
    var blocks = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
    tlibs.when(net.tfminecraft.tlibs.TLibs::getBlockAPI).thenReturn(blocks);
    var event =
        new PlayerInteractEvent(
            player, Action.LEFT_CLICK_BLOCK, null, block, org.bukkit.block.BlockFace.UP);
    manager.convertItem(event);
    assertFalse(event.isCancelled());
    event =
        new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, block, org.bukkit.block.BlockFace.UP);
    manager.convertItem(event);
    assertFalse(event.isCancelled());
    when(blocks.getChecker().checkBlock(block, Cache.ingredientStation)).thenReturn(true);
    manager.convertItem(event);
    assertFalse(event.isCancelled());
    var iron = ingredient("iron", "stats: ['armor(3)']");
    manager.set(new HashMap<>(Map.of("v.iron_ingot", iron)));
    try (var nbt = mockStatic(NBTItem.class);
        var custom = mockStatic(CustomStack.class);
        var menu = mockConstruction(InventoryManager.class)) {
      var data = mock(NBTItem.class);
      nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(data);
      player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
      manager.convertItem(event);
      assertFalse(event.isCancelled());
      player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_INGOT));
      manager.convertItem(event);
      assertTrue(event.isCancelled());
      verify(menu.constructed().getFirst())
          .templatePreviewView(player, iron.getIngredientData().getStatData());
      manager.convertItem(event);
      assertEquals(2, menu.constructed().size());
      var alloy =
          new Alloy("bronze", "Bronze", new AlloyData(iron, new StatData(), new HashMap<>(), null));
      AlloyManager.addAlloy(alloy);
      var item = new ItemStack(Material.IRON_INGOT);
      var meta = item.getItemMeta();
      meta.getPersistentDataContainer().set(PDCKeys.alloyId(), PersistentDataType.STRING, "bronze");
      item.setItemMeta(meta);
      player.getInventory().setItemInMainHand(item);
      manager.convertItem(event);
      assertEquals(3, menu.constructed().size());
      AlloyManager.removeAlloy("bronze");
      manager.convertItem(event);
      assertEquals(4, menu.constructed().size());
      item = iron.build();
      net.tfminecraft.advancedcrafting.loaders.IngredientLoader.oList.clear();
      player.getInventory().setItemInMainHand(item);
      manager.convertItem(event);
      assertEquals(5, menu.constructed().size());
    }
  }
}
