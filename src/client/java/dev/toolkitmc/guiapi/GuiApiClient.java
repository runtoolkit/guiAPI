package dev.toolkitmc.guiapi;

import dev.toolkitmc.guiapi.gui.GuiDefinition;
import dev.toolkitmc.guiapi.gui.OpenDialogPayload;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import dev.toolkitmc.guiapi.screen.DialogScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public class GuiApiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Handle S→C open_dialog packet: look up the GUI and open DialogScreen
        ClientPlayNetworking.registerGlobalReceiver(OpenDialogPayload.ID, (payload, context) -> {
            GuiDefinition def = GuiRegistry.INSTANCE.get(payload.guiId()).orElse(null);
            if (def == null) {
                GuiApiMod.LOGGER.warn("[GuiAPI] Client received open_dialog for unknown GUI: {}",
                        payload.guiId());
                return;
            }

            // Schedule screen open on render thread
            context.client().execute(() ->
                    MinecraftClient.getInstance().setScreen(new DialogScreen(def)));
        });
    }
}
