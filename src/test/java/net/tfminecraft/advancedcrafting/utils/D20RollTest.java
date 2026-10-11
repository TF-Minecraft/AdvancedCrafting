package net.tfminecraft.advancedcrafting.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.function.IntSupplier;
import net.Indyuce.mmocore.api.player.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class D20RollTest {
  IntSupplier previousDie;

  @BeforeEach
  void setup() {
    previousDie = D20Roll.die;
    D20Roll.load(null);
  }

  @AfterEach
  void cleanup() {
    D20Roll.die = previousDie;
    D20Roll.load(null);
    if (MockBukkit.isMocked()) MockBukkit.unmock();
  }

  static YamlConfiguration config(String text) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  @Test
  void defaultModifiersMatchTheRollTableAndClampPastItsEnds() {
    assertEquals(-5, D20Roll.modifier(-3));
    assertEquals(-5, D20Roll.modifier(1));
    assertEquals(-4, D20Roll.modifier(2));
    assertEquals(0, D20Roll.modifier(11));
    assertEquals(2, D20Roll.modifier(14));
    assertEquals(5, D20Roll.modifier(20));
    assertEquals(5, D20Roll.modifier(25));
  }

  @Test
  void naturalTwentyAlwaysSucceedsAndNaturalOneAlwaysFails() {
    D20Roll crit = new D20Roll(20, -5);
    assertTrue(crit.isCritical());
    assertFalse(crit.isFumble());
    assertEquals(15, crit.getTotal());
    assertTrue(crit.meets(20));
    assertEquals("§6Natural 20!", crit.describe());
    D20Roll fumble = new D20Roll(1, 5);
    assertTrue(fumble.isFumble());
    assertFalse(fumble.isCritical());
    assertEquals(1, fumble.getTotal());
    assertFalse(fumble.meets(1));
    assertEquals("§cNatural 1!", fumble.describe());
    assertEquals(1, fumble.getNatural());
    assertEquals(5, fumble.getModifier());
  }

  @Test
  void otherRollsAddTheModifier() {
    D20Roll good = new D20Roll(15, 3);
    assertEquals(18, good.getTotal());
    assertTrue(good.meets(18));
    assertFalse(good.meets(19));
    assertEquals("§e15 §7(+3) = §e18", good.describe());
    assertEquals("§e12 §7(-4) = §e8", new D20Roll(12, -4).describe());
  }

  @Test
  void rollsReadTheBaseAttributeThroughMmoCore() {
    var server = MockBukkit.mock();
    Player player = server.addPlayer();
    D20Roll.die = () -> 12;
    assertNull(D20Roll.readBase(null, "strength"));
    assertNull(D20Roll.readBase(player, null));
    assertNull(D20Roll.readBase(player, " "));
    assertNull(D20Roll.readBase(player, "strength"));
    D20Roll none = D20Roll.roll(player);
    assertEquals(12, none.getNatural());
    assertEquals(0, none.getModifier());
    MockBukkit.createMockPlugin("MMOCore");
    try (var data = mockStatic(PlayerData.class)) {
      data.when(() -> PlayerData.get(player)).thenThrow(new IllegalStateException("unavailable"));
      assertNull(D20Roll.readBase(player, "strength"));
      assertEquals(0, D20Roll.modifierFor(player, "strength"));
      PlayerData record = mock(PlayerData.class, RETURNS_DEEP_STUBS);
      when(record.getAttributes().getInstance("strength").getBase()).thenReturn(15);
      data.when(() -> PlayerData.get(player)).thenReturn(record);
      assertEquals(15, D20Roll.readBase(player, "strength"));
      assertEquals(2, D20Roll.roll(player).getModifier());
    }
  }

  @Test
  void configOverridesDefaultsAndForcedNaturalPinsTheDie() throws Exception {
    D20Roll.load(
        config(
            "attribute-modifiers:\n  0: -2\n  10: 1\n  x: 9\n"
                + "force-natural: 7\nattribute: dexterity\nclue-dc: 9\n"));
    assertEquals(-2, D20Roll.modifier(9));
    assertEquals(1, D20Roll.modifier(20));
    assertEquals("dexterity", D20Roll.attribute);
    assertEquals(9, D20Roll.clueDc);
    assertEquals(7, D20Roll.die.getAsInt());
    // Out-of-range forced values and a table with no numbers fall back to real rolls and the default table.
    D20Roll.load(config("attribute-modifiers:\n  x: 9\nforce-natural: 25\n"));
    assertEquals(5, D20Roll.modifier(20));
    assertEquals("strength", D20Roll.attribute);
    assertEquals(12, D20Roll.clueDc);
    for (int i = 0; i < 200; i++) {
      int natural = D20Roll.die.getAsInt();
      assertTrue(natural >= 1 && natural <= 20, String.valueOf(natural));
    }
    D20Roll.load(config("{}"));
    int natural = D20Roll.die.getAsInt();
    assertTrue(natural >= 1 && natural <= 20);
  }
}
