package net.tfminecraft.advancedcrafting.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.CraftingHit;
import net.tfminecraft.tlibs.objects.utils.IntCounter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HitClueTest {

  static CraftingHit hit(String id, String name) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString("name: '" + name + "'\ntool: v.iron_axe\n");
    return new CraftingHit(id, c);
  }

  static IntCounter counter(int current, int needed) {
    IntCounter c = new IntCounter();
    c.setNeeded(needed);
    c.setCurrent(current);
    return c;
  }

  static String needed(String amount, String name) {
    return HitClue.PREFIX + "you sense it needed " + amount + " " + name + "§7 work.";
  }

  static String took(String amount, String name) {
    return HitClue.PREFIX + "you sense it took " + amount + " " + name + "§7 work.";
  }

  @Test
  void exactHitsGiveNoClue() throws Exception {
    assertNull(HitClue.clueFor(Map.of()));
    assertNull(HitClue.clueFor(Map.of(hit("hit", "Hit"), counter(5, 5))));
  }

  @Test
  void theClueIsAboutTheHitClosestToRight() throws Exception {
    Map<CraftingHit, IntCounter> hits = new LinkedHashMap<>();
    hits.put(hit("hit", "Hit"), counter(4, 5));
    hits.put(hit("small_hit", "Small Hit"), counter(0, 3));
    hits.put(hit("etch", "Etch"), counter(2, 2));
    // 4 of 5 Hits is one away; 0 of 3 Small Hits is three away; the exact Etch is not a candidate.
    assertEquals(needed("a little more", "Hit"), HitClue.clueFor(hits));
  }

  @Test
  void equalMissesPreferTheSmallerShareThenTheId() throws Exception {
    Map<CraftingHit, IntCounter> hits = new LinkedHashMap<>();
    hits.put(hit("small_hit", "Small Hit"), counter(1, 2));
    hits.put(hit("hit", "Hit"), counter(9, 10));
    // Both are one away, but one in ten is closer to right than one in two.
    assertEquals(needed("a little more", "Hit"), HitClue.clueFor(hits));
    hits.clear();
    hits.put(hit("small_hit", "Small Hit"), counter(3, 4));
    hits.put(hit("hit", "Hit"), counter(5, 4));
    assertEquals(took("a little too much", "Hit"), HitClue.clueFor(hits));
    // Still level: the hit id decides, so the same piece always gets the same clue.
    hits.clear();
    hits.put(hit("sew", "Sew"), counter(1, 0));
    hits.put(hit("etch", "Etch"), counter(1, 0));
    assertEquals(HitClue.PREFIX + "you sense it took Etch§7 work it didn't need.", HitClue.clueFor(hits));
  }

  @Test
  void theWordingFollowsHowFarOffTheHitWas() throws Exception {
    CraftingHit h = hit("hit", "Hit");
    assertEquals(needed("a little more", "Hit"), HitClue.clueFor(Map.of(h, counter(3, 4))));
    assertEquals(needed("a little more", "Hit"), HitClue.clueFor(Map.of(h, counter(1, 2))));
    assertEquals(needed("a little more", "Hit"), HitClue.clueFor(Map.of(h, counter(6, 8))));
    assertEquals(needed("more", "Hit"), HitClue.clueFor(Map.of(h, counter(2, 4))));
    assertEquals(needed("more", "Hit"), HitClue.clueFor(Map.of(h, counter(5, 8))));
    assertEquals(needed("much more", "Hit"), HitClue.clueFor(Map.of(h, counter(1, 5))));
    assertEquals(took("a little too much", "Hit"), HitClue.clueFor(Map.of(h, counter(5, 4))));
    assertEquals(took("a little too much", "Hit"), HitClue.clueFor(Map.of(h, counter(2, 1))));
    assertEquals(took("too much", "Hit"), HitClue.clueFor(Map.of(h, counter(7, 4))));
    assertEquals(took("far too much", "Hit"), HitClue.clueFor(Map.of(h, counter(8, 4))));
    // None done, or any use of a hit the piece did not need, say so outright.
    assertEquals(
        HitClue.PREFIX + "you sense it needed some Hit§7 work.",
        HitClue.clueFor(Map.of(h, counter(0, 1))));
    assertEquals(
        HitClue.PREFIX + "you sense it took Hit§7 work it didn't need.",
        HitClue.clueFor(Map.of(h, counter(1, 0))));
  }
}
