# GUI API — Fabric 1.21.1

A Fabric mod that lets datapacks define and open barrel GUIs or custom dialog screens via JSON files.  
No macros. No external dependencies beyond Fabric API.

---

## Installation

1. Drop `guiapi-1.0.0.jar` into your `mods/` folder.
2. Drop the `example-datapack/` folder (or your own datapack) into `world/datapacks/`.
3. Run `/reload`.

---

## Commands

```
/guiapi open <namespace:id>             — open a GUI for yourself
/guiapi open <namespace:id> <targets>   — open a GUI for target players
/guiapi list                            — list all loaded GUI definitions
```

**Permission level 2** (OP) required.

Calling from a datapack function:

```mcfunction
# data/example/function/open_panel.mcfunction
execute as @a[tag=admin] run guiapi open example:admin_panel @s
```

---

## JSON Schema

GUI definition files go in: `data/<namespace>/gui/<name>.json`

The GUI ID used in commands is `<namespace>:<name>` (matching the file path).

---

### Barrel GUI

Rendered server-side as a chest inventory. **Client mod not required.**

```json
{
  "type": "barrel",
  "title": "§6My GUI",
  "rows": 3,
  "buttons": [
    {
      "slot": 4,
      "item": "minecraft:diamond",
      "name": "§bClick Me",
      "lore": ["§7Does something useful."],
      "action": {
        "type": "run_command",
        "value": "/say hello"
      }
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | `"barrel"` (default) |
| `title` | string | Inventory title. Supports `§` color codes. |
| `rows` | int 1–6 | Number of rows (9 slots each). |
| `buttons` | array | List of slot definitions. |
| `buttons[].slot` | int | Zero-based slot index. |
| `buttons[].item` | string | Item ID, e.g. `minecraft:stone`. |
| `buttons[].name` | string | Display name shown on hover. |
| `buttons[].lore` | string[] | Lore lines shown below the name. |
| `buttons[].action.type` | string | `run_command` · `close` · `open_gui` · `message` |
| `buttons[].action.value` | string | Command string, GUI ID, or message text. |

---

### Dialog GUI

Rendered client-side as a custom screen. **Requires the mod on the client.**

```json
{
  "type": "dialog",
  "title": "§6Welcome",
  "body": "Welcome to the server!\nPlease read the rules before playing.",
  "actions": [
    { "label": "§aAccept", "type": "run_command", "value": "/say I accepted the rules" },
    { "label": "§cDecline", "type": "close", "value": "" }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | `"dialog"` |
| `title` | string | Dialog title bar text. |
| `body` | string | Body text. Supports `\n` for line breaks. |
| `actions` | array | Buttons shown at the bottom of the dialog. |
| `actions[].label` | string | Button label text. |
| `actions[].type` | string | `run_command` · `close` · `open_gui` · `message` |
| `actions[].value` | string | Command string, GUI ID, or message text. |

The server sends an `OpenDialogPayload` packet to the client, which then opens the screen locally.

---

### Action Types

| Type | Behavior |
|------|----------|
| `run_command` | Runs a command as the player (uses the player's own permission level). |
| `close` | Closes the current GUI. |
| `open_gui` | Closes the current GUI and opens another by its ID (`namespace:name`). |
| `message` | Sends a chat message to the player (dialog: shows in action bar). |

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
src/main  (server-side)
 ├── GuiApiMod.java               ModInitializer — registers packets, reload listener, command
 ├── command/GuiCommand.java       /guiapi open|list
 ├── gui/
 │   ├── GuiDefinition.java        JSON data model (parsed on resource reload)
 │   ├── GuiScreenHandler.java     Blocks slot interaction; routes clicks to BarrelGuiHandler
 │   ├── BarrelGuiHandler.java     Opens inventory screens; dispatches button actions
 │   └── OpenDialogPayload.java    S→C network packet carrying a GUI ID
 └── loader/GuiRegistry.java       Loads data/<ns>/gui/*.json from the active datapack set

src/client  (client-side)
 ├── GuiApiClient.java             ClientModInitializer — registers S→C packet handler
 └── screen/DialogScreen.java      Custom Screen implementation for dialog GUIs
```

---

## Known Limitations

- **Dialog GUIs** require the mod installed on the client. Barrel GUIs are server-side only (vanilla-compatible clients work).
- `run_command` actions execute with the **player's own permission level** — the mod does not elevate permissions. If you need elevated execution, use a command block or function tag triggered server-side.
- Minecraft 1.21.1 has no native dialog API. This mod implements its own UI layer on top of Minecraft's `Screen` system. The native dialog API (`/dialog`) was added in 1.21.6.
