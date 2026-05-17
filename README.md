# GUI API — Fabric 1.21.1

A Fabric mod that lets datapacks define and open chest GUIs via JSON files.  
No client mod required. No macros. No external dependencies beyond Fabric API.

---

## Installation

1. Drop `guiapi-1.0.0.jar` into your `mods/` folder.
2. Drop your datapack into `world/datapacks/`.
3. Run `/reload` or `/guiapi reload`.

Optionally install [Mod Menu](https://modrinth.com/mod/modmenu) to see loaded GUIs in the mod list.

---

## Commands

| Command | Description |
|---------|-------------|
| `/guiapi` | Show help (same as `/guiapi help`) |
| `/guiapi open <id>` | Open a GUI for yourself |
| `/guiapi open <id> <targets>` | Open a GUI for target players |
| `/guiapi list` | List all loaded GUI definitions |
| `/guiapi reload` | Reload all datapack resources (including GUIs) |
| `/guiapi help` | Show command and JSON field reference in-game |

**Permission level 2** (OP) required.

Calling from a datapack function:

```mcfunction
# data/example/function/open_panel.mcfunction
execute as @a[tag=admin] run guiapi open example:admin_panel @s
```

---

## File Location

GUI definition files go in:

```
data/<namespace>/gui/<name>.json
```

The GUI ID used in commands is `<namespace>:<name>` — matching the file path under `gui/`.

---

## JSON Schema

### Top-level fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `title` | string | `"GUI"` | Inventory title. Supports `§` color codes and placeholders. |
| `rows` | int 1–6 | `3` | Number of rows (9 slots each). |
| `on_open` | action[] | `[]` | Actions executed when the GUI is opened. |
| `on_close` | action[] | `[]` | Actions executed when the GUI is closed (any reason). |
| `buttons` | button[] | `[]` | List of button definitions. |

### Button fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `slot` | int | `0` | Zero-based slot index (0–`rows*9-1`). |
| `page` | int | `0` | Which page this button appears on. |
| `item` | string | `"minecraft:stone"` | Item ID. |
| `name` | string | `""` | Display name. Supports color codes and placeholders. |
| `lore` | string[] | `[]` | Lore lines. Supports placeholders. |
| `glint` | boolean | `false` | Apply enchantment glint effect. |
| `click_type` | string | `"any"` | Which click triggers actions: `any` · `left` · `right` · `shift` |
| `condition` | object | — | Visibility condition (see below). |
| `actions` | action[] | `[close]` | Actions executed in order on click. |
| `toggle` | object | — | Toggle definition — replaces `item`/`actions` (see below). |

> **Legacy:** A single `"action": {}` object is still accepted.

### Placeholders

Supported in `title`, button `name`, `lore`, `message` values, and `run_command` values.

| Placeholder | Resolves to |
|-------------|-------------|
| `{player}` | Player's display name |
| `{gui}` | GUI ID (`namespace:name`) |
| `{page}` | Current page index (0-based) |
| `{page1}` | Current page index (1-based) |
| `{pages}` | Total page count |
| `{score:objective}` | Player's score in the given scoreboard objective |

### `click_type` values

| Value | Triggers on |
|-------|-------------|
| `any` | Left click, right click, or shift+click (default) |
| `left` | Left click only |
| `right` | Right click only |
| `shift` | Shift+left click only |

### Action types

| Type | `value` field | `run_with` | Description |
|------|--------------|------------|-------------|
| `run_command` | Command string | `player` · `console` | Run a command. Default: player. Supports placeholders. |
| `close` | — | — | Close the GUI. |
| `open_gui` | `namespace:name` | — | Close and open another GUI. |
| `message` | Text string | — | Send a chat message to the player. Supports placeholders. |
| `next_page` | — | — | Go to the next page. |
| `prev_page` | — | — | Go to the previous page. |
| `goto_page` | Page index (string) | — | Jump to a specific page. |

Multiple actions are executed in order. `close`, `open_gui`, `next_page`, `prev_page`, and `goto_page` stop the chain after executing.

### Condition types

Conditions control button **visibility**. Hidden buttons cannot be clicked.

| Type | `value` format | Visible when |
|------|---------------|--------------|
| `has_tag` | Tag name | Player has the scoreboard tag |
| `not_tag` | Tag name | Player does **not** have the scoreboard tag |
| `score_gt` | `"objective:threshold"` | Player's score > threshold |
| `score_lt` | `"objective:threshold"` | Player's score < threshold |
| `score_eq` | `"objective:value"` | Player's score == value |

### Toggle buttons

A toggle button shows different item/name/lore/actions depending on a scoreboard tag on the player. Replace the `item` and `actions` fields with a `toggle` object.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `tag` | string | `""` | Scoreboard tag that stores the on/off state. |
| `item_on` / `item_off` | string | lime/gray dye | Item shown in each state. |
| `name_on` / `name_off` | string | `§aEnabled` / `§7Disabled` | Display name in each state. |
| `lore_on` / `lore_off` | string[] | `[]` | Lore in each state. |
| `glint_on` / `glint_off` | boolean | `false` | Glint in each state. |
| `actions_on` | action[] | `[tag @s remove <tag>]` | Actions when clicking while ON. Default removes the tag. |
| `actions_off` | action[] | `[tag @s add <tag>]` | Actions when clicking while OFF. Default adds the tag. |

The default `actions_on`/`actions_off` use `run_with: console` and handle the tag automatically — you only need to specify them if you want additional side effects.

---

## Examples

### Minimal button

```json
{
  "title": "§6My GUI",
  "rows": 3,
  "buttons": [
    {
      "slot": 13,
      "item": "minecraft:diamond",
      "name": "§bClick Me",
      "lore": ["§7Does something useful."],
      "actions": [
        { "type": "run_command", "value": "say hello" },
        { "type": "close" }
      ]
    }
  ]
}
```

### Placeholder in name and lore

```json
{
  "slot": 4,
  "item": "minecraft:player_head",
  "name": "§6{player}",
  "lore": [
    "§7Page {page1}/{pages}",
    "§7Coins: §e{score:coins}"
  ]
}
```

### on_open and on_close hooks

```json
{
  "title": "§aShop",
  "rows": 3,
  "on_open":  [{ "type": "run_command", "value": "tag @s add in_shop", "run_with": "console" }],
  "on_close": [{ "type": "run_command", "value": "tag @s remove in_shop", "run_with": "console" }],
  "buttons": []
}
```

### Toggle button

```json
{
  "slot": 4,
  "toggle": {
    "tag": "notifications_on",
    "item_on":   "minecraft:bell",
    "item_off":  "minecraft:barrier",
    "name_on":   "§aNotifications: ON",
    "name_off":  "§cNotifications: OFF",
    "lore_on":   ["§7Click to disable."],
    "lore_off":  ["§7Click to enable."]
  }
}
```

### Left-click vs right-click on the same slot

```json
{
  "slot": 13, "item": "minecraft:paper", "name": "§fInteract",
  "click_type": "left",
  "actions": [{ "type": "message", "value": "§aLeft!" }]
},
{
  "slot": 13, "item": "minecraft:paper", "name": "§fInteract",
  "click_type": "right",
  "actions": [{ "type": "message", "value": "§eRight!" }]
}
```

> Two buttons can share a slot with different `click_type` values. The first matching one wins.

### Conditional button (tag-gated)

```json
{
  "slot": 4,
  "item": "minecraft:nether_star",
  "name": "§6Admin Only",
  "condition": { "type": "has_tag", "value": "admin" },
  "actions": [{ "type": "open_gui", "value": "example:admin_panel" }]
}
```

### Console command

```json
{
  "slot": 8,
  "item": "minecraft:command_block",
  "name": "§cGive OP Items",
  "condition": { "type": "has_tag", "value": "admin" },
  "actions": [
    { "type": "run_command", "value": "give @s minecraft:netherite_sword", "run_with": "console" },
    { "type": "close" }
  ]
}
```

---

## Building

```bash
./gradlew build
# Output: build/libs/guiapi-1.0.0.jar
```

Requires **Java 21**.

---

## Architecture

```
src/main/java/dev/toolkitmc/guiapi/
 ├── GuiApiMod.java                   ModInitializer — registers reload listener and command
 ├── command/
 │   └── GuiCommand.java              /guiapi open|list|reload|help  (bare /guiapi → help)
 ├── gui/
 │   ├── GuiDefinition.java           JSON model: title, rows, buttons, toggle, on_open/close
 │   ├── GuiScreenHandler.java        Extends GenericContainerScreenHandler; blocks interaction
 │   └── BarrelGuiHandler.java        Opens screens; evaluates conditions; resolves placeholders;
 │                                    dispatches actions; fires on_open/on_close hooks
 ├── loader/
 │   └── GuiRegistry.java             Loads data/<ns>/gui/*.json on resource reload
 └── modmenu/
     └── GuiApiModMenuEntry.java      Optional Mod Menu info screen
```

---

## Security

See [SECURITY.md](SECURITY.md).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

MIT — see [LICENSE](LICENSE).
