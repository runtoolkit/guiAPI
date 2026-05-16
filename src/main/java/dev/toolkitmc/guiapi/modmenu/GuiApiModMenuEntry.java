package dev.toolkitmc.guiapi.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

/**
 * Mod Menu integration — shows a simple info screen listing loaded GUIs.
 *
 * This class is only loaded when Mod Menu is present (modCompileOnly dependency).
 * The entrypoint declaration in fabric.mod.json is under "modmenu", which Fabric
 * Loader only invokes if the modmenu mod is installed.
 */
public class GuiApiModMenuEntry implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new GuiApiInfoScreen(parent);
    }

    // ── Simple info screen ───────────────────────────────────────────────────

    private static class GuiApiInfoScreen extends Screen {

        private final Screen parent;

        protected GuiApiInfoScreen(Screen parent) {
            super(Text.literal("GUI API"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            // Title
            addDrawableChild(new TextWidget(
                    width / 2 - 150, 20, 300, 10,
                    Text.literal("§6GUI API §7— Datapack-driven chest GUI system"),
                    textRenderer));

            // Loaded GUI count
            int count = GuiRegistry.INSTANCE.getAll().size();
            addDrawableChild(new TextWidget(
                    width / 2 - 150, 40, 300, 10,
                    Text.literal("§7Loaded GUIs: §f" + count +
                            (count == 0 ? " §c(join a world to load datapacks)" : "")),
                    textRenderer));

            // Loaded GUI list (up to 10)
            int y = 60;
            int shown = 0;
            for (var entry : GuiRegistry.INSTANCE.getAll().entrySet()) {
                if (shown >= 10) {
                    addDrawableChild(new TextWidget(
                            width / 2 - 150, y, 300, 10,
                            Text.literal("§8... and " + (count - 10) + " more"),
                            textRenderer));
                    break;
                }
                var def = entry.getValue();
                addDrawableChild(new TextWidget(
                        width / 2 - 150, y, 300, 10,
                        Text.literal("§a" + entry.getKey() +
                                " §8[rows=" + def.getRows() +
                                ", pages=" + def.getPageCount() + "]"),
                        textRenderer));
                y += 12;
                shown++;
            }

            // Close button
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Close"),
                    btn -> MinecraftClient.getInstance().setScreen(parent))
                    .dimensions(width / 2 - 50, height - 30, 100, 20)
                    .build());
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
}
