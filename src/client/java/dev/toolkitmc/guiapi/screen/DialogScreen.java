package dev.toolkitmc.guiapi.screen;

import dev.toolkitmc.guiapi.gui.GuiDefinition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Custom dialog screen displayed when a GuiDefinition of type DIALOG is opened.
 *
 * Layout:
 *   ┌──────────────────────────┐
 *   │         TITLE            │
 *   │       body text          │
 *   │  [Action1]  [Action2]    │
 *   └──────────────────────────┘
 */
public class DialogScreen extends Screen {

    private static final int PANEL_WIDTH  = 256;
    private static final int PANEL_HEIGHT = 160;
    private static final int PADDING      = 16;
    private static final int BUTTON_H     = 20;
    private static final int BUTTON_W     = 100;
    private static final int BUTTON_GAP   = 6;

    private final GuiDefinition definition;

    private int panelX;
    private int panelY;

    public DialogScreen(GuiDefinition definition) {
        super(Text.literal(definition.getTitle()));
        this.definition = definition;
    }

    @Override
    protected void init() {
        super.init();

        panelX = (width  - PANEL_WIDTH)  / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        var actions = definition.getDialogActions();
        if (actions.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("OK"), btn -> closeScreen())
                    .dimensions(panelX + (PANEL_WIDTH - BUTTON_W) / 2,
                                panelY + PANEL_HEIGHT - PADDING - BUTTON_H,
                                BUTTON_W, BUTTON_H)
                    .build());
            return;
        }

        int totalW = actions.size() * BUTTON_W + (actions.size() - 1) * BUTTON_GAP;
        int startX = panelX + (PANEL_WIDTH - totalW) / 2;
        int btnY   = panelY + PANEL_HEIGHT - PADDING - BUTTON_H;

        for (int i = 0; i < actions.size(); i++) {
            GuiDefinition.DialogAction action = actions.get(i);
            int btnX = startX + i * (BUTTON_W + BUTTON_GAP);

            addDrawableChild(ButtonWidget.builder(
                    Text.literal(action.label()),
                    btn -> executeDialogAction(action))
                    .dimensions(btnX, btnY, BUTTON_W, BUTTON_H)
                    .build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw dim overlay manually instead of renderBackground (avoids blur in 1.21.1)
        context.fill(0, 0, width, height, 0x88000000);

        // Panel background
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xCC000000);

        // Border
        int border = 0xFF555555;
        context.fill(panelX,                   panelY,                    panelX + PANEL_WIDTH, panelY + 1,            border);
        context.fill(panelX,                   panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, border);
        context.fill(panelX,                   panelY,                    panelX + 1,           panelY + PANEL_HEIGHT, border);
        context.fill(panelX + PANEL_WIDTH - 1, panelY,                    panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, border);

        // Title
        int titleX = panelX + PANEL_WIDTH / 2;
        int titleY = panelY + PADDING;
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(definition.getTitle()).styled(s -> s.withBold(true)),
                titleX, titleY, 0xFFFFFF);

        // Separator
        int sepY = titleY + textRenderer.fontHeight + 4;
        context.fill(panelX + PADDING, sepY, panelX + PANEL_WIDTH - PADDING, sepY + 1, 0xFF888888);

        // Body text
        int bodyY = sepY + 8;
        int maxWidth = PANEL_WIDTH - PADDING * 2;
        for (var line : textRenderer.wrapLines(Text.literal(definition.getBody()), maxWidth)) {
            context.drawTextWithShadow(textRenderer, line, panelX + PADDING, bodyY, 0xCCCCCC);
            bodyY += textRenderer.fontHeight + 2;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ── Action execution ──────────────────────────────────────────────────────

    private void executeDialogAction(GuiDefinition.DialogAction action) {
        switch (action.type()) {
            case RUN_COMMAND -> {
                closeScreen();
                if (client != null && client.player != null) {
                    String cmd = action.value().startsWith("/")
                            ? action.value().substring(1)
                            : action.value();
                    client.player.networkHandler.sendChatCommand(cmd);
                }
            }
            case CLOSE -> closeScreen();
            case MESSAGE -> {
                if (client != null && client.player != null) {
                    client.player.sendMessage(Text.literal(action.value()), true);
                }
            }
            case OPEN_GUI -> {
                closeScreen();
                if (client != null && client.player != null) {
                    client.player.networkHandler.sendChatCommand(
                            "guiapi open " + action.value());
                }
            }
        }
    }

    private void closeScreen() {
        if (client != null) client.setScreen(null);
    }
}
