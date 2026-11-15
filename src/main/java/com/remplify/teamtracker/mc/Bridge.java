package com.remplify.teamtracker.mc;

import com.remplify.teamtracker.core.FrameView;
import com.remplify.teamtracker.core.ItemInfo;
import com.remplify.teamtracker.core.PlayerSample;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 1.21.11 (Yarn names): reads game state into the shared, version-independent types. */
public final class Bridge {
    private final MinecraftClient mc;
    private Field cameraPosField;
    private boolean cameraPosLookedUp;
    private Method fovMethod;
    private boolean fovLookedUp;

    public Bridge(MinecraftClient mc) {
        this.mc = mc;
    }

    public PlayerSample sample() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return null;
        ServerInfo server = mc.getCurrentServerEntry();
        return new PlayerSample(
                mc.getSession().getUsername(),
                p.getX(), p.getY(), p.getZ(),
                PlayerSample.dimensionFromKeyString(mc.world.getRegistryKey().toString()),
                server == null || server.address == null ? "" : server.address,
                p.getHealth(), p.getAbsorptionAmount(), p.getMaxHealth());
    }

    public FrameView frame(float td) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return null;
        FrameView v = new FrameView();
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d c = cameraPos(cam, td);
        v.camX = c.x; v.camY = c.y; v.camZ = c.z;
        v.yaw = cam.getYaw();
        v.pitch = cam.getPitch();
        v.fovDegrees = fov(cam, td);
        Vec3d me = p.getLerpedPos(td);
        v.meX = me.x; v.meY = me.y; v.meZ = me.z;
        PlayerSample s = sample();
        v.worldKey = PlayerSample.serverId(s.serverAddress()) + "|" + s.dimension();
        for (AbstractClientPlayerEntity o : mc.world.getPlayers()) {
            if (o == p) continue;
            Vec3d pos = o.getLerpedPos(td);
            v.loaded.put(o.getName().getString().toLowerCase(Locale.ROOT), new double[]{pos.x, pos.y, pos.z});
        }
        return v;
    }

    // ------------------------------------------------------------------ inventory

    public List<ItemInfo> inventory() {
        List<ItemInfo> out = new ArrayList<>();
        ClientPlayerEntity p = mc.player;
        if (p == null) return out;
        for (int i = 0; i < 36; i++) add(out, i, p.getInventory().getStack(i));
        add(out, 36, p.getEquippedStack(EquipmentSlot.FEET));
        add(out, 37, p.getEquippedStack(EquipmentSlot.LEGS));
        add(out, 38, p.getEquippedStack(EquipmentSlot.CHEST));
        add(out, 39, p.getEquippedStack(EquipmentSlot.HEAD));
        add(out, 40, p.getOffHandStack());
        return out;
    }

    private static void add(List<ItemInfo> out, int slot, ItemStack s) {
        if (s == null || s.isEmpty()) return;
        ItemInfo i = new ItemInfo();
        i.slot = slot;
        i.id = Registries.ITEM.getId(s.getItem()).toString();
        i.count = s.getCount();
        if (s.isDamageable()) { i.damage = s.getDamage(); i.maxDamage = s.getMaxDamage(); }
        i.glint = s.hasGlint();
        i.name = segs(s.getName());
        LoreComponent lore = s.get(DataComponentTypes.LORE);
        if (lore != null) for (Text line : lore.lines()) i.lore.add(segs(line));
        ItemEnchantmentsComponent ench = s.getEnchantments();
        if (ench != null) {
            for (RegistryEntry<Enchantment> e : ench.getEnchantments()) {
                i.enchants.add(Enchantment.getName(e, ench.getLevel(e)).getString());
            }
        }
        out.add(i);
    }

    private static List<ItemInfo.Seg> segs(Text text) {
        List<ItemInfo.Seg> out = new ArrayList<>();
        text.visit((Style style, String str) -> {
            if (!str.isEmpty()) {
                TextColor col = style.getColor();
                out.add(new ItemInfo.Seg(str, col == null ? -1 : col.getRgb(), style.isBold(), style.isItalic()));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    // ------------------------------------------------------------------ camera helpers

    private Vec3d cameraPos(Camera cam, float td) {
        if (!cameraPosLookedUp) {
            cameraPosLookedUp = true;
            for (Field f : Camera.class.getDeclaredFields()) {
                if (f.getType() == Vec3d.class && !Modifier.isStatic(f.getModifiers())) {
                    try { f.setAccessible(true); cameraPosField = f; } catch (Exception ignored) { }
                    break;
                }
            }
        }
        if (cameraPosField != null) {
            try {
                if (cameraPosField.get(cam) instanceof Vec3d v) return v;
            } catch (Exception ignored) { }
        }
        return mc.player.getCameraPosVec(td);
    }

    private double fov(Camera cam, float td) {
        if (!fovLookedUp) {
            fovLookedUp = true;
            for (Method m : GameRenderer.class.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == Camera.class && p[1] == float.class && p[2] == boolean.class
                        && (m.getReturnType() == float.class || m.getReturnType() == double.class)) {
                    try { m.setAccessible(true); fovMethod = m; } catch (Exception ignored) { }
                    break;
                }
            }
        }
        if (fovMethod != null) {
            try {
                double v = ((Number) fovMethod.invoke(mc.gameRenderer, cam, td, true)).doubleValue();
                if (v > 1 && v < 179) return v;
            } catch (Exception ignored) { }
        }
        return mc.options.getFov().getValue();
    }
}
