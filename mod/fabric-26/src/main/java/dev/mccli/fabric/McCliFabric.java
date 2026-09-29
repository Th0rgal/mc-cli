package dev.mccli.fabric;

import dev.mccli.McCliMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric entrypoint for MC-CLI (Minecraft 26.x).
 *
 * All command logic lives in the shared common-26 sources; this class only wires
 * the loader lifecycle (client init and client tick) into {@link McCliMod}.
 */
public class McCliFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        McCliMod.init("Fabric");
        ClientTickEvents.END_CLIENT_TICK.register(client -> McCliMod.onClientTick());
    }
}
