package com.retile.client;
import com.retile.brush.*;
import com.retile.client.config.KeyCombo;
import com.retile.client.config.PresetStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.*;
import org.lwjgl.glfw.GLFW;

public final class BrushScreen extends Screen {
    private final BrushSettings settings = RetileClient.settings;
    private final List<Block> catalog = new ArrayList<>(), matches = new ArrayList<>();
    private EditBox search, minY, maxY, seed, presetName;
    private Button savePreset, fromHand, undoKeyButton, redoKeyButton;
    private boolean replacementsTab, presetsTab;
    private int presetRow, capturingKey = -1;
    private int left, right, panelWidth, top, columns, gridTop, gridBottom, selectedTop, selectedBottom;
    private int gridRow, selectedRow, controlsScroll;
    private final List<AbstractWidget> controls = new ArrayList<>();
    private final Map<AbstractWidget, Integer> controlY = new IdentityHashMap<>();
    private record Hit(int x, int y, int w, int h, Runnable leftClick, Runnable rightClick) {
        boolean contains(double mx, double my) { return mx >= x && mx < x+w && my >= y && my < y+h; }
    }
    private final List<Hit> hits = new ArrayList<>();
    public BrushScreen() {
        super(text("title"));
        BuiltInRegistries.BLOCK.forEach(block -> { if (BrushSelection.selectable(block)) catalog.add(block); });
        catalog.sort(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
    }
    private static Component text(String key, Object... args) { return Component.translatable("retile.gui." + key, args); }
    @Override protected void init() {
        String query = search == null ? "" : search.getValue();
        String name = presetName == null ? "" : presetName.getValue();
        controls.clear(); controlY.clear();
        panelWidth = Math.max(100, (width - 30) / 2); left = 10; right = left + panelWidth + 10; top = 30;
        columns = Math.max(1, panelWidth / 22); gridTop = top + 54;
        gridBottom = Math.max(gridTop + 22, top + (height - top - 56) / 2);
        selectedTop = gridBottom + 20; selectedBottom = height - 40;
        int tabWidth = (panelWidth - 8) / 3;
        button(left, top, tabWidth, text("filters"), () -> showTab(false, false));
        button(left + tabWidth + 4, top, tabWidth, text("replacements"), () -> showTab(true, false));
        button(left + 2 * (tabWidth + 4), top, panelWidth - 2 * (tabWidth + 4), text("presets"), () -> showTab(true, true));
        search = addRenderableWidget(new EditBox(font, left, top + 26, panelWidth, 20, text("search")));
        search.setHint(text("search")); search.setMaxLength(128); search.setValue(query); search.setResponder(this::filter); filter(query);
        presetName = addRenderableWidget(new EditBox(font, left, top + 26, panelWidth * 2 / 3 - 4, 20, text("preset_name")));
        presetName.setMaxLength(PresetStore.MAX_NAME_LENGTH); presetName.setHint(text("preset_name")); presetName.setValue(name);
        presetName.setTooltip(Tooltip.create(text("preset_name_hint")));
        savePreset = button(left + panelWidth * 2 / 3, top + 26, panelWidth - panelWidth * 2 / 3, text("save"), this::savePreset);
        savePreset.setTooltip(Tooltip.create(text("preset_save_hint")));
        fromHand = button(left, height - 28, panelWidth, text("from_hand"), () -> {
            if (minecraft.player != null && minecraft.player.getMainHandItem().getItem() instanceof BlockItem item) add(item.getBlock());
        });
        int y = top;
        control(new ValueSlider(right, y, panelWidth, 1, BrushConstants.MAX_RADIUS, settings.radius,
                v -> settings.radius = (int) Math.round(v), v -> text("radius", (int) Math.round(v)), 1)); y += 26;
        toggle(y, "shape", () -> settings.shape == BrushShapeType.SPHERE, () -> settings.shape = settings.shape == BrushShapeType.SPHERE ? BrushShapeType.CYLINDER : BrushShapeType.SPHERE,
                () -> text(settings.shape == BrushShapeType.SPHERE ? "sphere" : "cylinder")); y += 26;
        y += checkbox(y, "include_air", settings.includeAir, value -> settings.includeAir = value);
        control(new ValueSlider(right, y, panelWidth, -1, 1, settings.chaos,
                v -> settings.chaos = BrushSettings.snapChaos((float) v), v -> text("chaos", String.format(Locale.ROOT, "%.2f", v)), .05)); y += 26;
        y += checkbox(y, "limit_height", settings.limitHeight, value -> settings.limitHeight = value);
        minY = field(right, y, panelWidth / 2 - 3, Integer.toString(settings.minY), "min_y", value -> { try { settings.minY = Integer.parseInt(value); } catch (NumberFormatException ignored) {} });
        maxY = field(right + panelWidth / 2 + 3, y, panelWidth / 2 - 3, Integer.toString(settings.maxY), "max_y", value -> { try { settings.maxY = Integer.parseInt(value); } catch (NumberFormatException ignored) {} }); y += 26;
        controlButton(y, "player_y", () -> { if (minecraft.player != null) setY(minecraft.player.blockPosition().getY()); }); y += 26;
        controlButton(y, "target_y", () -> { var pos = RetileClient.target(minecraft); if (pos != null) setY(pos.getY()); }); y += 26;
        y += checkbox(y, "fixed_seed", settings.fixedSeed, value -> settings.fixedSeed = value);
        seed = field(right, y, panelWidth, Long.toString(settings.seed), "seed", value -> { try { settings.seed = Long.parseLong(value); } catch (NumberFormatException ignored) {} }); y += 26;
        controlButton(y, "new_seed", () -> { settings.seed = ThreadLocalRandom.current().nextLong(); seed.setValue(Long.toString(settings.seed)); }); y += 26;
        undoKeyButton = historyControls(y, false); y += 26;
        redoKeyButton = historyControls(y, true);
        updateKeyButtons(); updatePaletteWidgets();
        positionControls();
    }
    private void showTab(boolean replacements, boolean presets) {
        replacementsTab = replacements; presetsTab = presets; selectedRow = 0;
        updatePaletteWidgets();
    }
    private void updatePaletteWidgets() {
        search.visible = !presetsTab; fromHand.visible = !presetsTab;
        presetName.visible = presetsTab; savePreset.visible = presetsTab;
        savePreset.active = !presetName.getValue().isBlank() && !settings.replacements.isEmpty();
        if (getFocused() instanceof AbstractWidget widget && !widget.visible) setFocused(null);
    }
    private Button historyControls(int y, boolean redo) {
        Button action = Button.builder(text(redo ? "redo" : "undo"), ignored -> RetileClient.sendHistory(redo))
                .bounds(right, y, panelWidth / 2 - 3, 20).build();
        control(action);
        Button binding = Button.builder(Component.empty(), ignored -> {
            capturingKey = redo ? 1 : 0; setFocused(null); updateKeyButtons();
        }).bounds(right + panelWidth / 2 + 3, y, panelWidth - panelWidth / 2 - 3, 20).build();
        control(binding); return binding;
    }
    private void updateKeyButtons() {
        if (undoKeyButton == null || redoKeyButton == null) return;
        undoKeyButton.setMessage(capturingKey == 0 ? text("key_capture") : BrushHotkeys.label(BrushConfig.undoKey));
        redoKeyButton.setMessage(capturingKey == 1 ? text("key_capture") : BrushHotkeys.label(BrushConfig.redoKey));
        undoKeyButton.setTooltip(Tooltip.create(text("key_bind_hint", text("undo"), BrushHotkeys.label(BrushConfig.undoKey))));
        redoKeyButton.setTooltip(Tooltip.create(text("key_bind_hint", text("redo"), BrushHotkeys.label(BrushConfig.redoKey))));
    }
    private void savePreset() {
        PresetStore.SaveResult result = BrushConfig.presets.save(presetName.getValue(), settings.replacements.stream()
                .map(entry -> new PresetStore.Entry(entry.id().toString(), entry.weight())).toList());
        if (result == PresetStore.SaveResult.SAVED) BrushConfig.save(settings);
        else RetileClient.message(switch (result) {
            case EMPTY_NAME -> "preset_empty_name";
            case NAME_TOO_LONG -> "preset_name_long";
            case EMPTY_PALETTE -> "preset_empty_palette";
            case LIMIT_REACHED -> "preset_limit";
            default -> "list_full";
        });
    }
    private void loadPreset(PresetStore.Preset preset) {
        var replacements = BrushConfig.resolvePreset(preset);
        if (replacements.isEmpty()) { RetileClient.message("preset_unavailable"); return; }
        settings.replacements.clear(); settings.replacements.addAll(replacements);
        presetName.setValue(preset.name()); showTab(true, false);
    }
    private void removePreset(String name) {
        if (BrushConfig.presets.remove(name)) BrushConfig.save(settings);
    }
    private void filter(String query) {
        matches.clear(); String q = query.toLowerCase(Locale.ROOT);
        for (Block block : catalog) if (block.getName().getString().toLowerCase(Locale.ROOT).contains(q)
                || BuiltInRegistries.BLOCK.getKey(block).toString().contains(q)) matches.add(block);
        gridRow = 0;
    }
    private Button button(int x, int y, int w, Component label, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, w, 20).build());
    }
    private void control(AbstractWidget widget) { addRenderableWidget(widget); controls.add(widget); controlY.put(widget, widget.getY()); }
    private void controlButton(int y, String key, Runnable action) {
        Button b = Button.builder(text(key), ignored -> action.run()).bounds(right, y, panelWidth, 20).build(); control(b);
    }
    private int checkbox(int y, String key, boolean value, Consumer<Boolean> changed) {
        Checkbox box = Checkbox.builder(text(key), font).pos(right, y).maxWidth(panelWidth)
                .selected(value).onValueChange((widget, selected) -> changed.accept(selected)).build();
        control(box);
        return Math.max(20, box.getHeight()) + 6;
    }
    private void toggle(int y, String key, BooleanSupplier value, Runnable change, Supplier<Component> custom) {
        Supplier<Component> label = () -> text("setting", text(key), custom == null ? text(value.getAsBoolean() ? "on" : "off") : custom.get());
        Button b = Button.builder(label.get(), clicked -> { change.run(); clicked.setMessage(label.get()); }).bounds(right, y, panelWidth, 20).build(); control(b);
    }
    private EditBox field(int x, int y, int w, String value, String key, Consumer<String> changed) {
        EditBox box = new EditBox(font, x, y, w, 20, text(key)); box.setMaxLength(20); box.setValue(value); box.setResponder(changed);
        box.setTooltip(Tooltip.create(text(key))); control(box); return box;
    }
    private void setY(int value) { minY.setValue(Integer.toString(value)); maxY.setValue(Integer.toString(value)); }
    private void add(Block block) {
        if (!BrushSelection.selectable(block)) return;
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (replacementsTab) {
            if (settings.replacements.stream().anyMatch(e -> e.id().equals(id))) return;
            if (settings.replacements.size() >= BrushConstants.MAX_REPLACEMENTS) { RetileClient.message("list_full"); return; }
            settings.replacements.add(new BrushSettings.WeightedEntry(id, 1));
        } else {
            if (settings.filters.contains(id)) return;
            if (settings.filters.size() >= BrushConstants.MAX_FILTER) { RetileClient.message("list_full"); return; }
            settings.filters.add(id);
        }
    }
    private void positionControls() {
        int bottom = controls.stream().mapToInt(w -> controlY.get(w) + w.getHeight()).max().orElse(top);
        controlsScroll = Math.clamp(controlsScroll, 0, Math.max(0, bottom - (height - 10)));
        for (AbstractWidget w : controls) { w.setY(controlY.get(w) - controlsScroll); w.visible = w.getY() >= top && w.getY() + w.getHeight() <= height - 10; }
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xD018202A); hits.clear();
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        updatePaletteWidgets();
        if (presetsTab) { renderPresets(g, mx, my); super.render(g, mx, my, delta); return; }
        g.fill(left - 2, gridTop - 2, left + panelWidth + 2, gridBottom + 2, 0xFF10151C);
        g.enableScissor(left, gridTop, left + panelWidth, gridBottom);
        int rows = Math.max(1, (gridBottom-gridTop)/22);
        for (int i = gridRow * columns; i < Math.min(matches.size(), (gridRow + rows) * columns); i++) {
            Block block = matches.get(i); int x = left + (i % columns) * 22, y = gridTop + (i / columns - gridRow) * 22;
            g.renderItem(new ItemStack(block), x + 3, y + 3);
            if (mx >= x && mx < x+22 && my >= y && my < y+22) g.setTooltipForNextFrame(block.getName().copy().append(" (" + BuiltInRegistries.BLOCK.getKey(block) + ")"), mx, my);
            hits.add(new Hit(x,y,22,22, () -> add(block), () -> {}));
        }
        g.disableScissor();
        int count = replacementsTab ? settings.replacements.size() : settings.filters.size();
        g.drawString(font, text(replacementsTab ? "selected_replacements" : "selected_filters", count), left, gridBottom + 7, 0xFFFFFFFF);
        g.enableScissor(left, selectedTop, left + panelWidth, selectedBottom);
        int visible = Math.max(0, (selectedBottom-selectedTop)/24);
        selectedRow = Math.clamp(selectedRow, 0, Math.max(0,count-visible));
        for (int i = selectedRow; i < Math.min(count,selectedRow+visible); i++) {
            final int index = i; Identifier id = replacementsTab ? settings.replacements.get(i).id() : settings.filters.get(i);
            Block block = BuiltInRegistries.BLOCK.getValue(id); int y = selectedTop + (i-selectedRow)*24;
            g.renderItem(new ItemStack(block), left+2, y+3);
            Runnable remove = () -> { if (replacementsTab) settings.replacements.remove(index); else settings.filters.remove(index); };
            int extra = replacementsTab ? 78 : 24;
            String name = font.plainSubstrByWidth(block.getName().getString(), Math.max(10,panelWidth-extra-24));
            g.drawString(font,name,left+24,y+8,0xFFE0E8F0);
            hits.add(new Hit(left,y,panelWidth-extra,24, () -> {}, remove));
            if (mx >= left && mx < left+panelWidth-extra && my >= y && my < y+24) g.setTooltipForNextFrame(block.getName().copy().append(" ("+id+")"),mx,my);
            mini(g,left+panelWidth-20,y,text("remove_symbol"),text("remove"),remove,mx,my);
            if (replacementsTab) {
                int weight = settings.replacements.get(index).weight();
                mini(g,left+panelWidth-78,y,text("decrease_symbol"),text("decrease_weight"),() -> weight(index,-1),mx,my);
                g.drawCenteredString(font,Integer.toString(weight),left+panelWidth-48,y+8,0xFFFFFFFF);
                mini(g,left+panelWidth-38,y,text("increase_symbol"),text("increase_weight"),() -> weight(index,1),mx,my);
            }
        }
        g.disableScissor();
        if (count == 0) g.drawWordWrap(font,text(replacementsTab ? "empty_replacements" : "empty_filter"),left,selectedTop+4,panelWidth,0xFF9DAAB8);
        super.render(g,mx,my,delta);
    }
    private void renderPresets(GuiGraphics g, int mx, int my) {
        List<PresetStore.Preset> presets = BrushConfig.presets.list();
        g.fill(left - 2, gridTop - 2, left + panelWidth + 2, selectedBottom + 2, 0xFF10151C);
        int visible = Math.max(0, (selectedBottom - gridTop) / 24);
        presetRow = Math.clamp(presetRow, 0, Math.max(0, presets.size() - visible));
        g.enableScissor(left, gridTop, left + panelWidth, selectedBottom);
        for (int i = presetRow; i < Math.min(presets.size(), presetRow + visible); i++) {
            PresetStore.Preset preset = presets.get(i); int y = gridTop + (i - presetRow) * 24;
            boolean hovered = mx >= left && mx < left + panelWidth - 24 && my >= y + 2 && my < y + 22;
            g.fill(left, y + 2, left + panelWidth - 24, y + 22, hovered ? 0xFF3C4B5B : 0xFF263342);
            g.drawString(font, font.plainSubstrByWidth(preset.name(), panelWidth - 32), left + 4, y + 8, 0xFFE0E8F0);
            hits.add(new Hit(left, y + 2, panelWidth - 24, 20, () -> loadPreset(preset), () -> {}));
            if (hovered) g.setTooltipForNextFrame(text("preset_load_hint", preset.name(), preset.blocks().size()), mx, my);
            mini(g, left + panelWidth - 20, y, text("remove_symbol"), text("preset_delete_hint"), () -> removePreset(preset.name()), mx, my);
        }
        g.disableScissor();
        if (presets.isEmpty()) g.drawWordWrap(font, text("presets_empty"), left + 4, gridTop + 4, panelWidth - 8, 0xFF9DAAB8);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        // The screen draws its own translucent background without blurring the live world.
    }
    private void weight(int index, int change) {
        var entry = settings.replacements.get(index);
        settings.replacements.set(index,new BrushSettings.WeightedEntry(entry.id(),BrushSettings.clampWeight(entry.weight()+change)));
    }
    private void mini(GuiGraphics g,int x,int y,Component label,Component tooltip,Runnable action,int mx,int my) {
        g.fill(x,y+2,x+18,y+22,0xFF3C4B5B); g.drawCenteredString(font,label,x+9,y+8,0xFFFFFFFF);
        hits.add(new Hit(x,y+2,18,20,action,action));
        if(mx>=x&&mx<x+18&&my>=y+2&&my<y+22) g.setTooltipForNextFrame(tooltip,mx,my);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 1) {
            if (over(undoKeyButton, event.x(), event.y())) { assignKey(false, KeyCombo.UNASSIGNED); return true; }
            if (over(redoKeyButton, event.x(), event.y())) { assignKey(true, KeyCombo.UNASSIGNED); return true; }
        }
        for (Hit hit : hits) if (hit.contains(event.x(),event.y())) {
            if(event.button()==0) hit.leftClick.run(); else if(event.button()==1) hit.rightClick.run(); return true;
        }
        return super.mouseClicked(event,doubled);
    }
    private static boolean over(AbstractWidget widget, double x, double y) {
        return widget != null && widget.visible && x >= widget.getX() && x < widget.getX() + widget.getWidth()
                && y >= widget.getY() && y < widget.getY() + widget.getHeight();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (capturingKey < 0) return super.keyPressed(event);
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { capturingKey = -1; updateKeyButtons(); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            assignKey(capturingKey == 1, KeyCombo.UNASSIGNED); return true;
        }
        if (key >= GLFW.GLFW_KEY_LEFT_SHIFT && key <= GLFW.GLFW_KEY_RIGHT_SUPER) return true;
        try { assignKey(capturingKey == 1, new KeyCombo(key, event.modifiers() & KeyCombo.MODIFIER_MASK)); }
        catch (IllegalArgumentException ignored) { /* Unknown/scancode-only inputs cannot be polled by key code. */ }
        return true;
    }
    private void assignKey(boolean redo, KeyCombo combo) {
        KeyCombo other = redo ? BrushConfig.undoKey : BrushConfig.redoKey;
        if (combo.isAssigned() && combo.equals(other)) { RetileClient.message("key_conflict"); return; }
        if (redo) BrushConfig.redoKey = combo; else BrushConfig.undoKey = combo;
        capturingKey = -1; updateKeyButtons(); BrushConfig.save(settings);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        int move = vertical > 0 ? -1 : 1;
        if (vertical == 0) return super.mouseScrolled(mx,my,horizontal,vertical);
        if(mx>=right) { controlsScroll += move*26; positionControls(); return true; }
        if(mx>=left&&mx<left+panelWidth) {
            if(presetsTab && my>=gridTop&&my<selectedBottom) presetRow=Math.max(0,presetRow+move);
            else if(my>=gridTop&&my<gridBottom) gridRow=Math.clamp(gridRow+move,0,Math.max(0,(matches.size()+columns-1)/columns-Math.max(1,(gridBottom-gridTop)/22)));
            else if(my>=selectedTop&&my<selectedBottom) selectedRow=Math.max(0,selectedRow+move);
            return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void removed() {
        settings.minY = parse(minY,settings.minY); settings.maxY = parse(maxY,settings.maxY);
        if(settings.minY>settings.maxY) { int temp=settings.minY; settings.minY=settings.maxY; settings.maxY=temp; }
        BrushConfig.save(settings);
    }
    private static int parse(EditBox box,int fallback) { try { return Integer.parseInt(box.getValue()); } catch(Exception e) { return fallback; } }
    @Override public boolean isPauseScreen() { return false; }
    private static final class ValueSlider extends AbstractSliderButton {
        private final double min,max,step; private final DoubleConsumer changed; private final DoubleFunction<Component> label;
        ValueSlider(int x,int y,int w,double min,double max,double current,DoubleConsumer changed,DoubleFunction<Component> label,double step) {
            super(x,y,w,20,Component.empty(),(current-min)/(max-min)); this.min=min; this.max=max; this.step=step; this.changed=changed; this.label=label; updateMessage();
        }
        private double actual() { return Math.clamp(Math.round((min+value*(max-min))/step)*step,min,max); }
        @Override protected void updateMessage() { setMessage(label.apply(actual())); }
        @Override protected void applyValue() { changed.accept(actual()); }
    }
}
