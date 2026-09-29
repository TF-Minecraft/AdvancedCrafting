package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.utils.*;
import net.tfminecraft.tlibs.armour.*;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class RefreshListenerCoverageTest extends CoverageSupport {
  @Test
  void deferredRefreshUsesTheCurrentSlotAndUpdatesDropCursorAndArmor() throws Exception {
    when(plugin.isEnabled()).thenReturn(true);
    var p = server.addPlayer();
    var listener = new CraftRefreshListener();
    listener.start(plugin);
    listener.stop();
    var iron = ingredient("iron", "");
    iron.setRevision(1);
    var stale = iron.build();
    iron.setRevision(2);
    var fresh = iron.build();
    var drop = mock(Item.class);
    when(drop.getItemStack()).thenReturn(new ItemStack(Material.PAPER));
    listener.onDrop(new PlayerDropItemEvent(p, drop));
    server.getScheduler().performOneTick();
    verify(drop, never()).setItemStack(any());
    when(drop.getItemStack()).thenReturn(fresh);
    listener.onDrop(new PlayerDropItemEvent(p, drop));
    server.getScheduler().performOneTick();
    verify(drop, never()).setItemStack(any());
    when(drop.getItemStack()).thenReturn(stale);
    listener.onDrop(new PlayerDropItemEvent(p, drop));
    server.getScheduler().performOneTick();
    verify(drop).setItemStack(any());
    var event = mock(InventoryClickEvent.class);
    when(event.getWhoClicked()).thenReturn(mock(HumanEntity.class));
    listener.onInventoryClick(event);
    when(event.getWhoClicked()).thenReturn(p);
    when(event.getSlot()).thenReturn(0);
    p.setItemOnCursor(stale);
    listener.onInventoryClick(event);
    server.getScheduler().performOneTick();
    assertEquals(2, AcItemTags.getStoredRevision(p.getItemOnCursor()));
    when(event.getClickedInventory()).thenReturn(p.getInventory());
    p.getInventory().setItem(0, stale);
    listener.onInventoryClick(event);
    server.getScheduler().performOneTick();
    assertEquals(2, AcItemTags.getStoredRevision(p.getInventory().getItem(0)));
    for (ItemStack item :
        Arrays.asList(
            null, new ItemStack(Material.AIR), new ItemStack(Material.PAPER), fresh, stale)) {
      p.getInventory().setItem(1, item);
      listener.onHotbarSelect(new PlayerItemHeldEvent(p, 0, 1));
      server.getScheduler().performOneTick();
    }
    var equip = mock(ArmorEquipEvent.class);
    when(equip.getPlayer()).thenReturn(p);
    listener.onArmorEquip(equip);
    when(equip.getNewArmorPiece()).thenReturn(stale);
    for (ArmorType type : ArmorType.values()) {
      when(equip.getType()).thenReturn(type);
      p.getInventory().setArmorContents(new ItemStack[] {stale, stale, stale, stale});
      listener.onArmorEquip(equip);
      server.getScheduler().performOneTick();
    }
  }

  @Test
  void rebuildListenerKeepsTierMetadataAndSupportsUntaggedItems() {
    var listener = new MMOItemRebuildListener();
    var event = mock(MMOItemRebuildEvent.class);
    listener.onRebuild(event);
    var plain = new ItemStack(Material.IRON_SWORD);
    when(event.getNewItem()).thenReturn(plain);
    when(event.getOldItem()).thenReturn(plain);
    listener.onRebuild(event);
    var meta = plain.getItemMeta();
    meta.getPersistentDataContainer()
        .set(PDCKeys.craftMajorityTier(), PersistentDataType.INTEGER, 0);
    plain.setItemMeta(meta);
    listener.onRebuild(event);
    meta = plain.getItemMeta();
    meta.getPersistentDataContainer()
        .set(PDCKeys.craftMajorityTier(), PersistentDataType.INTEGER, 3);
    plain.setItemMeta(meta);
    listener.onRebuild(event);
    assertTrue(plain.getItemMeta().getLore().getFirst().contains("III"));
    listener.onRebuild(event);
    verify(event, times(2)).setNewItem(plain);
  }

  @Test
  void rebuildRestoresTierFromOldMetadataWhenBuilderOmitsIt() {
    var old = new ItemStack(Material.IRON_SWORD);
    var meta = old.getItemMeta();
    meta.setDisplayName("Sword");
    old.setItemMeta(meta);
    CraftTierLore.applyTierLine(old, 3);
    var rebuilt = new ItemStack(Material.IRON_SWORD);
    meta = rebuilt.getItemMeta();
    meta.setDisplayName("Sword");
    meta.setLore(List.of(""));
    rebuilt.setItemMeta(meta);
    var event = mock(MMOItemRebuildEvent.class);
    when(event.getOldItem()).thenReturn(old);
    when(event.getNewItem()).thenReturn(rebuilt);
    new MMOItemRebuildListener().onRebuild(event);
    assertTrue(rebuilt.getItemMeta().getLore().getFirst().contains("III"));
    assertEquals(
        3,
        rebuilt
            .getItemMeta()
            .getPersistentDataContainer()
            .get(PDCKeys.craftMajorityTier(), PersistentDataType.INTEGER));
  }
}
