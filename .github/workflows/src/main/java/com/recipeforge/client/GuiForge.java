package com.recipeforge.client;

import com.recipeforge.RecipeForge;
import com.recipeforge.data.Entry;
import com.recipeforge.data.Json;
import com.recipeforge.data.ListMsg;
import com.recipeforge.data.StationDef;
import com.recipeforge.net.PacketToServer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Редактор рецептов. Слева палитра всех предметов (или список «Мои правки»), справа сетка как у верстака.
 * Предмет можно взять кликом или перетащить мышью, ПКМ очищает слот.
 */
public class GuiForge extends GuiScreen {
    private static final ItemStack E = ItemStack.EMPTY;

    private boolean can;
    private List<Entry> entries = new ArrayList<Entry>();
    private List<StationDef> stations = new ArrayList<StationDef>();
    private final List<String[]> kinds = new ArrayList<String[]>(); // {тип, имя станции, подпись}
    private int kind = 0;

    private final ItemStack[] slots = new ItemStack[10]; // 0..8 вход, 9 результат
    private int count = 1;
    private String replace = "";
    private String editId = "";
    private String status = "";
    private String paramText = "";
    private String searchText = "";

    private int mode = 0;   // 0 палитра, 1 мои правки
    private int scroll = 0;
    private List<ItemStack> shown = new ArrayList<ItemStack>();

    private ItemStack held = E;
    private boolean heldFromSlot = false;
    private long pickMs = 0;

    private final List<IRecipe> found = new ArrayList<IRecipe>();
    private String foundFor = "";
    private int foundIdx = -1;

    private GuiTextField search, param;
    private final int palX = 8, palY = 46, cols = 9;
    private int rows, edX, gx, gy, ox, oy;

    public GuiForge(ListMsg m) {
        Arrays.fill(slots, E);
        setData(m);
    }

    /** Обновить данные от сервера, не сбрасывая редактор. */
    public void setData(ListMsg m) {
        can = m.can;
        entries = m.entries;
        stations = m.stations;
        String[] cur = kinds.isEmpty() ? null : kinds.get(kind);
        kinds.clear();
        kinds.add(new String[]{"shaped", "", "Верстак по форме"});
        kinds.add(new String[]{"shapeless", "", "Верстак без формы"});
        kinds.add(new String[]{"furnace", "", "Печь"});
        for (StationDef s : stations) kinds.add(new String[]{"station", s.name, "Станция: " + s.name});
        kinds.add(new String[]{"remove", "", "Удалить рецепт"});
        kind = 0;
        if (cur != null) {
            for (int i = 0; i < kinds.size(); i++) {
                if (kinds.get(i)[0].equals(cur[0]) && kinds.get(i)[1].equals(cur[1])) kind = i;
            }
        }
        if (m.savedId != null && !m.savedId.isEmpty()) editId = m.savedId;
        if (m.msg != null && !m.msg.isEmpty()) status = m.msg;
        else if (!can) status = "Нет прав на сохранение: нужен оператор (в одиночной игре — включённые читы).";
        refreshButtons();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        ItemCache.ensure();
        rows = Math.max(3, (height - palY - 10) / 18);
        edX = palX + cols * 18 + 26;
        gx = edX + 12;
        gy = 44;
        ox = gx + 3 * 20 + 34;
        oy = gy + 20;

        buttonList.add(new GuiButton(10, palX, 2, 78, 20, "Предметы"));
        buttonList.add(new GuiButton(11, palX + 80, 2, 82, 20, "Мои правки"));
        buttonList.add(new GuiButton(0, edX, 6, 220, 20, ""));
        buttonList.add(new GuiButton(1, ox - 12, oy + 22, 20, 20, "-"));
        buttonList.add(new GuiButton(2, ox + 10, oy + 22, 20, 20, "+"));
        buttonList.add(new GuiButton(3, edX, gy + 86, 220, 20, "Найти текущий рецепт результата"));
        buttonList.add(new GuiButton(4, edX, gy + 110, 108, 20, "Сохранить"));
        buttonList.add(new GuiButton(5, edX + 112, gy + 110, 108, 20, "Очистить"));
        buttonList.add(new GuiButton(6, edX, gy + 134, 220, 20, "Закрыть"));

        search = new GuiTextField(20, fontRenderer, palX, 26, cols * 18, 14);
        search.setText(searchText);
        search.setMaxStringLength(60);
        param = new GuiTextField(21, fontRenderer, edX + 70, gy + 64, 100, 14);
        param.setText(paramText);
        param.setMaxStringLength(20);
        refilter();
        refreshButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // иначе в одиночной игре сервер не получит пакет сохранения
    }

