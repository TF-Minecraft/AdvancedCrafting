package net.tfminecraft.AdvancedCrafting.Objects.Stats;

public class MergeStatModifier {
	private String type;
	private double value;
	private int amount;

	public MergeStatModifier(String type) {
		this.type = type;
		this.value = 0;
		this.amount = 0;
	}

	public MergeStatModifier(StatModifier m) {
		type = m.getType();
		value = m.getAmount();
		amount = 1;
	}

	public int getAmount() {
		return amount;
	}

	public String getType() {
		return type;
	}

	public double getValue() {
		return value;
	}

	public void modify(double d) {
		this.value = this.value + d;
		amount++;
	}

	public void addWeighted(double perUnit, int units) {
		this.value += perUnit * units;
		this.amount += units;
	}

	public StatModifier create() {
		if (amount <= 0) {
			return new StatModifier(type, 0);
		}
		double avg = Math.round((value / amount) * 100.0) / 100.0;
		return new StatModifier(type, avg);
	}

	public StatModifier createWithDenominator(int totalUnitsInBucket) {
		if (totalUnitsInBucket <= 0) {
			return new StatModifier(type, 0);
		}
		double avg = Math.round((value / totalUnitsInBucket) * 100.0) / 100.0;
		return new StatModifier(type, avg);
	}
}
