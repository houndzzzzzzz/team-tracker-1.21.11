package com.remplify.teamtracker.mc;

import com.remplify.teamtracker.core.Teammate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

import static com.remplify.teamtracker.mc.TeamTrackerClient.tt;

/** Lists everyone on the team code; click a sharing teammate to see their inventory. */
public final class TeamListScreen extends Screen {
    private static final int PER_PAGE = 8;
    private final Screen parent;
    private int page;
    private int builtVersion = -1;
    private boolean builtShare;
    private String message = "";

    public TeamListScreen(Screen parent) {
        super(Text.literal("Team Inventories"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        builtVersion = tt.store.rosterVersion();
        builtShare = tt.config.shareInventory;
        int cx = width / 2;
        int y = 34;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Share my inventory: " + (tt.config.shareInventory ? "ON" : "OFF")), b -> {
                    tt.config.shareInventory = !tt.config.shareInventory;
                    tt.config.save();
                    clearAndInit();
                }).dimensions(cx - 100, y, 200, 20).build());
        y += 30;

        List<Teammate.Member> roster = tt.store.roster();
        message = "";
        if (!tt.relay.isOpen()) message = tt.statusLine();
        else if (roster.isEmpty()) message = "Nobody else is on your team code right now.";
        else if (!tt.config.shareInventory) message = "Turn on sharing to see teammates who also share.";

        int pages = Math.max(1, (roster.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        for (int i = page * PER_PAGE; i < Math.min(roster.size(), (page + 1) * PER_PAGE); i++) {
            Teammate.Member m = roster.get(i);
            boolean can = tt.config.shareInventory && m.sharesInventory;
            String label = m.displayName() + (m.sharesInventory ? "  ✔ sharing" : "  ✖ not sharing");
            ButtonWidget b = ButtonWidget.builder(Text.literal(label),
                    btn -> client.setScreen(new TeamInventoryScreen(this, m.username))).dimensions(cx - 120, y, 240, 20).build();
            b.active = can;
            addDrawableChild(b);
            y += 22;
        }
        if (pages > 1) {
            addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page = (page + pages - 1) % pages; clearAndInit(); })
                    .dimensions(cx - 120, height - 54, 40, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page = (page + 1) % pages; clearAndInit(); })
                    .dimensions(cx + 80, height - 54, 40, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(cx - 100, height - 28, 200, 20).build());
    }

    @Override
    public void tick() {
        // refresh when people join/leave or change their sharing setting
        if (builtVersion != tt.store.rosterVersion() || builtShare != tt.config.shareInventory) clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        if (!message.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(message), width / 2, height - 70, 0xFFFFAA55);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
