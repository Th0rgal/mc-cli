package dev.mccli.neoforge;

import dev.mccli.McCliMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * NeoForge entrypoint for MC-CLI (Minecraft 26.x).
 *
 * All command logic lives in the shared common-26 sources; this class only wires
 * the loader lifecycle (mod construction and client tick) into {@link McCliMod}.
 */
@Mod(value = McCliMod.MOD_ID, dist = Dist.CLIENT)
public class McCliNeoForge {
    public McCliNeoForge() {
        McCliMod.init("NeoForge");
    }

    @EventBusSubscriber(modid = McCliMod.MOD_ID, value = Dist.CLIENT)
    public static class ClientTickHandler {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            McCliMod.onClientTick();
        }
    }
}
