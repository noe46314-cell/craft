package com.recipeforge.client;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Список всех предметов всех модов для палитры слева. Строится один раз при первом открытии. */
public class ItemCache {
    private static List<ItemStack> all;
    private static List<String> keys;
    private static List<String> mods;

    public static void ensure() {
        if (all != null) return;
        all = new ArrayList<ItemStack>();
        keys = new ArrayList<String>();
        mods = new ArrayList<String>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == null || item == Items.AIR || item.getRegistryName() == null) continue;
            NonNullList<ItemStack> sub = NonNullList.create();
            try {
                item.getSubItems(CreativeTabs.SEARCH, sub);
            } catch (Throwable t) {
                sub.clear();
            }
            if (sub.isEmpty()) sub.add(new ItemStack(item));
            for (ItemStack s : sub) {
                if (s == null || s.isEmpty()) continue;
                String name;
                try { name = s.getDisplayName(); } catch (Throwable t) { name = ""; }
                all.add(s);
                keys.add((name + " " + ItemUtil.toStr(s)).toLowerCase(Locale.ROOT));
                mods.add(item.getRegistryName().getResourceDomain().toLowerCase(Locale.ROOT));
            }
        }
    }

    /** Фильтр: слова ищутся в названии и ID, "@мод" оставляет только предметы этого мода. */
    public static List<ItemStack> filter(String query) {
        ensure();
        String mod = null;
        List<String> terms = new ArrayList<String>();
        for (String t : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (t.isEmpty()) continue;
            if (t.startsWith("@")) mod = t.substring(1);
            else terms.add(t);
        }
        List<ItemStack> res = new ArrayList<ItemStack>();
        for (int i = 0; i < all.size(); i++) {
            if (mod != null && !mods.get(i).contains(mod)) continue;
            boolean ok = true;
            for (String t : terms) if (!keys.get(i).contains(t)) { ok = false; break; }
            if (ok) res.add(all.get(i));
        }
        return res;
    }
}
