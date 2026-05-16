package dev.toolkitmc.guiapi.gui;

import dev.toolkitmc.guiapi.GuiApiMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side chest GUI handler.
 *
 * Features:
 *  - Multi-page support (page tracked per player)
 *  - Conditional buttons (has_tag, score_gt/lt/eq)
 *  - Multiple actions per button (executed in order)
 *  - Enchantment glint on items
 *  - run_command with run_with: player|console
 */
public class BarrelGuiHandler {

    /** Player UUID → currently open GUI state */
    private static final Map<UUID, OpenState> OPEN_GUIS = new HashMap<>();

    private record OpenState(GuiDefinition def, int page) {}

    private BarrelGuiHandler() {}

    // ── Public API ───────────────────────────────────────────────────────────

    public static void open(ServerPlayerEntity player, GuiDefinition def) {
        open(player, def, 0);
    }

    public static void open(ServerPlayerEntity player, GuiDefinition def, int page) {
        page = Math.clamp(page, 0, def.getPageCount() - 1);
        int rows = Math.clamp(def.getRows(), 1, 6);
        int finalPage = page;

        // Register state BEFORE building inventory so that any handleClick call
        // triggered during screen open (edge case) already sees the correct state.
        OPEN_GUIS.put(player.getUuid(), new OpenState(def, page));
        SimpleInventory inv = buildInventory(player, def, page, rows * 9);

        String pageIndicator = def.getPageCount() > 1
                ? " §8[" + (page + 1) + "/" + def.getPageCount() + "]"
                : "";

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal(def.getTitle() + pageIndicator);
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(
                    int syncId, PlayerInventory playerInv, PlayerEntity p) {
                return new GuiScreenHandler(rowsToType(rows), syncId, playerInv, inv, rows, def, finalPage);
            }
        });
    }

    public static boolean handleClick(ServerPlayerEntity player, GuiDefinition def,
                                      int page, int slot, SlotActionType actionType) {
        // Only PICKUP (left/right click) triggers button actions.
        // All other action types (QUICK_MOVE, THROW, CLONE, etc.) are consumed silently
        // to prevent any item manipulation inside the GUI inventory.
        if (actionType != SlotActionType.PICKUP) return true;

        for (GuiDefinition.Button btn : def.getButtonsForPage(page)) {
            if (btn.slot() != slot) continue;
            if (!evaluateCondition(player, btn)) continue; // invisible button, ignore

            for (GuiDefinition.ButtonAction action : btn.actions()) {
                boolean shouldBreak = executeAction(player, def, page, action);
                if (shouldBreak) break; // close/open_gui terminates chain
            }
            return true;
        }
        return true;
    }

    public static void onClose(UUID playerUuid) {
        OPEN_GUIS.remove(playerUuid);
    }

    // ── Inventory builder ────────────────────────────────────────────────────

    private static SimpleInventory buildInventory(ServerPlayerEntity player,
                                                  GuiDefinition def, int page, int size) {
        SimpleInventory inv = new SimpleInventory(size) {
            @Override public boolean canPlayerUse(PlayerEntity p) { return true; }
        };

        for (GuiDefinition.Button btn : def.getButtonsForPage(page)) {
            if (btn.slot() < 0 || btn.slot() >= size) continue;
            if (!evaluateCondition(player, btn)) continue; // hide button
            inv.setStack(btn.slot(), buildStack(btn));
        }

        return inv;
    }

    private static ItemStack buildStack(GuiDefinition.Button btn) {
        Identifier itemId = Identifier.tryParse(btn.item());
        Item item;
        if (itemId != null && Registries.ITEM.containsId(itemId)) {
            item = Registries.ITEM.get(itemId);
        } else {
            GuiApiMod.LOGGER.warn("[GuiAPI] Unknown item '{}' in slot {}, falling back to stone.", btn.item(), btn.slot());
            item = Items.STONE;
        }

        ItemStack stack = new ItemStack(item);

        if (!btn.name().isEmpty()) {
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(btn.name()).styled(s -> s.withItalic(false)));
        }

        if (!btn.lore().isEmpty()) {
            List<Text> loreTexts = btn.lore().stream()
                    .map(l -> (Text) Text.literal(l).styled(s -> s.withItalic(false)))
                    .toList();
            stack.set(DataComponentTypes.LORE, new LoreComponent(loreTexts));
        }

        // Enchantment glint — add a hidden glint flag via custom_data
        if (btn.glint()) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }

        // Mark as GUI item (blocks extraction)
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(new NbtCompound()));

        return stack;
    }

    // ── Condition evaluation ─────────────────────────────────────────────────

    private static boolean evaluateCondition(ServerPlayerEntity player,
                                             GuiDefinition.Button btn) {
        if (btn.condition().isEmpty()) return true;

        GuiDefinition.ButtonCondition cond = btn.condition().get();
        return switch (cond.type()) {
            case HAS_TAG  -> player.getCommandTags().contains(cond.value());
            case SCORE_GT -> getScore(player, cond.value().split(":"), 0) >
                             parseCondInt(cond.value().split(":"), 1);
            case SCORE_LT -> getScore(player, cond.value().split(":"), 0) <
                             parseCondInt(cond.value().split(":"), 1);
            case SCORE_EQ -> getScore(player, cond.value().split(":"), 0) ==
                             parseCondInt(cond.value().split(":"), 1);
        };
    }

    /** value format: "objective:threshold" */
    private static int getScore(ServerPlayerEntity player, String[] parts, int objIndex) {
        if (parts.length < 1) return 0;
        try {
            Scoreboard sb = player.getServer().getScoreboard();
            ScoreboardObjective obj = sb.getNullableObjective(parts[objIndex]);
            if (obj == null) return 0;
            var score = sb.getScore(ScoreHolder.fromName(player.getNameForScoreboard()), obj);
            return score != null ? score.getScore() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parseCondInt(String[] parts, int index) {
        if (parts.length <= index) return 0;
        try { return Integer.parseInt(parts[index]); }
        catch (NumberFormatException e) { return 0; }
    }

    // ── Action execution ─────────────────────────────────────────────────────

    /**
     * Execute a single action.
     * @return true if the action chain should stop (screen was closed/changed)
     */
    private static boolean executeAction(ServerPlayerEntity player, GuiDefinition def,
                                         int currentPage, GuiDefinition.ButtonAction action) {
        MinecraftServer server = player.getServer();
        switch (action.type()) {
            case RUN_COMMAND -> {
                String cmd = action.value().startsWith("/")
                        ? action.value().substring(1) : action.value();
                if (action.runWith() == GuiDefinition.RunWith.CONSOLE) {
                    server.getCommandManager().executeWithPrefix(
                            server.getCommandSource(), cmd);
                } else {
                    server.getCommandManager().executeWithPrefix(
                            player.getCommandSource(), cmd);
                }
            }
            case CLOSE -> {
                player.closeHandledScreen();
                return true;
            }
            case OPEN_GUI -> {
                player.closeHandledScreen();
                Identifier targetId = Identifier.tryParse(action.value());
                if (targetId != null) {
                    dev.toolkitmc.guiapi.loader.GuiRegistry.INSTANCE
                            .get(targetId)
                            .ifPresentOrElse(
                                    target -> open(player, target),
                                    () -> player.sendMessage(
                                            Text.literal("[GuiAPI] GUI not found: " + targetId), false));
                }
                return true;
            }
            case MESSAGE -> player.sendMessage(Text.literal(action.value()), false);
            case NEXT_PAGE -> {
                int next = currentPage + 1;
                if (next < def.getPageCount()) {
                    player.closeHandledScreen();
                    open(player, def, next);
                }
                return true;
            }
            case PREV_PAGE -> {
                int prev = currentPage - 1;
                if (prev >= 0) {
                    player.closeHandledScreen();
                    open(player, def, prev);
                }
                return true;
            }
            case GOTO_PAGE -> {
                try {
                    int target = Integer.parseInt(action.value());
                    if (target >= 0 && target < def.getPageCount()) {
                        player.closeHandledScreen();
                        open(player, def, target);
                    }
                } catch (NumberFormatException ignored) {}
                return true;
            }
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ScreenHandlerType<GenericContainerScreenHandler> rowsToType(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }
}
