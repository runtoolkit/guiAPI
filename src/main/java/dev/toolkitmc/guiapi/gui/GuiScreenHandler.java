package dev.toolkitmc.guiapi.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Custom GenericContainerScreenHandler that intercepts all slot actions
 * and routes them to BarrelGuiHandler instead of allowing item movement.
 */
public class GuiScreenHandler extends GenericContainerScreenHandler {

    private final GuiDefinition definition;

    public GuiScreenHandler(ScreenHandlerType<?> type, int syncId,
                            PlayerInventory playerInv, Inventory inv,
                            int rows, GuiDefinition definition) {
        super(type, syncId, playerInv, inv, rows);
        this.definition = definition;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Block all inventory interaction — GUI slots are read-only.
        // Only fire our action handler if the click is in the GUI area (not player inv).
        if (slotIndex >= 0 && slotIndex < definition.getRows() * 9) {
            if (player instanceof ServerPlayerEntity sp) {
                BarrelGuiHandler.handleClick(sp, definition, slotIndex, actionType);
            }
            // Do NOT call super — prevents item pickup/swap
            return;
        }
        // Clicks in player inventory area: also block to prevent shift-click item transfer
        // super.onSlotClick(slotIndex, button, actionType, player); // intentionally omitted
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY; // Disable shift-click
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        BarrelGuiHandler.onClose(player.getUuid());
    }
}
