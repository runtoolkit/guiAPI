package dev.toolkitmc.guiapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.toolkitmc.guiapi.gui.BarrelGuiHandler;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
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
 * /guiapi reload
 * /guiapi help
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

                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) {
                                ctx.getSource().sendError(
                                    Text.literal("[GuiAPI] Must be a player, or specify <targets>."));
                                return 0;
                            }
                            return openGui(ctx, List.of(player));
                        })

                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                            .executes(ctx -> openGui(ctx,
                                    EntityArgumentType.getPlayers(ctx, "targets"))))
                    )
                )

                .then(CommandManager.literal("list")
                    .executes(GuiCommand::listGuis))

                .then(CommandManager.literal("reload")
                    .executes(GuiCommand::reloadGuis))

                .then(CommandManager.literal("help")
                    .executes(GuiCommand::showHelp))
        );
    }

    // ── Subcommand handlers ──────────────────────────────────────────────────

    private static int openGui(CommandContext<ServerCommandSource> ctx,
                               Collection<ServerPlayerEntity> targets) {
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        GuiDefinition def = GuiRegistry.INSTANCE.get(id).orElse(null);
        if (def == null) {
            ctx.getSource().sendError(Text.literal("[GuiAPI] GUI not found: " + id));
            return 0;
        }

        for (ServerPlayerEntity player : targets) {
            BarrelGuiHandler.open(player, def);
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
                sb.append("  ").append(id)
                  .append(" [rows=").append(def.getRows())
                  .append(", pages=").append(def.getPageCount()).append("]\n"));
        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString().trim()), false);
        return all.size();
    }

    private static int reloadGuis(CommandContext<ServerCommandSource> ctx) {
        // Delegates to the server's full resource reload so GuiRegistry.apply()
        // fires through the normal Fabric reload pipeline — same as /reload.
        ctx.getSource().getServer()
                .reloadResources(ctx.getSource().getServer().getDataPackManager().getEnabledIds())
                .thenRun(() -> ctx.getSource().sendFeedback(
                        () -> Text.literal("[GuiAPI] Reload complete. " +
                                GuiRegistry.INSTANCE.getAll().size() + " GUI(s) loaded."),
                        true))
                .exceptionally(ex -> {
                    ctx.getSource().sendError(
                            Text.literal("[GuiAPI] Reload failed: " + ex.getMessage()));
                    return null;
                });
        return 1;
    }

    private static int showHelp(CommandContext<ServerCommandSource> ctx) {
        String help =
                "[GuiAPI] Commands (permission level 2):\n" +
                "  /guiapi open <id> [targets] - Open a GUI for yourself or target players\n" +
                "  /guiapi list               - List all loaded GUI definitions\n" +
                "  /guiapi reload             - Reload all datapack resources (including GUIs)\n" +
                "  /guiapi help               - Show this help message\n" +
                "\n" +
                "Button JSON fields:\n" +
                "  slot, page, item, name, lore, glint\n" +
                "  click_type: any | left | right | shift  (default: any)\n" +
                "  condition:  has_tag | score_gt | score_lt | score_eq\n" +
                "  actions:    run_command | close | open_gui | message\n" +
                "              next_page | prev_page | goto_page";
        ctx.getSource().sendFeedback(() -> Text.literal(help), false);
        return 1;
    }
}
