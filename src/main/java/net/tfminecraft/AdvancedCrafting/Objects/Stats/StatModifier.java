package net.tfminecraft.AdvancedCrafting.Objects.Stats;

public class StatModifier {
	private String type;
	private double value;
	
	public StatModifier(String s, double v) {
		type = s;
		value = v;
	}
	public StatModifier(String s) {
		type = s.split("\\(")[0];
		value = Double.parseDouble(s.split("\\(")[1].replace(")", ""));
	}

	public String getType() {
		return type;
	}

	public double getAmount() {
		return value;
	}
	public void setAmount(double a) {
		this.value = a;
	}
	
	public void modify(double d) {
		value = value+d;
		value = Math.round(value*100);
		value = value/100;
	}
	public StatModifier copy() {
		return new StatModifier(type, value);
	}
}
