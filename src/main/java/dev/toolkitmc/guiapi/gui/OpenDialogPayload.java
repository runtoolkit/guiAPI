package dev.toolkitmc.guiapi.gui;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S→C packet: tells the client to open a dialog GUI by its registry ID.
 *
 * The client looks up the GuiDefinition from its own copy of the registry
 * (synced on resource reload) and opens the dialog screen.
 */
public record OpenDialogPayload(Identifier guiId) implements CustomPayload {

    public static final CustomPayload.Id<OpenDialogPayload> ID =
            new CustomPayload.Id<>(Identifier.of("guiapi", "open_dialog"));

    public static final PacketCodec<PacketByteBuf, OpenDialogPayload> CODEC =
            PacketCodecs.STRING
                    .xmap(
                            s -> new OpenDialogPayload(Identifier.of(s)),
                            p -> p.guiId().toString()
                    )
                    .cast();

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
