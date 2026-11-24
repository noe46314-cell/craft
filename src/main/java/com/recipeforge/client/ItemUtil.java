package com.recipeforge.client;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class ItemUtil {
    /** ItemStack -> "modid:name", "modid:name:meta" или "modid:name:*" */
    public static String toStr(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        ResourceLocation rl = s.getItem().getRegistryName();
        if (rl == null) return "";
        int m = s.getMetadata();
        return rl.toString() + (m == 0 ? "" : ":" + (m == 32767 ? "*" : String.valueOf(m)));
    }

    public static ItemStack fromStr(String s) {
        if (s == null || s.isEmpty() || s.startsWith("ore:")) return ItemStack.EMPTY;
        String[] p = s.split(":");
        if (p.length < 2) return ItemStack.EMPTY;
        Item it = Item.getByNameOrId(p[0] + ":" + p[1]);
        if (it == null) return ItemStack.EMPTY;
        int meta = 0;
        if (p.length > 2) {
            if (p[2].equals("*")) meta = 32767;
            else {
                try { meta = Integer.parseInt(p[2]); } catch (NumberFormatException e) { meta = 0; }
            }
        }
        return new ItemStack(it, 1, meta);
    }
}
