package com.recipeforge.data;

import java.util.List;
import java.util.regex.Pattern;

/** Превращает правки в скрипт CraftTweaker (ZenScript). Не зависит от Minecraft. */
public class ZsGen {
    private static final Pattern ITEM = Pattern.compile("^(?:[a-z0-9_.\\-]+:[A-Za-z0-9_.\\-/]+(?::(?:\\*|\\d+))?|ore:[A-Za-z0-9_.\\-]+)?$");
    private static final Pattern NAME = Pattern.compile("^(?:[a-z0-9_.\\-]+:[a-z0-9_./\\-]+)?$");
    private static final Pattern PARAM = Pattern.compile("^[A-Za-z0-9_.\\-]*$");

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    /** Возвращает текст ошибки или null, если правка корректна. */
    public static String check(Entry e) {
        e.fix();
        switch (e.type) {
            case "shaped": case "shapeless": case "furnace": case "station": case "remove": break;
            default: return "неизвестный тип рецепта: " + e.type;
        }
        if (!ITEM.matcher(e.out).matches()) return "неверный ID результата: " + e.out;
        for (String s : e.in) if (!ITEM.matcher(s).matches()) return "неверный ID ингредиента: " + s;
        if (!NAME.matcher(e.replace).matches()) return "неверное имя заменяемого рецепта";
        if (!PARAM.matcher(e.param).matches()) return "параметр: только буквы, цифры, точка и минус";
        return null;
    }

    private static String br(String s) {
        s = nz(s);
        return s.isEmpty() ? "null" : "<" + s + ">";
    }

    public static String generate(List<Entry> list) {
        StringBuilder rem = new StringBuilder();
        StringBuilder add = new StringBuilder();
        for (Entry e : list) {
            e.fix();
            String id = e.id.replaceAll("[^a-z0-9]", "");
            String name = "rf_" + id;
            String out = br(e.out) + (e.count > 1 ? " * " + e.count : "");
            boolean craft = e.type.equals("shaped") || e.type.equals("shapeless");
            boolean hasRepl = !e.replace.isEmpty();

            if (e.type.equals("remove") || (craft && hasRepl)) {
                if (hasRepl) rem.append("recipes.removeByRecipeName(\"").append(e.replace).append("\");\n");
                else if (!e.out.isEmpty()) rem.append("recipes.remove(").append(br(e.out)).append(");\n");
            }
            if (e.type.equals("remove")) continue;
            if (e.out.isEmpty()) { add.append("// ").append(name).append(": не указан результат\n"); continue; }

            if (e.type.equals("shaped")) {
                int y0 = 9, y1 = -1, x0 = 9, x1 = -1;
                for (int i = 0; i < 9; i++) {
                    if (e.in[i].isEmpty()) continue;
                    int y = i / 3, x = i % 3;
                    y0 = Math.min(y0, y); y1 = Math.max(y1, y);
                    x0 = Math.min(x0, x); x1 = Math.max(x1, x);
                }
                if (y1 < 0) { add.append("// ").append(name).append(": пустая сетка\n"); continue; }
                StringBuilder rows = new StringBuilder();
                for (int y = y0; y <= y1; y++) {
                    if (y > y0) rows.append(", ");
                    rows.append("[");
                    for (int x = x0; x <= x1; x++) {
                        if (x > x0) rows.append(", ");
                        rows.append(br(e.in[y * 3 + x]));
                    }
                    rows.append("]");
                }
                add.append("recipes.addShaped(\"").append(name).append("\", ").append(out).append(", [").append(rows).append("]);\n");
            } else if (e.type.equals("shapeless")) {
                StringBuilder ing = new StringBuilder();
                for (int i = 0; i < 9; i++) {
                    if (e.in[i].isEmpty()) continue;
                    if (ing.length() > 0) ing.append(", ");
                    ing.append(br(e.in[i]));
                }
                if (ing.length() == 0) { add.append("// ").append(name).append(": нет ингредиентов\n"); continue; }
                add.append("recipes.addShapeless(\"").append(name).append("\", ").append(out).append(", [").append(ing).append("]);\n");
            } else if (e.type.equals("furnace")) {
                String xp = e.param.isEmpty() ? "0.5" : e.param;
                add.append("furnace.addRecipe(").append(out).append(", ").append(br(e.in[0])).append(", ").append(xp).append(");\n");
            } else if (e.type.equals("station")) {
                String t = e.tpl.replaceAll("[\\r\\n]+", " ").replace("{out}", out).replace("{p}", e.param);
                for (int i = 8; i >= 0; i--) t = t.replace("{in" + (i + 1) + "}", br(e.in[i]));
                add.append(t).append("\n");
            }
        }
        return "// Создано RecipeForge (внутриигровой редактор рецептов).\n"
             + "// Файл можно переносить в другую сборку с теми же модами: нужен только CraftTweaker.\n\n"
             + rem + "\n" + add;
    }
}
