package net.tfminecraft.advancedcrafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.util.*;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.tfminecraft.advancedcrafting.cache.Cache;
import net.tfminecraft.advancedcrafting.loaders.*;
import net.tfminecraft.advancedcrafting.managers.*;
import net.tfminecraft.advancedcrafting.objects.alloys.Alloy;
import net.tfminecraft.advancedcrafting.objects.crafting.*;
import net.tfminecraft.advancedcrafting.objects.data.*;
import net.tfminecraft.advancedcrafting.objects.ingredients.IngredientType;
import net.tfminecraft.advancedcrafting.utils.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class EquipmentGiveCommandTest extends CoverageSupport {
    final CommandManager commands = new CommandManager();

    Command command() {
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("ac");
        return command;
    }

    boolean run(CommandSender sender, String... tail) {
        List<String> args = new ArrayList<>(List.of("give", "equipment"));
        args.addAll(Arrays.asList(tail));
        return commands.onCommand(sender, command(), "ac", args.toArray(String[]::new));
    }

    List<String> complete(CommandSender sender, String... args) {
        return commands.onTabComplete(sender, command(), "ac", args);
    }

    @Test
    void customPermissionIsRequiredForExecutionAndCompletionAndReloadsSafely() throws Exception {
        var config = temp.resolve("config.yml");
        Files.writeString(config, "give-equipment-permission: '  staff.equipment  '");
        new ConfigLoader().load(config.toFile());
        assertEquals("staff.equipment", Cache.giveEquipmentPermission);
        var staff = mock(CommandSender.class);
        assertTrue(run(staff));
        verify(staff).sendMessage(contains("No permission"));
        assertEquals(List.of(), complete(staff, "give", "equipment", ""));
        assertEquals(List.of(), complete(staff, "give", ""));
        when(staff.hasPermission("staff.equipment")).thenReturn(true);
        assertEquals(List.of("give"), complete(staff));
        assertEquals(List.of("give"), complete(staff, "g"));
        assertEquals(List.of("give"), complete(staff, (String) null));
        assertEquals(List.of(), complete(staff, "reload"));
        assertEquals(List.of(), complete(staff, "other", ""));
        assertEquals(List.of("equipment"), complete(staff, "give", ""));
        assertTrue(run(staff));
        verify(staff).sendMessage(contains("Usage"));
        assertTrue(commands.onCommand(staff, command(), "ac", new String[] {"reload"}));
        verify(staff, times(2)).sendMessage(contains("No permission"));
        Files.writeString(config, "give-equipment-permission: ' '");
        new ConfigLoader().load(config.toFile());
        assertEquals(AdminPermissions.PERMISSION, Cache.giveEquipmentPermission);
        Files.writeString(config, "{}");
        new ConfigLoader().load(config.toFile());
        assertEquals(AdminPermissions.PERMISSION, Cache.giveEquipmentPermission);
    }

    @Test
    void rejectsUsageTargetsQualityRecipesAndMaterialsBeforeBuilding() throws Exception {
        var p = server.addPlayer("Staff");
        p.setOp(true);
        var console = mock(CommandSender.class);
        when(console.hasPermission(AdminPermissions.PERMISSION)).thenReturn(true);
        run(console, "sword", "ingredient.iron");
        verify(console).sendMessage(contains("Specify an online player"));
        run(p, "sword", "ingredient.iron", "missing");
        assertTrue(p.nextMessage().contains("Player not found"));
        for (String quality : List.of("bad", "NaN", "Infinity", "-Infinity", "-1", "101")) {
            run(p, "sword", "ingredient.iron", "Staff", quality);
            assertTrue(p.nextMessage().contains("Quality must"));
        }
        run(p, "sword", "ingredient.iron");
        assertTrue(p.nextMessage().contains("Unknown recipe"));
        var r = recipe("recipe: ['metal.4', 'leather.2']");
        RecipeLoader.map.put("sword", r);
        ingredient("iron", "");
        for (String id : List.of("iron", "ingredient.missing", "alloy.missing")) {
            run(p, "sword", id);
            assertTrue(p.nextMessage().contains("incompatible"));
        }
        run(p, "sword", "ingredient.iron");
        assertTrue(p.nextMessage().contains("No configured ingredient"));
        run(p, "sword", "ingredient.iron", "Staff", "50", "extra");
        assertTrue(p.nextMessage().contains("Usage"));
        assertEquals(0, p.getInventory().all(Material.IRON_SWORD).size());
        RecipeLoader.map.put("no_main", recipe("main-type: wood\nrecipe: ['metal.1']"));
        assertEquals(List.of(), complete(p, "give", "equipment", "no_main", ""));
    }

    @Test
    void grantsSelfConsoleTargetsAlloysAndOverflowWithExactProvenance() throws Exception {
        var p = server.addPlayer("Staff");
        p.setOp(true);
        var target = server.addPlayer("Target");
        var iron = ingredient("iron", "tier: 2\nxp: smith(10)\nhits: ['strike.1']");
        TypeLoader.map.put("leather", new IngredientType("leather", yaml("name: Leather")));
        ingredient("z_leather", "type: leather\ntier: 1");
        ingredient("a_leather", "type: leather\ntier: 1");
        ingredient("expensive_leather", "type: leather\ntier: 2");
        var recipe = recipe("recipe: ['metal.4', 'leather.2']");
        RecipeLoader.map.put("sword", recipe);
        QualityLoader.map.put("fine", new Quality("fine", yaml("name: Fine\namount: 0\nvalue: 0")));
        var alloy = new Alloy("bronze", "Bronze", new AlloyData(iron,
                new StatData(new ArrayList<>()), new HashMap<>(), "smith(10)"));
        AlloyManager.addAlloy(alloy);
        var wrongAlloy = mock(Alloy.class, RETURNS_DEEP_STUBS);
        when(wrongAlloy.getId()).thenReturn("wrong");
        when(wrongAlloy.getData().getType().getId()).thenReturn("wood");
        AlloyManager.addAlloy(wrongAlloy);
        assertEquals(List.of("alloy.bronze", "ingredient.iron"),
                complete(p, "give", "equipment", "sword", ""));
        assertEquals(List.of("sword"), complete(p, "give", "equipment", "S"));
        assertEquals(List.of(), complete(p, "give", "equipment", "missing", ""));
        assertEquals(List.of("Target"), complete(p, "give", "equipment", "sword", "", "t"));
        assertEquals(List.of("100"), complete(p, "give", "equipment", "sword", "", "", "1"));
        assertEquals(List.of(), complete(p, "give", "equipment", "sword", "", "", "", ""));
        try (var nbt = mockStatic(NBTItem.class);
             var mmos = mockConstruction(LiveMMOItem.class,
                     withSettings().defaultAnswer(RETURNS_DEEP_STUBS), (mmo, ctx) -> {
                         when(mmo.getData(ItemStats.NAME)).thenReturn(new StringData("Old"));
                         when(mmo.computeStatHistory(ItemStats.NAME)).thenReturn(null);
                         when(mmo.newBuilder().build()).thenAnswer(i -> new ItemStack(Material.IRON_SWORD));
                     });
             var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
             var lifecycle = mockStatic(net.tfminecraft.advancedcrafting.lifecycle.CraftLifecycle.class)) {
            run(p, "SWORD", "INGREDIENT.IRON");
            var item = p.getInventory().getItem(0);
            assertEquals(Material.IRON_SWORD, item.getType());
            var provenance = CraftProvenance.readFrom(item);
            assertEquals("sword", provenance.getRecipeId());
            assertEquals("fine", provenance.getQualityId());
            assertEquals(Map.of("ingredient.iron", 4, "ingredient.a_leather", 2),
                    provenance.getInputs().stream().collect(java.util.stream.Collectors.toMap(
                            input -> input.getKind() + "." + input.getId(), CraftInput::getAmount)));
            assertTrue(target.getWorld().getEntitiesByClass(Item.class).isEmpty());
            var console = mock(CommandSender.class);
            when(console.hasPermission(AdminPermissions.PERMISSION)).thenReturn(true);
            run(console, "sword", "alloy.bronze", "Target", "0");
            assertEquals(Material.IRON_SWORD, target.getInventory().getItem(0).getType());
            assertTrue(CraftProvenance.readFrom(target.getInventory().getItem(0)).getInputs().stream()
                    .anyMatch(input -> input.getId().equals("bronze")));
            run(p, "sword", "ingredient.iron", "Target", "100");
            ItemStack[] full = new ItemStack[target.getInventory().getSize()];
            Arrays.fill(full, new ItemStack(Material.STONE, 64));
            target.getInventory().setContents(full);
            run(p, "sword", "alloy.bronze", "Target", "50");
            assertEquals(1, target.getWorld().getEntitiesByClass(Item.class).size());
            bukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never());
            lifecycle.verifyNoInteractions();
        }
    }

    @Test
    void buildingFailuresAreReportedWithoutGrantAndIncompleteBuilderRejects() throws Exception {
        var p = server.addPlayer();
        p.setOp(true);
        ingredient("iron", "");
        var r = recipe("recipe: ['metal.1']");
        RecipeLoader.map.put("sword", r);
        var station = new CraftingStation(p.getLocation());
        station.setRecipe(r);
        assertThrows(IllegalStateException.class, () -> station.buildCompletedItem(p, 100));
        p.nextMessage();
        try (var stations = mockConstruction(CraftingStation.class, (mock, ctx) -> {
            when(mock.buildCompletedItem(any(), anyDouble())).thenThrow(new IllegalStateException("Bad template"));
        })) {
            run(p, "sword", "ingredient.iron");
            assertTrue(p.nextMessage().contains("could not be built"));
            assertTrue(p.getInventory().isEmpty());
        }
    }
}
