package dev.mccli.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Small helpers that isolate Minecraft 26.x client API moves from the command code.
 *
 * 26.x changes relative to 1.21.11:
 * - The open screen lives on {@code Minecraft.gui} ({@code gui.screen()} / {@code gui.setScreen()}).
 * - The F1 "hide GUI" flag moved from {@code Options.hideGui} to {@code Gui.hud} ({@code isHidden()} / {@code toggle()}).
 * - Day time is driven by world clocks ({@code Level.getOverworldClockTime()}).
 * - 26.3 windows are SDL3 (26.2 still GLFW); version specifics live in {@code dev.mccli.platform.Platform}.
 */
public final class ClientCompat {
    private ClientCompat() {}

    public static @Nullable Screen screen(Minecraft client) {
        return client.gui.screen();
    }

    public static void setScreen(Minecraft client, @Nullable Screen screen) {
        client.gui.setScreen(screen);
    }

    public static boolean isHudHidden(Minecraft client) {
        return client.gui.hud.isHidden();
    }

    public static void setHudHidden(Minecraft client, boolean hidden) {
        if (client.gui.hud.isHidden() != hidden) {
            client.gui.hud.toggle();
        }
    }

    /** Time of day in ticks (0-23999), equivalent to the pre-26 {@code getDayTime() % 24000}. */
    public static long timeOfDay(Level level) {
        return Math.floorMod(level.getOverworldClockTime(), 24000L);
    }

    /** Native handle of the main game window (SDL_Window* on 26.3, GLFW on 26.2), or 0 if unavailable. */
    public static long windowHandle(Minecraft client) {
        return client.getWindow() != null ? client.getWindow().handle() : 0L;
    }
}
