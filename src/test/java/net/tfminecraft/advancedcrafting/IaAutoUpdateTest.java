package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableItemNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import java.util.function.Consumer;
import java.util.function.Function;
import net.tfminecraft.advancedcrafting.utils.IaAutoUpdate;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class IaAutoUpdateTest extends CoverageSupport {
  static boolean exposed(ReadableItemNBT nbt) {
    var item = mock(ItemStack.class);
    try (var nbts = mockStatic(NBT.class)) {
      nbts.when(() -> NBT.get(eq(item), any(Function.class)))
          .thenAnswer(call -> ((Function<ReadableItemNBT, Object>) call.getArgument(1)).apply(nbt));
      return IaAutoUpdate.isExposed(item);
    }
  }

  static ReadableItemNBT withCompound(ReadableNBT compound) {
    var nbt = mock(ReadableItemNBT.class);
    when(nbt.hasTag("itemsadder")).thenReturn(true);
    when(nbt.getCompound("itemsadder")).thenReturn(compound);
    return nbt;
  }

  static ReadableNBT flag(Boolean value) {
    var compound = mock(ReadableNBT.class);
    when(compound.getBoolean("override_auto_update")).thenReturn(value);
    return compound;
  }

  @Test
  void onlyItemsAdderLooksWithoutTheOptOutAreExposed() {
    assertFalse(exposed(mock(ReadableItemNBT.class)), "no ItemsAdder look");
    assertFalse(exposed(withCompound(null)));
    assertTrue(exposed(withCompound(flag(null))));
    assertTrue(exposed(withCompound(flag(false))));
    assertFalse(exposed(withCompound(flag(true))), "already opted out");
  }

  @Test
  void protectingSetsTheOptOut() {
    var item = mock(ItemStack.class);
    var nbt = mock(ReadWriteItemNBT.class);
    var compound = mock(ReadWriteNBT.class);
    when(nbt.getOrCreateCompound("itemsadder")).thenReturn(compound);
    try (var nbts = mockStatic(NBT.class)) {
      nbts.when(() -> NBT.modify(eq(item), any(Consumer.class)))
          .thenAnswer(
              call -> {
                ((Consumer<ReadWriteItemNBT>) call.getArgument(1)).accept(nbt);
                return null;
              });
      IaAutoUpdate.protect(item);
    }
    verify(compound).setBoolean("override_auto_update", true);
  }

  @Test
  void withoutNbtApiNothingIsExposedOrChanged() {
    var item = mock(ItemStack.class);
    try (var nbts = mockStatic(NBT.class)) {
      nbts.when(() -> NBT.get(eq(item), any(Function.class))).thenThrow(new NoClassDefFoundError("NBT"));
      nbts.when(() -> NBT.modify(eq(item), any(Consumer.class))).thenThrow(new NoClassDefFoundError("NBT"));
      assertFalse(IaAutoUpdate.isExposed(item));
      assertDoesNotThrow(() -> IaAutoUpdate.protect(item));
    }
  }
}
