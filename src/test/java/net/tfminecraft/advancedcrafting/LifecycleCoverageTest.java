package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.advancedcrafting.lifecycle.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class LifecycleCoverageTest extends CoverageSupport {
  @Test
  void eventPayloadsAndHandlerListsMatchTheCraftingOutcome() {
    var p = server.addPlayer();
    var id = p.getUniqueId();
    var a = new AlloyCraftedEvent(p, id, "bronze");
    assertSame(p, a.getPlayer());
    assertEquals(id, a.getPlayerUuid());
    assertEquals("bronze", a.getAlloyId());
    assertSame(AlloyCraftedEvent.getHandlerList(), a.getHandlers());
    var d = new AlloyDiscoveredEvent(p, id, "bronze");
    assertSame(p, d.getPlayer());
    assertEquals(id, d.getPlayerUuid());
    assertEquals("bronze", d.getAlloyId());
    assertSame(AlloyDiscoveredEvent.getHandlerList(), d.getHandlers());
    var h = new SmithingHitEvent(p, id, "hammer");
    assertSame(p, h.getPlayer());
    assertEquals(id, h.getPlayerUuid());
    assertEquals("hammer", h.getHitId());
    assertSame(SmithingHitEvent.getHandlerList(), h.getHandlers());
    var c = new ItemCraftedEvent(p, id, "sword", "weapons");
    assertSame(p, c.getPlayer());
    assertEquals(id, c.getPlayerUuid());
    assertEquals("sword", c.getRecipeId());
    assertEquals("weapons", c.getCategoryId());
    assertSame(ItemCraftedEvent.getHandlerList(), c.getHandlers());
  }

  @Test
  void lifecycleEmitsDiscoveryOnceAndRejectsMissingIdentifiers() {
    var p = server.addPlayer();
    CraftLifecycle.init(null);
    assertNull(CraftLifecycle.getTracker());
    CraftLifecycle.fireAlloyOutcome(p, "BRONZE");
    CraftLifecycle.fireAlloyCrafted(p, "BRONZE");
    var tracker = new PlayerAlloyForgeTracker(temp.toFile());
    CraftLifecycle.init(tracker);
    assertSame(tracker, CraftLifecycle.getTracker());
    CraftLifecycle.fireAlloyOutcome(p, "STEEL");
    CraftLifecycle.fireAlloyOutcome(p, "steel");
    CraftLifecycle.fireItemCrafted(p, "sword", null);
    CraftLifecycle.fireItemCrafted(p, "sword", "WEAPONS");
    CraftLifecycle.fireSmithingHit(p, "HAMMER");
    for (Player player : Arrays.asList(null, p))
      for (String id : Arrays.asList(null, " ")) {
        CraftLifecycle.fireAlloyOutcome(player, id);
        CraftLifecycle.fireAlloyCrafted(player, id);
        CraftLifecycle.fireItemCrafted(player, id, "weapons");
        CraftLifecycle.fireSmithingHit(player, id);
      }
    server.getPluginManager().assertEventFired(AlloyCraftedEvent.class);
    server.getPluginManager().assertEventFired(AlloyDiscoveredEvent.class);
    server.getPluginManager().assertEventFired(ItemCraftedEvent.class);
    server.getPluginManager().assertEventFired(SmithingHitEvent.class);
  }
}
