package net.tfminecraft.AdvancedCrafting.Objects.Data;

public class PermissionNamespace {
	private final String id;
	private final String display;

	public PermissionNamespace(String id, String display) {
		this.id = id.toLowerCase();
		this.display = display;
	}

	public String getId() {
		return id;
	}

	public String getDisplay() {
		return display;
	}
}
