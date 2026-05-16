package dev.toolkitmc.guiapi.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parsed representation of a datapack GUI definition.
 *
 * JSON schema — data/<ns>/gui/<name>.json:
 * {
 *   "title": "My GUI",
 *   "rows": 3,
 *   "buttons": [
 *     {
 *       "slot": 4,
 *       "page": 0,                        // optional, default 0
 *       "item": "minecraft:diamond",
 *       "name": "§bClick Me",
 *       "lore": ["§7Line 1"],
 *       "glint": true,                    // enchantment glint effect
 *       "click_type": "any",             // any (default) | left | right | shift
 *       "condition": {                    // optional visibility condition
 *         "type": "has_tag",             // has_tag | score_gt | score_lt | score_eq
 *         "value": "my_tag"             // tag name OR "objective:min:max" for score
 *       },
 *       "actions": [                      // list of actions (executed in order)
 *         {
 *           "type": "run_command",
 *           "value": "/say hi",
 *           "run_with": "player"         // "player" (default) | "console"
 *         },
 *         { "type": "close" }
 *       ]
 *     }
 *   ]
 * }
 *
 * Action types:
 *   run_command  — run a command (run_with: player|console)
 *   close        — close the GUI
 *   open_gui     — open another GUI by id (value: "ns:name")
 *   message      — send chat message to player (value: text)
 *   next_page    — go to next page
 *   prev_page    — go to previous page
 *   goto_page    — go to specific page (value: page index as string)
 *
 * Condition types:
 *   has_tag      — value: tag name. Button visible only if player has tag.
 *   score_gt     — value: "objective:min". Visible if score > min.
 *   score_lt     — value: "objective:max". Visible if score < max.
 *   score_eq     — value: "objective:val". Visible if score == val.
 */
public class GuiDefinition {

    // ── Enums ────────────────────────────────────────────────────────────────

    /**
     * Which mouse button triggers this button's actions.
     *   ANY   — left or right click (default, original behaviour)
     *   LEFT  — only left click
     *   RIGHT — only right click
     *   SHIFT — only shift+left click (QUICK_MOVE)
     */
    public enum ClickType {
        ANY, LEFT, RIGHT, SHIFT;

