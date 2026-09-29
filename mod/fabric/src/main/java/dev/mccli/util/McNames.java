package dev.mccli.util;

import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.util.ActionResult;

import java.util.Map;

/**
 * Mojang-named descriptions of Minecraft objects for JSON responses.
 *
 * In a production Fabric client, Minecraft classes carry intermediary names
 * (e.g. "class_525"), so getSimpleName() and record toString() would leak those.
 * NeoForge runs with Mojang names, so these helpers keep responses identical
 * across loaders for the classes the CLI commonly sees.
 */
public final class McNames {
    private McNames() {}

    private static final Map<Class<?>, String> SCREENS = Map.ofEntries(
        Map.entry(AccessibilityOnboardingScreen.class, "AccessibilityOnboardingScreen"),
        Map.entry(ChatScreen.class, "ChatScreen"),
        Map.entry(ConfirmScreen.class, "ConfirmScreen"),
        Map.entry(ConnectScreen.class, "ConnectScreen"),
        Map.entry(CreateWorldScreen.class, "CreateWorldScreen"),
        Map.entry(CreativeInventoryScreen.class, "CreativeModeInventoryScreen"),
        Map.entry(DeathScreen.class, "DeathScreen"),
        Map.entry(DirectConnectScreen.class, "DirectJoinServerScreen"),
        Map.entry(DisconnectedScreen.class, "DisconnectedScreen"),
        Map.entry(GameMenuScreen.class, "PauseScreen"),
        Map.entry(GenericContainerScreen.class, "ContainerScreen"),
        Map.entry(InventoryScreen.class, "InventoryScreen"),
        Map.entry(LevelLoadingScreen.class, "LevelLoadingScreen"),
        Map.entry(MessageScreen.class, "GenericMessageScreen"),
        Map.entry(MultiplayerScreen.class, "JoinMultiplayerScreen"),
        Map.entry(OptionsScreen.class, "OptionsScreen"),
        Map.entry(ProgressScreen.class, "ProgressScreen"),
        Map.entry(SelectWorldScreen.class, "SelectWorldScreen"),
        Map.entry(SleepingChatScreen.class, "InBedChatScreen"),
        Map.entry(TitleScreen.class, "TitleScreen"),
        Map.entry(VideoOptionsScreen.class, "VideoSettingsScreen")
    );

    /**
     * Screen class name as NeoForge reports it (Mojang simple name); falls back to
     * the runtime simple name for modded or unlisted screens.
     */
    public static String screen(Screen screen) {
        String name = SCREENS.get(screen.getClass());
        return name != null ? name : screen.getClass().getSimpleName();
    }

    /**
     * ActionResult formatted like Mojang's InteractionResult record toString(),
     * e.g. "Pass[]" or "Success[swingSource=CLIENT, itemContext=ItemContext[...]]".
     */
    public static String actionResult(ActionResult result) {
        if (result instanceof ActionResult.Success success) {
            ActionResult.ItemContext ctx = success.itemContext();
            return "Success[swingSource=" + success.swingSource()
                + ", itemContext=ItemContext[wasItemInteraction=" + ctx.incrementStat()
                + ", heldItemTransformedTo=" + ctx.newHandStack() + "]]";
        }
        if (result instanceof ActionResult.Pass) {
            return "Pass[]";
        }
        if (result instanceof ActionResult.Fail) {
            return "Fail[]";
        }
        if (result instanceof ActionResult.PassToDefaultBlockAction) {
            return "TryEmptyHandInteraction[]";
        }
        return String.valueOf(result);
    }
}