    // ---------- состояние ----------

    private String type() { return kinds.get(kind)[0]; }

    private StationDef stationDef(String name) {
        for (StationDef s : stations) if (s.name.equals(name)) return s;
        return null;
    }

    private int inCount() {
        String t = type();
        if (t.equals("shaped") || t.equals("shapeless")) return 9;
        if (t.equals("furnace")) return 1;
        if (t.equals("station")) {
            StationDef d = stationDef(kinds.get(kind)[1]);
            return d == null ? 1 : Math.min(9, Math.max(1, d.inputs));
        }
        return 0;
    }

    private boolean isCraft() {
        String t = type();
        return t.equals("shaped") || t.equals("shapeless");
    }

    private void refreshButtons() {
        for (GuiButton b : buttonList) if (b.id == 0) b.displayString = "Тип: " + kinds.get(kind)[2];
    }

    private void applyKind() {
        String[] k = kinds.get(kind);
        if (k[0].equals("furnace")) paramText = "0.5";
        else if (k[0].equals("station")) {
            StationDef d = stationDef(k[1]);
            paramText = d == null ? "" : d.param;
        } else paramText = "";
        if (param != null) param.setText(paramText);
        refreshButtons();
    }

    private void refilter() {
        shown = ItemCache.filter(searchText);
        scroll = 0;
    }

    private void clearAll() {
        Arrays.fill(slots, E);
        count = 1;
        replace = "";
        editId = "";
        found.clear();
        foundFor = "";
        foundIdx = -1;
        status = "";
    }

    private Entry build() {
        Entry e = new Entry();
        String[] k = kinds.get(kind);
        e.type = k[0];
        e.station = k[0].equals("station") ? k[1] : "";
        e.id = editId;
        e.out = ItemUtil.toStr(slots[9]);
        e.count = count;
        int n = inCount();
        for (int i = 0; i < 9; i++) e.in[i] = i < n ? ItemUtil.toStr(slots[i]) : "";
        e.param = param.getText().trim();
        e.replace = replace;
        return e;
    }

