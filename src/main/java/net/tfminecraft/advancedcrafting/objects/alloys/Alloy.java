package net.tfminecraft.advancedcrafting.objects.alloys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.WordUtils;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.enums.APIType;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.manager.ItemManager;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.type.NameData;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.advancedcrafting.AdvancedCrafting;
import net.tfminecraft.advancedcrafting.objects.crafting.hits.CraftingHit;
import net.tfminecraft.advancedcrafting.objects.data.AlloyData;
import net.tfminecraft.advancedcrafting.objects.data.AlloyRecipe;
import net.tfminecraft.advancedcrafting.objects.data.StatData;
import net.tfminecraft.advancedcrafting.utils.AcItemTags;
import net.tfminecraft.advancedcrafting.utils.IngredientLore;
import net.tfminecraft.advancedcrafting.utils.PDCKeys;
import net.tfminecraft.advancedcrafting.objects.ingredients.Ingredient;

public class Alloy {
	private String id;
	private String name;
	private AlloyData data;
	private int revision;
	
	public Alloy(String n, Ingredient base, StatData stats, HashMap<CraftingHit, Integer> hits, String xp, AlloyRecipe recipe) {
		name = n;
		id = (new String(name)).replace(" ", "_").toLowerCase();
		System.out.println(name);
		name = StringFormatter.formatHex("#"+base.getIngredientData().getScheme().getColourScheme().randomColour()+name);
		data = new AlloyData(base, stats, hits, xp);
		data.setRecipe(recipe);
	}
	
	public Alloy(String id, String name, AlloyData data) {
		this.id = id;
		this.name = name;
		this.data = data;
	}
	
	@SuppressWarnings("deprecation")
	public ItemStack build() {
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		ItemStack template = api.getCreator().getItemFromPath(data.getColourScheme().getItem());
		MMOItem mmo = new LiveMMOItem(NBTItem.get(template));
		StringData itemName = (StringData) mmo.getData(ItemStats.NAME);
		itemName.setString(name);
		mmo.replaceData(ItemStats.NAME, itemName);
		StatHistory hist = StatHistory.from(mmo, ItemStats.NAME);
		if (hist != null) {
            NameData og = (NameData) hist.getOriginalData();
            og.setString(name);
            mmo.setStatHistory(ItemStats.NAME, hist);
        }
		List<String> loreList = new ArrayList<>();
		IngredientLore.Block loreBlock = IngredientLore.applyAlloyLore(loreList, data.getType(), data.getTier(),
				data.getStatData());
		StringListData lore = new StringListData(loreList);
		mmo.setData(ItemStats.LORE, lore);
		ItemStack i = mmo.newBuilder().build();
		ItemMeta m = i.getItemMeta();
		m.addEnchant(Enchantment.UNBREAKING, 1, true);
		m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		m.setCustomModelData(data.getModel());
		m.getPersistentDataContainer().set(PDCKeys.alloyId(), PersistentDataType.STRING, id);
		AcItemTags.write(m, revision, loreBlock);
		i.setItemMeta(m);
		return i;
	}

	public void setId(String s) {
		id = s;
	}

	public void setName(String n) {
		name = n;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public AlloyData getData() {
		return data;
	}

	public int getRevision() {
		return revision;
	}

	public void setRevision(int revision) {
		this.revision = revision;
	}
	
	
}
