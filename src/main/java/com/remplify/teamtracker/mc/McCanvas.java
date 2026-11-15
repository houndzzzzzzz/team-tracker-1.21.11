package com.remplify.teamtracker.mc;

import com.remplify.teamtracker.core.Canvas;
import com.remplify.teamtracker.core.InventoryPanel;
import com.remplify.teamtracker.core.ItemInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 1.21.11 (Yarn names): Canvas backed by DrawContext. */
public final class McCanvas implements Canvas {
    private final MinecraftClient mc;
    private final DrawContext ctx;

    public McCanvas(MinecraftClient mc, DrawContext ctx) {
        this.mc = mc;
        this.ctx = ctx;
    }

    @Override public int width() { return ctx.getScaledWindowWidth(); }
    @Override public int height() { return ctx.getScaledWindowHeight(); }
    @Override public void fill(int x1, int y1, int x2, int y2, int argb) { ctx.fill(x1, y1, x2, y2, argb); }
    @Override public void text(String s, int x, int y, int argb, boolean shadow) { ctx.drawText(mc.textRenderer, Text.literal(s), x, y, argb, shadow); }
    @Override public int textWidth(String s) { return mc.textRenderer.getWidth(s); }
    @Override public int fontHeight() { return mc.textRenderer.fontHeight; }
    @Override public void push() { ctx.getMatrices().pushMatrix(); }
    @Override public void pop() { ctx.getMatrices().popMatrix(); }
    @Override public void translate(float x, float y) { ctx.getMatrices().translate(x, y); }
    @Override public void scale(float x, float y) { ctx.getMatrices().scale(x, y); }
    @Override public void rotate(float radians) { ctx.getMatrices().rotate(radians); }

    @Override
    public boolean head(String username, int x, int y, int size) {
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (nh == null) return false;
        PlayerListEntry entry = nh.getPlayerListEntry(username);
        if (entry == null) return false;
        PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), x, y, size);
        return true;
    }

    @Override
    public void item(ItemInfo info, int x, int y) {
        if (info.cachedStack == null) info.cachedStack = buildStack(info);
        if (info.cachedStack instanceof ItemStack stack && !stack.isEmpty()) ctx.drawItem(stack, x, y);
        else InventoryPanel.unknownItem(this, info, x, y);
    }

    private static Object buildStack(ItemInfo info) {
        Identifier id = Identifier.tryParse(info.id);
        if (id == null || !Registries.ITEM.containsId(id)) return Boolean.FALSE;
        ItemStack stack = new ItemStack(Registries.ITEM.get(id), Math.max(1, info.count));
        if (info.glint) stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (info.maxDamage > 0 && info.damage > 0 && stack.isDamageable()) stack.setDamage(Math.min(info.damage, stack.getMaxDamage() - 1));
        return stack;
    }
}
