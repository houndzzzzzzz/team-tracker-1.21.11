package com.remplify.teamtracker.mc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.remplify.teamtracker.core.Config;
import com.remplify.teamtracker.core.FrameView;
import com.remplify.teamtracker.core.TeamTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/** Minecraft 1.21.11 entrypoint. */
public final class TeamTrackerClient implements ClientModInitializer {
    public static TeamTracker tt;
    public static Bridge bridge;

    private KeyBinding menuKey, invKey, toggleKey, revealKey, symbolKey, colourKey, showModeKey;
    private Supplier<Screen> openNextTick;

    @Override
    public void onInitializeClient() {
        MinecraftClient mc = MinecraftClient.getInstance();
        tt = new TeamTracker();
        bridge = new Bridge(mc);

        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(TeamTracker.MOD_ID, "main"));
        menuKey = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT, cat);
        invKey = key("inventories", GLFW.GLFW_KEY_I, cat);
        revealKey = key("reveal", GLFW.GLFW_KEY_LEFT_ALT, cat);
        toggleKey = key("toggle", InputUtil.UNKNOWN_KEY.getCode(), cat);
        symbolKey = key("cycle_symbol", InputUtil.UNKNOWN_KEY.getCode(), cat);
        colourKey = key("cycle_colour", InputUtil.UNKNOWN_KEY.getCode(), cat);
        showModeKey = key("cycle_show", InputUtil.UNKNOWN_KEY.getCode(), cat);

        HudElementRegistry.addLast(Identifier.of(TeamTracker.MOD_ID, "markers"), this::renderHud);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        registerCommands();
    }

    private static KeyBinding key(String name, int code, KeyBinding.Category cat) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key." + TeamTracker.MOD_ID + "." + name, InputUtil.Type.KEYSYM, code, cat));
    }

    private void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        McCanvas canvas = new McCanvas(mc, ctx);
        if (!mc.options.hudHidden) {
            FrameView v = bridge.frame(tickCounter.getTickProgress(true));
            if (v != null) tt.markers.render(canvas, v, revealKey.isPressed());
        }
        tt.notifier.draw(canvas);
    }

    private void onTick(MinecraftClient mc) {
        if (openNextTick != null) {
            mc.setScreen(openNextTick.get());
            openNextTick = null;
        }
        while (menuKey.wasPressed()) mc.setScreen(new SettingsScreen(mc.currentScreen));
        while (invKey.wasPressed()) mc.setScreen(new TeamListScreen(mc.currentScreen));
        while (toggleKey.wasPressed()) tt.toggle(() -> tt.config.enabled = !tt.config.enabled, "Team Tracker " + onOff(!tt.config.enabled));
        while (symbolKey.wasPressed()) tt.toggle(() -> tt.config.symbol = tt.config.symbol().next().name(), "Symbol: " + tt.config.symbol().next().label);
        while (colourKey.wasPressed()) tt.toggle(() -> tt.config.colorMode = tt.config.colorMode().next().name(), "Colour: " + tt.config.colorMode().next().label);
        while (showModeKey.wasPressed()) tt.toggle(() -> tt.config.showMode = tt.config.showMode().next().name(), "Text: " + tt.config.showMode().next().label);
        tt.relay.tick(bridge.sample(), bridge::inventory);
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommandManager.literal("tt")
                        .executes(c -> { openNextTick = () -> new SettingsScreen(null); return 1; })
                        .then(ClientCommandManager.literal("menu").executes(c -> { openNextTick = () -> new SettingsScreen(null); return 1; }))
                        .then(ClientCommandManager.literal("inv").executes(c -> { openNextTick = () -> new TeamListScreen(null); return 1; }))
                        .then(ClientCommandManager.literal("status").executes(c -> { tt.notifier.show(tt.statusLine()); return 1; }))
                        .then(ClientCommandManager.literal("on").executes(c -> { tt.toggle(() -> tt.config.enabled = true, "Team Tracker ON"); return 1; }))
                        .then(ClientCommandManager.literal("off").executes(c -> { tt.toggle(() -> tt.config.enabled = false, "Team Tracker OFF"); return 1; }))
                        .then(ClientCommandManager.literal("team")
                                .then(ClientCommandManager.argument("code", StringArgumentType.greedyString()).executes(c -> {
                                    String code = StringArgumentType.getString(c, "code").trim();
                                    tt.toggle(() -> tt.config.teamCode = code, "Team code set");
                                    tt.relay.reconnectNow();
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("nick")
                                .executes(c -> { tt.toggle(() -> tt.config.nickname = "", "Nickname cleared"); tt.relay.markDirty(); return 1; })
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(c -> {
                                    String n = Config.cleanNickname(StringArgumentType.getString(c, "name"));
                                    tt.toggle(() -> tt.config.nickname = n, "Nickname: " + n);
                                    tt.relay.markDirty();
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("relay")
                                .then(ClientCommandManager.argument("url", StringArgumentType.greedyString()).executes(c -> {
                                    String url = StringArgumentType.getString(c, "url").trim();
                                    tt.toggle(() -> tt.config.relayUrl = url, "Relay set");
                                    tt.relay.reconnectNow();
                                    return 1;
                                })))
        ));
    }
}
