package net.tfminecraft.AdvancedCrafting.Objects.Data;

import java.util.ArrayList;
import java.util.List;


import net.tfminecraft.AdvancedCrafting.Objects.Stats.StatModifier;

public class StatData {
	private List<StatModifier> modifiers = new ArrayList<>();
	
	public StatData(List<String> stats) {
		for(String s : stats) {
			modifiers.add(new StatModifier(s));
		}
	}
	public StatData() {
		
	}
	
	public boolean hasModifiers() {
		if(modifiers.size() > 0) return true;
		return false;
	}
	
	public List<StatModifier> getModifiers() {
		return modifiers;
	}

	public boolean hasModifier(StatModifier m) {
		for(StatModifier mod : modifiers) {
			if(mod.getType().equalsIgnoreCase(m.getType())) return true;
		}
		return false;
	}
	
	private void modify(StatModifier m) {
		for(StatModifier mod : modifiers) {
			if(mod.getType().equalsIgnoreCase(m.getType())) {
				mod.modify(m.getAmount());
			}
		}
	}
	
	public double getAmount(StatModifier m) {
		for(StatModifier mod : modifiers) {
			if(mod.getType().equalsIgnoreCase(m.getType())) return mod.getAmount();
		}
		return 0;
	}
	
	public void mergeFrom(StatData data) {
		for(StatModifier m : data.getModifiers()) {
			if(hasModifier(m)) {
				modify(m);
				continue;
			}
			addModifier(m);
		}
	}
	
	public void addModifier(StatModifier m) {
		if(hasModifier(m)) {
			modify(m);
		} else {
			modifiers.add(new StatModifier(m.getType(), m.getAmount()));
		}
	}
}