        public static ClickType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "left"  -> LEFT;
                case "right" -> RIGHT;
                case "shift" -> SHIFT;
                default      -> ANY;
            };
        }
    }

    public enum ActionType {
        RUN_COMMAND, CLOSE, OPEN_GUI, MESSAGE, NEXT_PAGE, PREV_PAGE, GOTO_PAGE;

        public static ActionType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "run_command" -> RUN_COMMAND;
                case "close"       -> CLOSE;
                case "open_gui"    -> OPEN_GUI;
                case "message"     -> MESSAGE;
                case "next_page"   -> NEXT_PAGE;
                case "prev_page"   -> PREV_PAGE;
                case "goto_page"   -> GOTO_PAGE;
                default            -> CLOSE;
            };
        }
    }

    public enum RunWith { PLAYER, CONSOLE;
        public static RunWith fromString(String s) {
            return "console".equalsIgnoreCase(s) ? CONSOLE : PLAYER;
        }
    }

    public enum ConditionType {
        HAS_TAG, SCORE_GT, SCORE_LT, SCORE_EQ;

        public static ConditionType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "has_tag"  -> HAS_TAG;
                case "score_gt" -> SCORE_GT;
                case "score_lt" -> SCORE_LT;
                case "score_eq" -> SCORE_EQ;
                default         -> HAS_TAG;
            };
        }
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record ButtonAction(ActionType type, String value, RunWith runWith) {
        public ButtonAction(ActionType type, String value) {
            this(type, value, RunWith.PLAYER);
        }
    }

    public record ButtonCondition(ConditionType type, String value) {}

    public record Button(
            int slot,
            int page,
            String item,
            String name,
            List<String> lore,
            boolean glint,
            ClickType clickType,
            Optional<ButtonCondition> condition,
            List<ButtonAction> actions
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final Identifier id;
    private final String title;
    private final int rows;
    private final int pageCount;
    private final List<Button> buttons;

    // ── Constructor ──────────────────────────────────────────────────────────

    private GuiDefinition(Identifier id, String title, int rows, List<Button> buttons) {
        this.id       = id;
        this.title    = title;
        this.rows     = rows;
        this.buttons  = buttons;
        this.pageCount = buttons.stream().mapToInt(Button::page).max().orElse(0) + 1;
    }

    // ── Parser ───────────────────────────────────────────────────────────────

    public static GuiDefinition parse(Identifier id, JsonObject obj) {
        String title = obj.has("title") ? obj.get("title").getAsString() : "GUI";
        int rows = obj.has("rows") ? Math.clamp(obj.get("rows").getAsInt(), 1, 6) : 3;

        List<Button> buttons = new ArrayList<>();
        if (obj.has("buttons") && obj.get("buttons").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("buttons")) {
                buttons.add(parseButton(el.getAsJsonObject()));
            }
        }

        return new GuiDefinition(id, title, rows, buttons);
    }

    private static Button parseButton(JsonObject b) {
        int slot  = b.has("slot") ? b.get("slot").getAsInt() : 0;
        int page  = b.has("page") ? Math.max(0, b.get("page").getAsInt()) : 0;
        String item = b.has("item") ? b.get("item").getAsString() : "minecraft:stone";
        String name = b.has("name") ? b.get("name").getAsString() : "";
        boolean glint = b.has("glint") && b.get("glint").getAsBoolean();

        ClickType clickType = b.has("click_type")
                ? ClickType.fromString(b.get("click_type").getAsString())
                : ClickType.ANY;

        List<String> lore = new ArrayList<>();
        if (b.has("lore") && b.get("lore").isJsonArray()) {
            for (JsonElement l : b.getAsJsonArray("lore"))
                lore.add(l.getAsString());
        }

        Optional<ButtonCondition> condition = Optional.empty();
        if (b.has("condition") && b.get("condition").isJsonObject()) {
            JsonObject c = b.getAsJsonObject("condition");
            ConditionType ct = ConditionType.fromString(
                    c.has("type") ? c.get("type").getAsString() : "has_tag");
            String cv = c.has("value") ? c.get("value").getAsString() : "";
            condition = Optional.of(new ButtonCondition(ct, cv));
        }

        List<ButtonAction> actions = new ArrayList<>();

        // Support both legacy "action": {} and new "actions": []
        if (b.has("actions") && b.get("actions").isJsonArray()) {
            for (JsonElement el : b.getAsJsonArray("actions"))
                actions.add(parseAction(el.getAsJsonObject()));
        } else if (b.has("action") && b.get("action").isJsonObject()) {
            actions.add(parseAction(b.getAsJsonObject("action")));
        }

        if (actions.isEmpty()) actions.add(new ButtonAction(ActionType.CLOSE, ""));

        return new Button(slot, page, item, name, lore, glint, clickType, condition, actions);
    }

    private static ButtonAction parseAction(JsonObject a) {
        ActionType type = ActionType.fromString(
                a.has("type") ? a.get("type").getAsString() : "close");
        String value   = a.has("value") ? a.get("value").getAsString() : "";
        RunWith runWith = a.has("run_with")
                ? RunWith.fromString(a.get("run_with").getAsString())
                : RunWith.PLAYER;
        return new ButtonAction(type, value, runWith);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Identifier getId()        { return id; }
    public String getTitle()         { return title; }
    /** Always in [1, 6]. */
    public int getRows()             { return Math.clamp(rows, 1, 6); }
    public int getPageCount()        { return pageCount; }
    public List<Button> getButtons() { return buttons; }

    /** Returns only buttons belonging to the given page. */
    public List<Button> getButtonsForPage(int page) {
        return buttons.stream().filter(b -> b.page() == page).toList();
    }

    @Override
    public String toString() {
        return "GuiDefinition{id=" + id + ", pages=" + pageCount + ", buttons=" + buttons.size() + "}";
    }
}
