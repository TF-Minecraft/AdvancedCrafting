package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.advancedcrafting.database.*;
import net.tfminecraft.advancedcrafting.lifecycle.*;
import net.tfminecraft.advancedcrafting.objects.alloys.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.utils.*;
import org.junit.jupiter.api.Test;

class PersistenceCoverageTest extends CoverageSupport {
  @Test
  void revisionsRoundTripAndIncrementOnlyForChangedContent() throws Exception {
    var tracker = new RevisionTracker();
    tracker.flush();
    tracker.resolveIngredient("early", "x");
    tracker.flush();
    tracker.load(temp.toFile());
    tracker.flush();
    assertEquals(1, tracker.resolveIngredient("IRON", "first"));
    assertEquals(1, tracker.resolveIngredient("iron", "first"));
    assertEquals(2, tracker.resolveIngredient("iron", "changed"));
    assertEquals(1, tracker.resolveAlloy("BRONZE", "alloy"));
    assertEquals(1, tracker.resolveStatTemplate("SWORD", "template"));
    tracker.flush();
    tracker.flush();
    var other = new RevisionTracker();
    other.load(temp.toFile());
    assertEquals(2, other.resolveIngredient("iron", "changed"));
    assertEquals(1, other.resolveAlloy("bronze", "alloy"));
    assertEquals(1, other.resolveStatTemplate("sword", "template"));
    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        RevisionTracker.sha256("abc"));
    Files.writeString(
        temp.resolve("data/revisions.json"), "{\"ingredients\":{\"legacy\":{}},\"alloys\":[]}");
    other.load(temp.toFile());
    assertEquals(1, other.resolveIngredient("legacy", ""));
    Files.writeString(temp.resolve("data/revisions.json"), "invalid");
    other.load(temp.toFile());
    assertEquals(1, other.resolveAlloy("new", "new"));
    Files.delete(temp.resolve("data/revisions.json"));
    Files.createDirectory(temp.resolve("data/revisions.json"));
    other.flush();
    assertTrue(Files.isDirectory(temp.resolve("data/revisions.json")));
  }

  @Test
  void recipeStorePersistsCanonicalCombosFindsDuplicatesAndRenamesResults() throws Exception {
    File root = temp.resolve("recipes").toFile();
    var store = new AlloyRecipeStore(root);
    new AlloyRecipeStore(root);
    var a = new AlloyRecipe("IRON", List.of("copper"));
    var b = new AlloyRecipe("iron", List.of("zinc"));
    var c = new AlloyRecipe("iron", List.of());
    assertNull(store.getResultByCombo(null));
    assertNull(store.getResultByCombo(a.comboKey()));
    store.upsert(a, "BRONZE");
    store.upsert(b, "bronze");
    store.upsert(c, "scrap");
    assertEquals("bronze", store.getResultByCombo(a.comboKey()));
    assertEquals(Map.of("bronze", 2), store.findDuplicateResultsExcludingScrap());
    assertTrue(List.of(a, b).contains(store.getRecipeByResult("BRONZE")));
    assertNull(store.getRecipeByResult("unknown"));
    store.updateResultId("bronze", "BRASS");
    assertEquals("brass", store.getResultByCombo(a.comboKey()));
    assertEquals(
        Map.of(a.comboKey(), "brass", b.comboKey(), "brass", "iron", "scrap"),
        store.loadAllComboResults());
    store.deleteByCombo(a.comboKey());
    store.deleteByCombo(a.comboKey());
    store.deleteByCombo(null);
    assertNull(store.getResultByCombo(a.comboKey()));
    var station = mock(AlloyStation.class);
    assertNull(store.getResult(station));
    store.deleteByStation(station);
    var base = ingredient("iron", "base: true");
    when(station.getBaseItem()).thenReturn(base);
    when(station.getCatalysts()).thenReturn(new ArrayList<>());
    assertEquals("scrap", store.getResult(station));
    assertEquals(c, AlloyRecipe.fromStation(station));
    store.deleteByStation(station);
    assertNull(store.getResult(station));
    when(station.getCatalysts()).thenReturn(new ArrayList<>(List.of(ingredient("zinc", ""))));
    assertEquals(b, AlloyRecipe.fromStation(station));
  }

  @Test
  void recipeStoreReadsLegacyFilesAndIgnoresIncompleteOrUnreadableEntries() throws Exception {
    var root = temp.resolve("recipes");
    var store = new AlloyRecipeStore(root.toFile());
    Files.createDirectories(root.resolve("iron"));
    Files.writeString(root.resolve("iron/iron.idx"), "\nBRONZE\nignored\nunknown=value\n");
    Files.writeString(root.resolve("iron/iron__zinc.idx"), "BRONZE");
    Files.writeString(root.resolve("iron/iron__empty.idx"), "combo=iron|empty\n");
    Files.writeString(root.resolve("iron/iron__none.idx"), "combo=\nresult=brass");
    Files.createDirectory(root.resolve("broken.idx"));
    assertEquals("bronze", store.getResultByCombo("iron"));
    assertTrue(
        List.of(new AlloyRecipe("iron", List.of()), new AlloyRecipe("iron", List.of("zinc")))
            .contains(store.getRecipeByResult("bronze")));
    store.updateResultId("bronze", "steel");
    assertEquals("steel", store.getResultByCombo("iron|zinc"));
    assertEquals(2, store.findDuplicateResultsExcludingScrap().get("steel"));
    assertNotNull(store.getRecipeByResult("brass"));
    assertTrue(store.loadAllComboResults().containsKey("iron"));
    var removed = temp.resolve("removed");
    var missing = new AlloyRecipeStore(removed.toFile());
    Files.delete(removed);
    assertTrue(missing.loadAllComboResults().isEmpty());
    var blocked = temp.resolve("blocked");
    Files.writeString(blocked, "file");
    new AlloyRecipeStore(blocked.toFile()).upsert(new AlloyRecipe("x", List.of()), "bad");
  }

  @Test
  void malformedLegacyIndexNameCannotAbortLookup() throws Exception {
    var root = temp.resolve("recipes");
    var store = new AlloyRecipeStore(root.toFile());
    Files.createDirectories(root.resolve("longbasename"));
    Files.writeString(root.resolve("longbasename/x.idx"), "bronze");
    assertNull(store.getRecipeByResult("bronze"));
    assertTrue(store.loadAllComboResults().isEmpty());
  }

  @Test
  void playerForgeTrackerPersistsFirstDiscoveryAndLoadsLegacyData() throws Exception {
    var tracker = new PlayerAlloyForgeTracker(temp.toFile());
    new PlayerAlloyForgeTracker(temp.toFile());
    UUID id = UUID.randomUUID();
    assertFalse(tracker.recordForge(null, "x"));
    assertFalse(tracker.recordForge(id, null));
    assertFalse(tracker.recordForge(id, " "));
    assertTrue(tracker.recordForge(id, "BRONZE"));
    assertFalse(tracker.recordForge(id, "bronze"));
    assertTrue(tracker.recordForge(id, "steel"));
    assertTrue(Files.readString(temp.resolve("forged-alloys/" + id + ".json")).contains("bronze"));
    UUID legacy = UUID.randomUUID();
    Files.writeString(
        temp.resolve("forged-alloys/" + legacy + ".json"), "[null,\" \",\" BRONZE \"]");
    assertFalse(tracker.recordForge(legacy, "bronze"));
    UUID bad = UUID.randomUUID();
    Files.writeString(temp.resolve("forged-alloys/" + bad + ".json"), "bad");
    assertTrue(tracker.recordForge(bad, "x"));
    UUID obj = UUID.randomUUID();
    Files.writeString(temp.resolve("forged-alloys/" + obj + ".json"), "{}");
    assertTrue(tracker.recordForge(obj, "x"));
    UUID directory = UUID.randomUUID();
    Files.createDirectory(temp.resolve("forged-alloys/" + directory + ".json"));
    assertTrue(tracker.recordForge(directory, "x"));
  }
}
