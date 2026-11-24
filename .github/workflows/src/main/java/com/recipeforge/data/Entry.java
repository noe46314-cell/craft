package com.recipeforge.data;

import java.util.Arrays;

/** Одна правка: рецепт верстака, печи, станции или удаление. Предметы хранятся как "modid:name", "modid:name:meta", "modid:name:*" или "ore:name". */
public class Entry {
    public String id = "";
    public String type = "shaped";   // shaped | shapeless | furnace | station | remove
    public String station = "";
    public String tpl = "";
    public String out = "";
    public int count = 1;
    public String[] in = new String[9];
    public String param = "";
    public String replace = "";      // имя заменяемого рецепта, например "minecraft:stick"

    public Entry() {
        Arrays.fill(in, "");
    }

    public void fix() {
        String[] n = new String[9];
        for (int i = 0; i < 9; i++) n[i] = (in != null && i < in.length && in[i] != null) ? in[i].trim() : "";
        in = n;
        if (id == null) id = "";
        if (type == null) type = "shaped";
        if (station == null) station = "";
        if (tpl == null) tpl = "";
        if (out == null) out = "";
        out = out.trim();
        if (param == null) param = "";
        param = param.trim();
        if (replace == null) replace = "";
        replace = replace.trim();
        if (count < 1) count = 1;
        if (count > 64) count = 64;
    }
}
