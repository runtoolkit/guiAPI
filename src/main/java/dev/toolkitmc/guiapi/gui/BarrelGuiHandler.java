package dev.toolkitmc.guiapi.gui;

import dev.toolkitmc.guiapi.GuiApiMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opens a chest/barrel-style GUI populated from a GuiDefinition.
 *
 * Uses GenericContainerScreenHandler (1–6 rows of 9 slots).
 * Slot-click interception happens via the custom screen handler subclass.
 */
public class BarrelGuiHandler {

    // Track which GUI each player has open: player UUID → GuiDefinition
    private static final Map<UUID, GuiDefinition> OPEN_GUIS = new HashMap<>();

    private BarrelGuiHandler() {}

    /**
     * Open the GUI for a player on the server side.
     */
    public static void open(ServerPlayerEntity player, GuiDefinition def) {
        // Clamp rows: GenericContainerScreenHandler supports 1–6 rows (9 slots each)
        int rows = Math.clamp(def.getRows(), 1, 6);
        SimpleInventory inv = buildInventory(def, rows * 9);

        OPEN_GUIS.put(player.getUuid(), def);

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal(def.getTitle());
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(
                    int syncId, PlayerInventory playerInv, PlayerEntity p) {

                return new GuiScreenHandler(
                        rowsToType(rows), syncId, playerInv, inv, rows, def
                );
            }
        });
    }

    /**
     * Called when a player clicks a slot. Returns true if the click was consumed.
     * Dispatched from GuiScreenHandler.
     */
    public static boolean handleClick(ServerPlayerEntity player, GuiDefinition def,
                                      int slot, SlotActionType actionType) {
        if (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE) {
            for (GuiDefinition.Button btn : def.getButtons()) {
                if (btn.slot() == slot) {
                    executeAction(player, def, btn.action());
                    return true;
                }
            }
        }
        return true; // always consume — no item extraction from GUI inventories
    }

    public static void onClose(UUID playerUuid) {
        OPEN_GUIS.remove(playerUuid);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static SimpleInventory buildInventory(GuiDefinition def, int size) {
        SimpleInventory inv = new SimpleInventory(size) {
            @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
        };

        for (GuiDefinition.Button btn : def.getButtons()) {
            if (btn.slot() < 0 || btn.slot() >= size) continue;
            inv.setStack(btn.slot(), buildStack(btn));
        }

        return inv;
    }

    private static ItemStack buildStack(GuiDefinition.Button btn) {
        Identifier itemId = Identifier.tryParse(btn.item());
        Item item = itemId != null
                ? Registries.ITEM.get(itemId)
                : Items.STONE;

        ItemStack stack = new ItemStack(item);

        // Custom name
        if (!btn.name().isEmpty()) {
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(btn.name()).styled(s -> s.withItalic(false)));
        }

        // Lore
        if (!btn.lore().isEmpty()) {
            List<Text> loreTexts = btn.lore().stream()
                    .map(l -> (Text) Text.literal(l).styled(s -> s.withItalic(false)))
                    .toList();
            stack.set(DataComponentTypes.LORE, new LoreComponent(loreTexts));
        }

        // Tag the stack so we can block extraction later
        stack.set(DataComponentTypes.CUSTOM_DATA,
                NbtComponent.of(new net.minecraft.nbt.NbtCompound()));

        return stack;
    }

    private static void executeAction(ServerPlayerEntity player,
                                      GuiDefinition def,
                                      GuiDefinition.ButtonAction action) {
        switch (action.type()) {
            case RUN_COMMAND -> {
                String cmd = action.value().startsWith("/")
                        ? action.value().substring(1)
                        : action.value();
                player.getServer().getCommandManager()
                      .executeWithPrefix(player.getCommandSource(), cmd);
            }
            case CLOSE -> player.closeHandledScreen();
            case OPEN_GUI -> {
                player.closeHandledScreen();
                Identifier targetId = Identifier.tryParse(action.value());
                if (targetId != null) {
                    // Re-open via command so the registry does the lookup
                    dev.toolkitmc.guiapi.loader.GuiRegistry.INSTANCE
                            .get(targetId)
                            .ifPresentOrElse(
                                    target -> open(player, target),
                                    () -> player.sendMessage(
                                            Text.literal("[GuiAPI] GUI not found: " + targetId), false)
                            );
                }
            }
            case MESSAGE -> player.sendMessage(Text.literal(action.value()), false);
        }
    }

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
