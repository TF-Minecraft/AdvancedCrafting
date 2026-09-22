package net.tfminecraft.AdvancedCrafting.Objects.Alloys;

import org.bukkit.inventory.ItemStack;

public class NamableAlloy {
    private int time;
    private Alloy alloy;
    private ItemStack item;

    public NamableAlloy(Alloy a, ItemStack i) {
        time = 0;
        alloy = a;
        item = i;
    }

    public int getTime() {
        return time;
    }

    public boolean tick() {
        time++;
        if(time >= 60) return true;
        return false;
    }

    public Alloy getAlloy() {
        return alloy;
    }

    public ItemStack getItem() {
        return item;
    }
}
