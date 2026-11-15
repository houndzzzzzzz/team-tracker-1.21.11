package com.remplify.teamtracker.mc;

import com.remplify.teamtracker.core.InventoryPanel;
import com.remplify.teamtracker.core.ItemInfo;
import com.remplify.teamtracker.core.Teammate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import static com.remplify.teamtracker.mc.TeamTrackerClient.tt;

/** Shows one teammate's inventory, refreshed live while open. */
public final class TeamInventoryScreen extends Screen {
    private final Screen parent;
    private final String username;
    private int ticks;

    public TeamInventoryScreen(Screen parent, String username) {
        super(Text.literal(username + "'s inventory"));
        this.parent = parent;
        this.username = username;
    }

    @Override
    protected void init() {
        tt.relay.requestInventory(username);
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close()).dimensions(width / 2 - 100, height - 28, 200, 20).build());
    }

    @Override
    public void tick() {
        if (++ticks % 80 == 0) tt.relay.requestInventory(username);   // keep it live (every 4s)
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        McCanvas c = new McCanvas(client, ctx);
        Teammate.Member m = tt.store.findMember(username);
        String name = m == null ? username : m.displayName();
        c.text(name, width / 2 - c.textWidth(name) / 2, 12, 0xFFFFFFFF, true);

        String status;
        if (m == null) status = username + " left the team";
        else if (!tt.config.shareInventory) status = "Turn on 'Share inventory' to view";
        else if (!m.sharesInventory) status = username + " stopped sharing";
        else status = InventoryPanel.statusLines(m).get(0);
        c.text(status, width / 2 - c.textWidth(status) / 2, 24, 0xFFAAAAAA, false);

        boolean visible = m != null && m.sharesInventory && tt.config.shareInventory;
        boolean side = width >= InventoryPanel.WIDTH + InventoryPanel.DETAILS_WIDTH + 30;
        int totalW = side ? InventoryPanel.WIDTH + 8 + InventoryPanel.DETAILS_WIDTH : InventoryPanel.WIDTH;
        int x = width / 2 - totalW / 2, y = 40;
        ItemInfo hovered = InventoryPanel.draw(c, visible ? m.inventory : null, x, y, mouseX, mouseY);
        if (side) InventoryPanel.drawDetails(c, hovered, x + InventoryPanel.WIDTH + 8, y, Math.max(InventoryPanel.HEIGHT, height - y - 40));
        else InventoryPanel.drawDetails(c, hovered, x + (InventoryPanel.WIDTH - InventoryPanel.DETAILS_WIDTH) / 2, y + InventoryPanel.HEIGHT + 6, Math.max(40, height - y - InventoryPanel.HEIGHT - 46));
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
