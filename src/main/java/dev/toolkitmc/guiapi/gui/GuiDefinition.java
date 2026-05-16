package dev.toolkitmc.guiapi.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed representation of a datapack GUI definition.
 *
 * JSON schema (data/<ns>/gui/<name>.json):
 * <pre>
 * {
 *   "type": "barrel" | "dialog",
 *   "title": "My GUI",
 *   "rows": 3,                    // barrel only (1-6 rows → chest; 1 row default = barrel)
 *   "buttons": [
 *     {
 *       "slot": 0,
 *       "item": "minecraft:diamond",
 *       "name": "Click Me",
 *       "lore": ["Line 1", "Line 2"],
 *       "action": {
 *         "type": "run_command",   // run_command | close | open_gui | message
 *         "value": "/say hello"
 *       }
 *     }
 *   ],
 *   // dialog-only fields:
 *   "body": "Dialog body text",
 *   "actions": [
 *     { "label": "OK", "type": "run_command", "value": "/say ok" }
 *   ]
 * }
 * </pre>
 */
public class GuiDefinition {

    public enum GuiType { BARREL, DIALOG }

    public enum ActionType {
        RUN_COMMAND,
        CLOSE,
        OPEN_GUI,
        MESSAGE;

        public static ActionType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "run_command" -> RUN_COMMAND;
                case "close"       -> CLOSE;
                case "open_gui"    -> OPEN_GUI;
                case "message"     -> MESSAGE;
                default            -> CLOSE;
            };
        }
    }

    public record ButtonAction(ActionType type, String value) {}

    public record Button(
            int slot,
            String item,
            String name,
            List<String> lore,
            ButtonAction action
    ) {}

    public record DialogAction(String label, ActionType type, String value) {}

    // ── Fields ──────────────────────────────────────────────────────────────

    private final Identifier id;
    private final GuiType type;
    private final String title;

    // barrel-specific
    private final int rows;
    private final List<Button> buttons;

    // dialog-specific
    private final String body;
    private final List<DialogAction> dialogActions;

    // ── Constructor (use static parse()) ────────────────────────────────────

    private GuiDefinition(Identifier id, GuiType type, String title,
                          int rows, List<Button> buttons,
                          String body, List<DialogAction> dialogActions) {
        this.id            = id;
        this.type          = type;
        this.title         = title;
        this.rows          = rows;
        this.buttons       = buttons;
        this.body          = body;
        this.dialogActions = dialogActions;
    }

    // ── Static parser ───────────────────────────────────────────────────────

    /**
     * Parse a JsonObject loaded from data/<ns>/gui/<name>.json.
     *
     * @param id  the ResourceLocation computed from the file path
     * @param obj the parsed JSON root object
     * @return a GuiDefinition ready to use
     * @throws IllegalArgumentException on malformed JSON
     */
    public static GuiDefinition parse(Identifier id, JsonObject obj) {
        String typeStr = obj.has("type") ? obj.get("type").getAsString() : "barrel";
        GuiType type = typeStr.equalsIgnoreCase("dialog") ? GuiType.DIALOG : GuiType.BARREL;

        String title = obj.has("title") ? obj.get("title").getAsString() : "GUI";
        int rows = obj.has("rows") ? Math.clamp(obj.get("rows").getAsInt(), 1, 6) : 1;

        // Parse buttons (used by barrel; may be present in dialog too for slot decoration)
        List<Button> buttons = new ArrayList<>();
        if (obj.has("buttons") && obj.get("buttons").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("buttons")) {
                JsonObject b = el.getAsJsonObject();
                int slot = b.has("slot") ? b.get("slot").getAsInt() : 0;
                String item = b.has("item") ? b.get("item").getAsString() : "minecraft:stone";
                String name = b.has("name") ? b.get("name").getAsString() : "";
                List<String> lore = parseLore(b);
                ButtonAction action = parseButtonAction(b);
                buttons.add(new Button(slot, item, name, lore, action));
            }
        }

        // Dialog-specific
        String body = obj.has("body") ? obj.get("body").getAsString() : "";
        List<DialogAction> dialogActions = new ArrayList<>();
        if (obj.has("actions") && obj.get("actions").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("actions")) {
                JsonObject a = el.getAsJsonObject();
                String label = a.has("label") ? a.get("label").getAsString() : "OK";
                ActionType atype = a.has("type")
                        ? ActionType.fromString(a.get("type").getAsString())
                        : ActionType.CLOSE;
                String value = a.has("value") ? a.get("value").getAsString() : "";
                dialogActions.add(new DialogAction(label, atype, value));
            }
        }

        return new GuiDefinition(id, type, title, rows, buttons, body, dialogActions);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static List<String> parseLore(JsonObject b) {
        List<String> lore = new ArrayList<>();
        if (b.has("lore") && b.get("lore").isJsonArray()) {
            for (JsonElement l : b.getAsJsonArray("lore")) {
                lore.add(l.getAsString());
            }
        }
        return lore;
    }

    private static ButtonAction parseButtonAction(JsonObject b) {
        if (!b.has("action") || !b.get("action").isJsonObject()) {
            return new ButtonAction(ActionType.CLOSE, "");
        }
        JsonObject a = b.getAsJsonObject("action");
        ActionType type = ActionType.fromString(
                a.has("type") ? a.get("type").getAsString() : "close"
        );
        String value = a.has("value") ? a.get("value").getAsString() : "";
        return new ButtonAction(type, value);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Identifier getId()                    { return id; }
    public GuiType getType()                     { return type; }
    public String getTitle()                     { return title; }
    public int getRows()                         { return rows; }
    public List<Button> getButtons()             { return buttons; }
    public String getBody()                      { return body; }
    public List<DialogAction> getDialogActions() { return dialogActions; }

    @Override
    public String toString() {
        return "GuiDefinition{id=" + id + ", type=" + type + ", buttons=" + buttons.size() + "}";
    }
}