    private void loadEntry(Entry e) {
        int idx = -1;
        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.get(i)[0].equals(e.type) && (!e.type.equals("station") || kinds.get(i)[1].equals(e.station))) idx = i;
        }
        if (idx < 0) { status = "Станция из этой правки не найдена в recipeforge_stations.json"; return; }
        kind = idx;
        Arrays.fill(slots, E);
        for (int i = 0; i < 9; i++) slots[i] = ItemUtil.fromStr(e.in[i]);
        slots[9] = ItemUtil.fromStr(e.out);
        count = e.count;
        replace = e.replace;
        editId = e.id;
        paramText = e.param;
        param.setText(paramText);
        status = "Правка загружена. «Сохранить» обновит её.";
        refreshButtons();
    }

    private void save() {
        if (!can) { status = "Нет прав: нужен оператор (в одиночной игре — читы)."; return; }
        Entry e = build();
        String t = e.type;
        if (t.equals("remove")) {
            if (e.out.isEmpty() && e.replace.isEmpty()) { status = "Положи в слот результата предмет, рецепт которого удалить."; return; }
        } else {
            if (e.out.isEmpty()) { status = "Положи предмет в слот результата."; return; }
            boolean any = false;
            for (String s : e.in) if (!s.isEmpty()) any = true;
            if (!any) { status = "Нужен хотя бы один ингредиент."; return; }
        }
        if (t.equals("station")) {
            StationDef d = stationDef(e.station);
            if (d != null && d.template.contains("{p}") && e.param.isEmpty()) { status = "Укажи параметр (энергия, время и т.п.)."; return; }
        }
        RecipeForge.NET.sendToServer(new PacketToServer("save", Json.GSON.toJson(e)));
        status = "Отправлено на сервер…";
    }

    private void findRecipe() {
        ItemStack out = slots[9];
        if (out.isEmpty()) { status = "Положи в слот результата предмет, рецепт которого нужен."; return; }
        String key = ItemUtil.toStr(out);
        if (!key.equals(foundFor)) {
            found.clear();
            for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
                ItemStack ro = r.getRecipeOutput();
                if (ro.isEmpty() || ro.getItem() != out.getItem()) continue;
                if (out.getMetadata() != 32767 && ro.getMetadata() != out.getMetadata()) continue;
                found.add(r);
            }
            foundFor = key;
            foundIdx = -1;
        }
        if (found.isEmpty()) { status = "Рецептов верстака для этого предмета не найдено."; return; }
        foundIdx = (foundIdx + 1) % found.size();
        IRecipe r = found.get(foundIdx);
        kind = 0;
        if (!(r instanceof IShapedRecipe)) kind = 1;
        Arrays.fill(slots, 0, 9, E);
        boolean groups = false;
        List<Ingredient> ing = r.getIngredients();
        int w = 3;
        if (r instanceof IShapedRecipe) w = Math.max(1, ((IShapedRecipe) r).getRecipeWidth());
        for (int i = 0; i < ing.size() && i < 9; i++) {
            ItemStack[] ms = ing.get(i).getMatchingStacks();
            if (ms.length == 0) continue;
            if (ms.length > 1) groups = true;
            ItemStack s = ms[0].copy();
            s.setCount(1);
            int pos = i;
            if (r instanceof IShapedRecipe) pos = (i / w) * 3 + (i % w);
            if (pos < 9) slots[pos] = s;
        }
        ItemStack o = r.getRecipeOutput().copy();
        count = Math.max(1, o.getCount());
        o.setCount(1);
        slots[9] = o;
        replace = r.getRegistryName() == null ? "" : r.getRegistryName().toString();
        editId = "";
        paramText = "";
        param.setText("");
        refreshButtons();
        status = "Рецепт " + replace + " (" + (foundIdx + 1) + "/" + found.size() + "). Сохранение заменит его."
                + (groups ? " В ячейках с группой предметов выбран первый вариант." : "");
    }

    // ---------- геометрия ----------

    private int[] slotPos(int idx) {
        if (idx == 9) return new int[]{ox, oy};
        if (isCraft()) return new int[]{gx + (idx % 3) * 20, gy + (idx / 3) * 20};
        return new int[]{gx + idx * 20, gy + 20};
    }

    private int hitSlot(int mx, int my) {
        int n = inCount();
        for (int i = 0; i <= n; i++) {
            int idx = i < n ? i : 9;
            int[] p = slotPos(idx);
            if (mx >= p[0] - 1 && mx < p[0] + 17 && my >= p[1] - 1 && my < p[1] + 17) return idx;
        }
        return -1;
    }

    private int hitPalette(int mx, int my) {
        if (mode != 0) return -1;
        if (mx < palX || mx >= palX + cols * 18 || my < palY || my >= palY + rows * 18) return -1;
        int i = ((my - palY) / 18) * cols + (mx - palX) / 18 + scroll * cols;
        return i < shown.size() ? i : -1;
    }

    private int hitEntryRow(int mx, int my) {
        if (mode != 1) return -1;
        if (mx < palX || mx >= palX + cols * 18 || my < palY || my >= palY + rows * 18) return -1;
        int i = (my - palY) / 18 + scroll;
        return i < entries.size() ? i : -1;
    }

    private void put(int idx, ItemStack s) {
        ItemStack c = s.copy();
        c.setCount(1);
        slots[idx] = c;
    }

    // ---------- ввод ----------

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        super.mouseClicked(mx, my, btn);
        search.mouseClicked(mx, my, btn);
        param.mouseClicked(mx, my, btn);
        int s = hitSlot(mx, my);
        if (btn == 0) {
            if (s >= 0) {
                if (held.isEmpty()) {
                    if (!slots[s].isEmpty()) {
                        held = slots[s].copy();
                        heldFromSlot = true;
                        pickMs = System.currentTimeMillis();
                        slots[s] = E;
                    }
                } else {
                    ItemStack prev = slots[s];
                    put(s, held);
                    if (heldFromSlot) {
                        if (prev.isEmpty()) { held = E; heldFromSlot = false; }
                        else held = prev.copy();
                    }
                }
                return;
            }
            int p = hitPalette(mx, my);
            if (p >= 0) {
                held = shown.get(p).copy();
                held.setCount(1);
                heldFromSlot = false;
                pickMs = System.currentTimeMillis();
                return;
            }
            int r = hitEntryRow(mx, my);
            if (r >= 0) {
                Entry e = entries.get(r);
                if (mx >= palX + cols * 18 - 14) {
                    if (can) {
                        RecipeForge.NET.sendToServer(new PacketToServer("delete", e.id));
                        status = "Удаляю…";
                    } else status = "Нет прав: нужен оператор.";
                } else loadEntry(e);
            }
        } else if (btn == 1) {
            if (s >= 0) slots[s] = E;
            else held = E;
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        super.mouseReleased(mx, my, state);
        if (state != 0 || held.isEmpty()) return;
        if (System.currentTimeMillis() - pickMs > 200) { // перетаскивание: отпустили над слотом — положить, иначе выбросить
            int s = hitSlot(mx, my);
            if (s >= 0) put(s, held);
            held = E;
            heldFromSlot = false;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) {
            int total = mode == 0 ? (shown.size() + cols - 1) / cols : entries.size();
            int per = mode == 0 ? rows : rows;
            int max = Math.max(0, total - per);
            scroll = Math.max(0, Math.min(max, scroll + (d > 0 ? -1 : 1)));
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (search.isFocused() && search.textboxKeyTyped(c, key)) {
            searchText = search.getText();
            refilter();
            return;
        }
        if (param.isFocused() && param.textboxKeyTyped(c, key)) {
            paramText = param.getText();
            return;
        }
        if (key == Keyboard.KEY_ESCAPE && !held.isEmpty()) { held = E; return; }
        super.keyTyped(c, key);
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
        param.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        switch (b.id) {
            case 0: kind = (kind + 1) % kinds.size(); applyKind(); break;
            case 1: count = Math.max(1, count - (isShiftKeyDown() ? 8 : 1)); break;
            case 2: count = Math.min(64, count + (isShiftKeyDown() ? 8 : 1)); break;
            case 3: findRecipe(); break;
            case 4: save(); break;
            case 5: clearAll(); break;
            case 6: mc.displayGuiScreen(null); break;
            case 10: mode = 0; scroll = 0; break;
            case 11: mode = 1; scroll = 0; break;
            default: break;
        }
    }

    // ---------- рисование ----------

    private void drawStack(ItemStack s, int x, int y) {
        if (s.isEmpty()) return;
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(s, x, y);
        itemRender.renderItemOverlayIntoGUI(fontRenderer, s, x, y, null);
        RenderHelper.disableStandardItemLighting();
    }

    private void drawSlotBox(int x, int y) {
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        drawRect(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    private String label(Entry e) {
        String o = e.out.isEmpty() ? "?" : e.out;
        switch (e.type) {
            case "shaped": return "Верстак: " + o + " x" + e.count;
            case "shapeless": return "Без формы: " + o + " x" + e.count;
            case "furnace": return "Печь: " + o;
            case "station": return e.station + ": " + o;
            default: return "Удалить: " + (e.replace.isEmpty() ? o : e.replace);
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawRect(palX - 5, palY - 5, palX + cols * 18 + 5, height - 4, 0xC0101010);
        drawRect(edX - 8, 2, edX + 228, height - 4, 0xC0101010);

        ItemStack hover = E;
        String hoverText = null;

        if (mode == 0) {
            int n = rows * cols;
            for (int i = 0; i < n; i++) {
                int idx = scroll * cols + i;
                if (idx >= shown.size()) break;
                int x = palX + (i % cols) * 18, y = palY + (i / cols) * 18;
                drawRect(x, y, x + 17, y + 17, 0xFF373737);
                drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
                if (mx >= x && mx < x + 18 && my >= y && my < y + 18) {
                    drawRect(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
                    hover = shown.get(idx);
                }
                drawStack(shown.get(idx), x + 1, y + 1);
            }
            if (shown.isEmpty()) fontRenderer.drawString("Ничего не найдено", palX, palY, 0xFFAAAAAA);
        } else {
            for (int i = 0; i < rows; i++) {
                int idx = scroll + i;
                if (idx >= entries.size()) break;
                int y = palY + i * 18;
                boolean on = entries.get(idx).id.equals(editId);
                drawRect(palX, y, palX + cols * 18, y + 17, on ? 0xFF3B6B2E : 0xFF555555);
                fontRenderer.drawString(fontRenderer.trimStringToWidth(label(entries.get(idx)), cols * 18 - 20), palX + 3, y + 5, 0xFFFFFFFF);
                fontRenderer.drawString("x", palX + cols * 18 - 10, y + 5, 0xFFFF6666);
            }
            if (entries.isEmpty()) fontRenderer.drawString("Правок пока нет", palX, palY, 0xFFAAAAAA);
        }

        // редактор
        int n = inCount();
        for (int i = 0; i < n; i++) {
            int[] p = slotPos(i);
            drawSlotBox(p[0], p[1]);
            drawStack(slots[i], p[0], p[1]);
            if (mx >= p[0] && mx < p[0] + 16 && my >= p[1] && my < p[1] + 16 && !slots[i].isEmpty()) hover = slots[i];
        }
        drawSlotBox(ox, oy);
        ItemStack outShow = slots[9].copy();
        if (!outShow.isEmpty()) outShow.setCount(count);
        drawStack(outShow, ox, oy);
        if (mx >= ox && mx < ox + 16 && my >= oy && my < oy + 16 && !slots[9].isEmpty()) hover = slots[9];
        if (!type().equals("remove")) fontRenderer.drawString("→", gx + 3 * 20 + 10, oy + 4, 0xFFFFFFFF);
        fontRenderer.drawString(type().equals("remove") ? "Что удалить" : "Результат", ox - 6, oy - 12, 0xFFDDDDDD);
        fontRenderer.drawString("x" + count, ox + 38, oy + 28, 0xFFFFFFFF);
        if (type().equals("furnace")) fontRenderer.drawString("Опыт:", edX, gy + 67, 0xFFDDDDDD);
        else if (type().equals("station")) fontRenderer.drawString("Параметр:", edX, gy + 67, 0xFFDDDDDD);
        if (!replace.isEmpty()) fontRenderer.drawString("Заменяет: " + fontRenderer.trimStringToWidth(replace, 150), edX, gy - 12, 0xFFFFDD66);

        fontRenderer.drawSplitString(status, edX, gy + 160, 220, 0xFFFFFFFF);
        fontRenderer.drawString("ЛКМ: взять/положить или перетащить. ПКМ: очистить.", palX, height - 14, 0xFFAAAAAA);

        search.drawTextBox();
        if (type().equals("furnace") || type().equals("station")) param.drawTextBox();
        super.drawScreen(mx, my, pt);

        if (held.isEmpty() && !hover.isEmpty()) {
            List<String> tip = new ArrayList<String>();
            try { tip.add(hover.getDisplayName()); } catch (Throwable t) { tip.add("?"); }
            tip.add(TextFormatting.GRAY + ItemUtil.toStr(hover));
            drawHoveringText(tip, mx, my);
        }
        if (!held.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 300);
            drawStack(held, mx - 8, my - 8);
            GlStateManager.popMatrix();
        }
    }
}
