package net.tfminecraft.advancedcrafting.objects.crafting;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class Quality {
	private String id;
	private double amount;
	private int value;
	private String name;
	
	public Quality(String key, ConfigurationSection config) {
		id = key;
		amount = config.getDouble("amount");
		value = config.getInt("value");
		name = StringFormatter.formatHex(config.getString("name"));
	}

	public String getId() {
		return id;
	}

	public double getAmount() {
		return amount;
	}

	public int getValue() {
		return value;
	}

	public String getName() {
		return name;
	}

	public boolean isValid(double d) {
		if(d >= amount) return true;
		return false;
	}
}
