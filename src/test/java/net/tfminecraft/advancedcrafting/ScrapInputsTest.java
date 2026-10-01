package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import net.tfminecraft.advancedcrafting.objects.data.ScrapProvenance;
import net.tfminecraft.advancedcrafting.utils.PDCKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class ScrapInputsTest extends CoverageSupport {
  @Test
  void inputsRoundTripQuantitiesAndLegacyScrapReturnsOnlyItsBase() {
    var scrap = new ItemStack(Material.IRON_NUGGET);
    assertTrue(ScrapProvenance.readInputs(null).isEmpty());
    assertTrue(ScrapProvenance.readInputs(org.mockito.Mockito.mock(ItemStack.class)).isEmpty());
    assertTrue(ScrapProvenance.readInputs(scrap).isEmpty());
    scrap.setItemMeta(scrap.getItemMeta());
    var meta = scrap.getItemMeta();
    meta.setDisplayName("Scrap");
    scrap.setItemMeta(meta);
    assertTrue(ScrapProvenance.readInputs(scrap).isEmpty());
    ScrapProvenance.applyTo(scrap, "IRON");
    assertEquals(Map.of("iron", 1), ScrapProvenance.readInputs(scrap));
    ScrapProvenance.applyInputs(scrap, Map.of("IRON", 2, "ruby", 3, "zero", 0));
    assertEquals(Map.of("iron", 2, "ruby", 3), ScrapProvenance.readInputs(scrap));
    meta = scrap.getItemMeta();
    var inputs = meta.getPersistentDataContainer().get(PDCKeys.scrapInputs(), PersistentDataType.TAG_CONTAINER);
    inputs.set(new NamespacedKey(plugin, "bad"), PersistentDataType.STRING, "bad");
    meta.getPersistentDataContainer().set(PDCKeys.scrapInputs(), PersistentDataType.TAG_CONTAINER, inputs);
    scrap.setItemMeta(meta);
    assertEquals(Map.of("iron", 2, "ruby", 3), ScrapProvenance.readInputs(scrap));
    meta = scrap.getItemMeta();
    meta.getPersistentDataContainer().remove(PDCKeys.scrapInputs());
    meta.getPersistentDataContainer().set(PDCKeys.scrapBase(), PersistentDataType.STRING, " ");
    scrap.setItemMeta(meta);
    assertTrue(ScrapProvenance.readInputs(scrap).isEmpty());
  }
}
