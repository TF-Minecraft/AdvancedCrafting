package net.tfminecraft.advancedcrafting.objects.alloys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import net.tfminecraft.advancedcrafting.objects.stats.StatModifier;

public class AlloyForgerTest {
	@Test
	public void everyConfiguredGemstoneUsesTheGemstoneRule() throws IOException {
		long gemstoneCount = 0;
		for(String line : Files.readAllLines(Path.of("src/main/resources/ingredients.yml"))) {
			String trimmed = line.trim();
			if(!trimmed.startsWith("path:")) continue;
			String path = trimmed.substring("path:".length()).trim();
			if(path.startsWith("m.gemstones.")) {
				assertTrue(AlloyForger.isGemstonePath(path));
				gemstoneCount++;
			} else {
				assertFalse(AlloyForger.isGemstonePath(path));
			}
		}
		assertEquals(40, gemstoneCount);
		assertFalse(AlloyForger.isGemstonePath(null));
		assertFalse(AlloyForger.isGemstonePath("m.gemstones_fake.ruby"));
	}

	@Test
	public void gemstoneChanceUsesTheConfiguredIngredientValue() {
		assertEquals(60.0, AlloyForger.gemstoneInheritanceChance(1), 0.0);
		assertEquals(65.0, AlloyForger.gemstoneInheritanceChance(2), 0.0);
		assertEquals(70.0, AlloyForger.gemstoneInheritanceChance(3), 0.0);
		assertEquals(75.0, AlloyForger.gemstoneInheritanceChance(4), 0.0);
		assertEquals(100.0, AlloyForger.gemstoneInheritanceChance(10), 0.0);
	}

	@Test
	public void gemstoneWithMatchingStatAddsItsOwnPositiveValue() {
		StatModifier steelArmor = new StatModifier("armor", 0.5);
		StatModifier jasperArmor = new StatModifier("armor", 0.05);
		StatModifier gemContribution = AlloyForger.modifierForMerge(jasperArmor, steelArmor, true);

		assertEquals(0.05, gemContribution.getAmount(), 0.0);
		assertNotSame(jasperArmor, gemContribution);
		assertEquals(0.5, steelArmor.getAmount(), 0.0);
		assertEquals(-0.45, AlloyForger.modifierForMerge(jasperArmor, steelArmor, false).getAmount(), 0.000001);
	}

	@Test
	public void gemstoneWithAnyOtherMatchingStatAlsoAddsItsOwnValue() {
		StatModifier baseHealth = new StatModifier("max_health", 2.0);
		StatModifier gemHealth = new StatModifier("max_health", 0.5);
		assertEquals(0.5, AlloyForger.modifierForMerge(gemHealth, baseHealth, true).getAmount(), 0.0);
		assertEquals(0.5, AlloyForger.modifierForMerge(gemHealth, null, true).getAmount(), 0.0);
	}
}
