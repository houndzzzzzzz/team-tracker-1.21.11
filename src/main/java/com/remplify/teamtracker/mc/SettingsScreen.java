package com.remplify.teamtracker.mc;

import com.remplify.teamtracker.core.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

import static com.remplify.teamtracker.mc.TeamTrackerClient.tt;

public final class SettingsScreen extends Screen {
    private enum Page { TEAM("Team"), DISPLAY("Display"), STYLE("Style");
        final String label; Page(String l) { label = l; } }

    private static final int[] COLOURS = {0x55FF55, 0x55FFFF, 0xFF55FF, 0xFFAA00, 0xFF5555, 0x5555FF, 0xFFFF55, 0xFFFFFF};
    private static final String[] COLOUR_NAMES = {"Green", "Aqua", "Pink", "Gold", "Red", "Blue", "Yellow", "White"};

    private final Screen parent;
    private final Config cfg = tt.config;
    private static Page page = Page.TEAM;
    private final List<Label> labels = new ArrayList<>();

    private record Label(String text, int x, int y) {}

    public SettingsScreen(Screen parent) {
        super(Text.literal("Remplify Team Tracker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        int cx = width / 2;
        int tabW = 80;
        int tx = cx - (Page.values().length * (tabW + 4)) / 2;
        for (Page p : Page.values()) {
            ButtonWidget b = ButtonWidget.builder(Text.literal(p == page ? "[ " + p.label + " ]" : p.label), btn -> {
                page = p;
                clearAndInit();
            }).dimensions(tx, 28, tabW, 20).build();
            b.active = p != page;
            addDrawableChild(b);
            tx += tabW + 4;
        }

        int colW = 150, gap = 8, full = colW * 2 + gap;
        int left = cx - colW - gap / 2, right = cx + gap / 2;
        int y = 58;

        switch (page) {
            case TEAM -> {
                y = field(left, y, full, "Relay URL (wss://...)", cfg.relayUrl, 256, s -> cfg.relayUrl = s.trim());
                y = field(left, y, full, "Team code (same for all teammates, make it long)", cfg.teamCode, 64, s -> cfg.teamCode = s.trim());
                y = field(left, y, full, "Your nickname on teammates' screens (optional)", cfg.nickname, 24, s -> cfg.nickname = s);
                addDrawableChild(toggle(left, y, colW, "Tracker", () -> cfg.enabled, v -> cfg.enabled = v));
                addDrawableChild(toggle(right, y, colW, "Share my health", () -> cfg.shareHealth, v -> cfg.shareHealth = v));
                y += 24;
                addDrawableChild(toggle(left, y, colW, "Share inventory", () -> cfg.shareInventory, v -> cfg.shareInventory = v));
                addDrawableChild(ButtonWidget.builder(Text.literal("Team inventories..."),
                        b -> { cfg.save(); client.setScreen(new TeamListScreen(this)); }).dimensions(right, y, colW, 20).build());
                y += 24;
                addDrawableChild(ButtonWidget.builder(Text.literal("Apply & reconnect"), b -> {
                    cfg.save();
                    tt.relay.reconnectNow();
                }).dimensions(left, y, full, 20).build());
            }
            case DISPLAY -> {
                addDrawableChild(toggle(left, y, colW, "Names", () -> cfg.showNames, v -> cfg.showNames = v));
                addDrawableChild(toggle(right, y, colW, "Distance", () -> cfg.showDistance, v -> cfg.showDistance = v));
                y += 24;
                addDrawableChild(toggle(left, y, colW, "Health", () -> cfg.showHealth, v -> cfg.showHealth = v));
                addDrawableChild(toggle(right, y, colW, "Off-screen arrows", () -> cfg.edgeArrows, v -> cfg.edgeArrows = v));
                y += 24;
                addDrawableChild(cycle(left, y, colW, () -> "Text: " + cfg.showMode().label, () -> cfg.showMode = cfg.showMode().next().name()));
                addDrawableChild(slider(right, y, colW, "Look radius", 5, 300, cfg.lookRadiusPx, v -> cfg.lookRadiusPx = v, v -> Math.round(v) + "px"));
                y += 24;
                addDrawableChild(toggle(left, y, colW, "Hide if other world", () -> cfg.hideOnOtherServer, v -> cfg.hideOnOtherServer = v));
                addDrawableChild(slider(right, y, colW, "Hide within", 0, 64, cfg.hideWithinBlocks,
                        v -> cfg.hideWithinBlocks = Math.round(v), v -> Math.round(v) == 0 ? "off" : Math.round(v) + " blocks"));
                y += 24;
                addDrawableChild(slider(left, y, full, "Smoothing", 0, 120, cfg.outputSmoothingMs,
                        v -> cfg.outputSmoothingMs = Math.round(v), v -> Math.round(v) == 0 ? "off" : Math.round(v) + "ms"));
            }
            case STYLE -> {
                addDrawableChild(cycle(left, y, colW, () -> "Symbol: " + cfg.symbol().label, () -> cfg.symbol = cfg.symbol().next().name()));
                addDrawableChild(slider(right, y, colW, "Symbol size", 3, 24, cfg.symbolSize, v -> cfg.symbolSize = (int) Math.round(v), v -> String.valueOf(Math.round(v))));
                y += 24;
                addDrawableChild(cycle(left, y, colW, () -> "Colour: " + cfg.colorMode().label, () -> cfg.colorMode = cfg.colorMode().next().name()));
                addDrawableChild(cycle(right, y, colW, () -> "One colour: " + colourName(cfg.customColor),
                        () -> cfg.customColor = COLOURS[(colourIndex(cfg.customColor) + 1) % COLOURS.length]));
                y += 24;
                addDrawableChild(slider(left, y, colW, "Text size", 0.5, 3.0, cfg.textScale, v -> cfg.textScale = Math.round(v * 20) / 20.0, v -> String.format(Locale.ROOT, "%.2fx", v)));
                addDrawableChild(toggle(right, y, colW, "Text shadow", () -> cfg.textShadow, v -> cfg.textShadow = v));
                y += 24;
                addDrawableChild(toggle(left, y, colW, "Text background", () -> cfg.textBackground, v -> cfg.textBackground = v));
                addDrawableChild(toggle(right, y, colW, "Shrink when far", () -> cfg.distanceFade, v -> cfg.distanceFade = v));
            }
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(cx - 100, height - 28, 200, 20).build());
    }

    private int field(int x, int y, int w, String label, String value, int max, Consumer<String> onChange) {
        labels.add(new Label(label, x, y));
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y + 11, w, 18, Text.literal(label));
        f.setMaxLength(max);
        f.setText(value == null ? "" : value);
        f.setChangedListener(onChange);
        addDrawableChild(f);
        return y + 36;
    }

    private ButtonWidget toggle(int x, int y, int w, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        return ButtonWidget.builder(Text.literal(name + ": " + (get.get() ? "ON" : "OFF")), b -> {
            set.accept(!get.get());
            b.setMessage(Text.literal(name + ": " + (get.get() ? "ON" : "OFF")));
        }).dimensions(x, y, w, 20).build();
    }

    private ButtonWidget cycle(int x, int y, int w, Supplier<String> label, Runnable next) {
        return ButtonWidget.builder(Text.literal(label.get()), b -> {
            next.run();
            b.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, w, 20).build();
    }

    private SliderWidget slider(int x, int y, int w, String name, double min, double max, double current,
                                Consumer<Double> set, DoubleFunction<String> fmt) {
        double start = (Math.max(min, Math.min(max, current)) - min) / (max - min);
        return new SliderWidget(x, y, w, 20, Text.empty(), start) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Text.literal(name + ": " + fmt.apply(min + value * (max - min)))); }
            @Override protected void applyValue() { set.accept(min + value * (max - min)); }
        };
    }

    private static int colourIndex(int c) {
        for (int i = 0; i < COLOURS.length; i++) if (COLOURS[i] == (c & 0xFFFFFF)) return i;
        return -1;
    }

    private static String colourName(int c) {
        int i = colourIndex(c);
        return i < 0 ? String.format("#%06X", c & 0xFFFFFF) : COLOUR_NAMES[i];
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        for (Label l : labels) ctx.drawText(textRenderer, Text.literal(l.text), l.x, l.y, 0xFFA0A0A0, false);
        if (page == Page.TEAM) ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(tt.statusLine()), width / 2, height - 46, 0xFFFFFFFF);
    }

    @Override
    public void removed() { cfg.save(); }

    @Override
    public void close() {
        cfg.save();
        if (client != null) client.setScreen(parent);
    }
}
