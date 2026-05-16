package dev.toolkitmc.guiapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.toolkitmc.guiapi.gui.BarrelGuiHandler;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import dev.toolkitmc.guiapi.gui.OpenDialogPayload;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;

/**
 * /guiapi open <namespace:id> [<targets>]
 * /guiapi list
 *
 * Permission level 2 required.
 */
public class GuiCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(
            CommandManager.literal("guiapi")
                .requires(src -> src.hasPermissionLevel(2))

                .then(CommandManager.literal("open")
                    .then(CommandManager.argument("id", IdentifierArgumentType.identifier())
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemainingLowerCase();
                            GuiRegistry.INSTANCE.getAll().keySet().stream()
                                    .map(Identifier::toString)
                                    .filter(s -> s.contains(input))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })

                        // /guiapi open <id>  — self
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) {
                                ctx.getSource().sendError(
                                    Text.literal("[GuiAPI] Must be a player, or specify <targets>."));
                                return 0;
                            }
                            return openGui(ctx, List.of(player));
                        })

                        // /guiapi open <id> <targets>
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                            .executes(ctx -> openGui(ctx,
                                    EntityArgumentType.getPlayers(ctx, "targets"))))
                    )
                )

                .then(CommandManager.literal("list")
                    .executes(GuiCommand::listGuis))
        );
    }

    private static int openGui(CommandContext<ServerCommandSource> ctx,
                               Collection<ServerPlayerEntity> targets) {
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        GuiDefinition def = GuiRegistry.INSTANCE.get(id).orElse(null);
        if (def == null) {
            ctx.getSource().sendError(Text.literal("[GuiAPI] GUI not found: " + id));
            return 0;
        }

        for (ServerPlayerEntity player : targets) {
            switch (def.getType()) {
                case BARREL -> BarrelGuiHandler.open(player, def);
                case DIALOG -> ServerPlayNetworking.send(player, new OpenDialogPayload(def.getId()));
            }
        }

        ctx.getSource().sendFeedback(
                () -> Text.literal("[GuiAPI] Opened '" + id + "' for " + targets.size() + " player(s)."),
                false);
        return targets.size();
    }

    private static int listGuis(CommandContext<ServerCommandSource> ctx) {
        var all = GuiRegistry.INSTANCE.getAll();
        if (all.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("[GuiAPI] No GUIs loaded."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[GuiAPI] Loaded GUIs (" + all.size() + "):\n");
        all.forEach((id, def) ->
                sb.append("  ").append(id).append(" [").append(def.getType()).append("]\n"));
        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString().trim()), false);
        return all.size();
    }
}
