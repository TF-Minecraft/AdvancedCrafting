package net.tfminecraft.AdvancedCrafting.Objects.Alloys;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;

import net.tfminecraft.AdvancedCrafting.Cache.Cache;
import net.tfminecraft.AdvancedCrafting.Enums.StationFeedback;
import net.tfminecraft.AdvancedCrafting.Objects.Ingredients.Ingredient;

public class AlloyStation {
	private Location loc;
	private Ingredient baseItem;
	private List<Ingredient> catalysts = new ArrayList<>();
	
	public AlloyStation(Location loc) {
		this.loc = loc;
	}
	
	public Ingredient getBaseItem() {
		return baseItem;
	}

	public Location getLocation() {
		return loc;
	}
	private boolean hasIngredient(Ingredient i) {
		if(baseItem != null && baseItem.getId().equals(i.getId())) {
			return true;
		}
		for(Ingredient c : catalysts) {
			if(c.getId().equals(i.getId())) return true;
		}
		return false;
	}
	public StationFeedback addIngredient(Ingredient i) {
		if(baseItem == null && !i.getIngredientData().canBeBase()) return StationFeedback.WRONG_BASE;
		if(hasIngredient(i)) return StationFeedback.EXISTS;
		if(catalysts.size() == 4) return StationFeedback.CAPACITY;
		if(baseItem != null && !Cache.canCombine(baseItem.getIngredientData().getType(), i.getIngredientData().getType())) return StationFeedback.INCOMPATIBLE_TYPE;
		if(baseItem == null) {
			baseItem = i;
		} else {
			catalysts.add(i);
		}
		return StationFeedback.SUCCESS;
	}
	public int getElementAmount() {
		int c = 0;
		if(baseItem != null) {
			c = c+1;
		}
		c = c+catalysts.size();
		return c;
	}
	
	public void drop() {
		if(baseItem != null) {
			loc.getWorld().dropItem(loc, baseItem.build());
		}
		for(Ingredient c : catalysts) {
			loc.getWorld().dropItem(loc, c.build());
		}
	}
	public String getStatus() {
		String s = "§eBase: ";
		if(baseItem != null) {
			s=s+"§71/1 ";
		} else {
			s=s+"§70/1 ";
		}
		s = s+"§eCatalysts: §7"+catalysts.size()+"/4";
		return s;
	}
	public int getTotalValue() {
		int v = 0;
		if(baseItem != null) {
			v = v+baseItem.getIngredientData().getValue();
		}
		for(Ingredient c : catalysts) {
			v = v+c.getIngredientData().getValue();
		}
		return v;
	}
	public List<Ingredient> getIngredients() {
		List<Ingredient> list = new ArrayList<>();
		if(baseItem != null) {
			list.add(baseItem);
		}
		list.addAll(catalysts);
		return list;
	}

	public List<Ingredient> getCatalysts() {
		return catalysts;
	}
	
}
