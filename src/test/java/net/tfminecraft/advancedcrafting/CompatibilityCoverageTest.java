package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.util.LegacyModelData;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.junit.jupiter.api.Test;

class CompatibilityCoverageTest {
  @Test
  void legacyModelSetterReplacesTheEntireComponentAndSupportsClearing() {
    var meta = mock(ItemMeta.class);
    var component = mock(CustomModelDataComponent.class);
    when(meta.getCustomModelDataComponent()).thenReturn(component);
    when(component.getFloats()).thenReturn(List.of());
    assertFalse(LegacyModelData.has(meta));
    assertThrows(IllegalStateException.class, () -> LegacyModelData.get(meta));
    when(component.getFloats()).thenReturn(List.of(7.8f));
    assertTrue(LegacyModelData.has(meta));
    assertEquals(7, LegacyModelData.get(meta));
    LegacyModelData.set(meta, 9);
    verify(component).setFloats(List.of(9f));
    verify(component).setFlags(List.of());
    verify(component).setStrings(List.of());
    verify(component).setColors(List.of());
    verify(meta).setCustomModelDataComponent(component);
    LegacyModelData.set(meta, null);
    verify(meta).setCustomModelDataComponent(null);
  }

  @Test
  void adminPermissionCheckExplainsRejection() {
    var sender = mock(CommandSender.class);
    assertFalse(AdminPermissions.require(sender));
    verify(sender).sendMessage("§cNo permission.");
    when(sender.hasPermission(AdminPermissions.PERMISSION)).thenReturn(true);
    assertTrue(AdminPermissions.require(sender));
  }
}
