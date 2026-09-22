package net.tfminecraft.AdvancedCrafting.Utils;

import java.util.HashMap;

import org.apache.commons.lang.WordUtils;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import net.tfminecraft.AdvancedCrafting.Objects.Stats.StatModifier;

public class StatToString {
    public static HashMap<String, String> map = new HashMap<>();


    public static void add(String key, String value) {
        map.put(key, value);
    }

    public static String get(String key) {
        if(map.containsKey(key)) return map.get(key);
        return WordUtils.capitalize(key).replace("_", " ");
    }

    public static String getFullString(StatModifier m) {
        String valuePart = m.getAmount() >= 0
                ? "#45c46f+" + m.getAmount()
                : "#d13530" + m.getAmount();
        String result = "§f- #b8ae61" + StatToString.get(m.getType()) + " " + valuePart;
        return StringFormatter.formatHex(result);
    }
}
