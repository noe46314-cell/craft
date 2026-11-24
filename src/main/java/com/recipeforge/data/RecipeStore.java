package com.recipeforge.data;

import com.recipeforge.RecipeForge;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Хранит правки в scripts/recipeforge.json и генерирует scripts/recipeforge.zs. Работает на стороне сервера. */
public class RecipeStore {
    private static File scriptsDir() {
        File d = new File(Loader.instance().getConfigDir().getParentFile(), "scripts");
        d.mkdirs();
        return d;
    }
    private static File jsonFile() { return new File(scriptsDir(), "recipeforge.json"); }
    private static File zsFile() { return new File(scriptsDir(), "recipeforge.zs"); }
    private static File stationsFile() { return new File(Loader.instance().getConfigDir(), "recipeforge_stations.json"); }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
    private static void write(File f, String s) throws IOException {
        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    public static synchronized List<Entry> load() {
        List<Entry> res = new ArrayList<Entry>();
        File f = jsonFile();
        if (!f.exists()) return res;
        try {
            Entry[] a = Json.GSON.fromJson(read(f), Entry[].class);
            if (a != null) for (Entry e : a) { if (e != null) { e.fix(); res.add(e); } }
        } catch (Exception ex) {
            RecipeForge.LOG.error("RecipeForge: не удалось прочитать recipeforge.json, делаю копию .bak", ex);
            try { Files.copy(f.toPath(), new File(f.getParentFile(), "recipeforge.json.bak").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
        }
        return res;
    }

    private static void saveAll(List<Entry> l) throws IOException {
        write(jsonFile(), Json.GSON.toJson(l));
        write(zsFile(), ZsGen.generate(l));
    }

    public static synchronized List<StationDef> stations() {
        File f = stationsFile();
        if (f.exists()) {
            try {
                StationDef[] a = Json.GSON.fromJson(read(f), StationDef[].class);
                if (a != null) {
                    List<StationDef> r = new ArrayList<StationDef>();
                    for (StationDef s : a) if (s != null && s.name != null && s.template != null) r.add(s);
                    return r;
                }
            } catch (Exception ex) {
                RecipeForge.LOG.error("RecipeForge: ошибка в recipeforge_stations.json", ex);
            }
        }
        List<StationDef> d = defaults();
        try { write(f, Json.GSON.toJson(d)); } catch (IOException ignored) {}
        return d;
    }

    private static List<StationDef> defaults() {
        List<StationDef> l = new ArrayList<StationDef>();
        l.add(new StationDef("Thermal: измельчитель", 1, "mods.thermalexpansion.Pulverizer.addRecipe({out}, {in1}, {p});", "2000"));
        l.add(new StationDef("Thermal: индукционная печь", 2, "mods.thermalexpansion.InductionSmelter.addRecipe({out}, {in1}, {in2}, {p});", "2000"));
        l.add(new StationDef("Mekanism: дробилка", 1, "mods.mekanism.crusher.addRecipe({in1}, {out});", ""));
        l.add(new StationDef("Mekanism: обогатитель", 1, "mods.mekanism.enrichment.addRecipe({in1}, {out});", ""));
        l.add(new StationDef("IC2: мацератор", 1, "mods.ic2.Macerator.addRecipe({out}, {in1});", ""));
        l.add(new StationDef("Immersive Engineering: дробилка", 1, "mods.immersiveengineering.Crusher.addRecipe({out}, {in1}, {p});", "2000"));
        return l;
    }

    /** Добавляет или обновляет правку, возвращает её id. */
    public static synchronized String upsert(Entry e) throws IOException {
        e.fix();
        String err = ZsGen.check(e);
        if (err != null) throw new IllegalArgumentException(err);
        if (e.type.equals("station")) {
            StationDef found = null;
            for (StationDef s : stations()) if (s.name.equals(e.station)) found = s;
            if (found == null) throw new IllegalArgumentException("станция не найдена в recipeforge_stations.json: " + e.station);
            e.tpl = found.template;
        } else {
            e.tpl = "";
            e.station = "";
        }
        List<Entry> l = load();
        if (e.id.isEmpty() || !e.id.matches("[a-z0-9]+")) e.id = Long.toString(System.currentTimeMillis(), 36);
        boolean replaced = false;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id.equals(e.id)) { l.set(i, e); replaced = true; break; }
        }
        if (!replaced) l.add(e);
        saveAll(l);
        return e.id;
    }

    public static synchronized void delete(String id) throws IOException {
        List<Entry> l = load();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(id)) l.remove(i);
        saveAll(l);
    }
}
