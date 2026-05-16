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
| `title` | string | `"GUI"` | Inventory title. Supports `§` color codes. |
| `rows` | int 1–6 | `3` | Number of rows (9 slots each). |
| `buttons` | array | `[]` | List of button definitions. |

### Button fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `slot` | int | `0` | Zero-based slot index (0–`rows*9-1`). |
| `page` | int | `0` | Which page this button appears on. |
| `item` | string | `"minecraft:stone"` | Item ID, e.g. `minecraft:diamond`. |
| `name` | string | `""` | Display name. Supports `§` color codes. |
| `lore` | string[] | `[]` | Lore lines shown below the name. |
| `glint` | boolean | `false` | Apply enchantment glint effect. |
| `click_type` | string | `"any"` | Which click triggers actions: `any` · `left` · `right` · `shift` |
| `condition` | object | — | Visibility condition (see below). |
| `actions` | array | `[close]` | Actions executed in order on click (see below). |

> **Legacy:** A single `"action": {}` object is still accepted for backwards compatibility.

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
| `run_command` | Command string (with or without leading `/`) | `player` · `console` | Run a command. Default executor: player. |
| `close` | — | — | Close the GUI. |
| `open_gui` | `namespace:name` | — | Close and open another GUI. |
| `message` | Text string | — | Send a chat message to the player. |
| `next_page` | — | — | Go to the next page. |
| `prev_page` | — | — | Go to the previous page. |
| `goto_page` | Page index (string) | — | Jump to a specific page. |

Multiple actions are executed in order. `close`, `open_gui`, `next_page`, `prev_page`, and `goto_page` stop the chain after executing.

### Condition types

Conditions control button **visibility**. Hidden buttons cannot be clicked.

| Type | `value` format | Visible when |
|------|---------------|--------------|
| `has_tag` | Tag name | Player has the scoreboard tag |
| `score_gt` | `"objective:threshold"` | Player's score > threshold |
| `score_lt` | `"objective:threshold"` | Player's score < threshold |
| `score_eq` | `"objective:value"` | Player's score == value |

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

### Right-click vs left-click on the same slot

```json
{
  "slot": 13,
  "item": "minecraft:paper",
  "name": "§7Left or Right Click",
  "click_type": "left",
  "actions": [{ "type": "message", "value": "§aYou left-clicked!" }]
},
{
  "slot": 13,
  "item": "minecraft:paper",
  "name": "§7Left or Right Click",
  "click_type": "right",
  "actions": [{ "type": "message", "value": "§eYou right-clicked!" }]
}
```

> Two buttons can share a slot if they have different `click_type` values. The first matching one wins.

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

### Multi-page GUI

```json
{
  "title": "§9Item Shop",
  "rows": 4,
  "buttons": [
    { "slot": 0,  "page": 0, "item": "minecraft:apple",    "name": "§aApple",    "actions": [{ "type": "run_command", "value": "give @s minecraft:apple" }] },
    { "slot": 1,  "page": 0, "item": "minecraft:bread",    "name": "§aBread",    "actions": [{ "type": "run_command", "value": "give @s minecraft:bread" }] },
    { "slot": 26,  "page": 0, "item": "minecraft:arrow",   "name": "§7Next →",   "actions": [{ "type": "next_page" }] },
    { "slot": 0,  "page": 1, "item": "minecraft:diamond",  "name": "§bDiamond",  "actions": [{ "type": "run_command", "value": "give @s minecraft:diamond" }] },
    { "slot": 27, "page": 1, "item": "minecraft:arrow",    "name": "§7← Back",   "actions": [{ "type": "prev_page" }] }
  ]
}
```

### Console command (elevated execution)

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
 │   └── GuiCommand.java              /guiapi open|list|reload|help
 ├── gui/
 │   ├── GuiDefinition.java           JSON data model (title, rows, buttons, actions, conditions)
 │   ├── GuiScreenHandler.java        Extends GenericContainerScreenHandler; blocks slot interaction
 │   └── BarrelGuiHandler.java        Opens inventory screens; evaluates conditions; dispatches actions
 ├── loader/
 │   └── GuiRegistry.java             Loads data/<ns>/gui/*.json on resource reload
 └── modmenu/
     └── GuiApiModMenuEntry.java      Optional Mod Menu info screen (compile-only dependency)
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
