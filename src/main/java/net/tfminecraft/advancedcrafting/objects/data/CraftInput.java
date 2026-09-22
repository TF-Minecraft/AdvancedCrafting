package net.tfminecraft.advancedcrafting.objects.data;

public class CraftInput {
	private String k;
	private String id;
	private int n;
	private int r;

	public CraftInput() {
	}

	public CraftInput(String kind, String id, int amount, int revision) {
		this.k = kind;
		this.id = id;
		this.n = amount;
		this.r = revision;
	}

	public String getKind() {
		return k;
	}

	public String getId() {
		return id;
	}

	public int getAmount() {
		return n;
	}

	public int getRevision() {
		return r;
	}

	public void setRevision(int revision) {
		this.r = revision;
	}
}
