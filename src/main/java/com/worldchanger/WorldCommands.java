package com.worldchanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class WorldCommands {
    private WorldCommands() {}

    private static final long DELETE_CONFIRM_TIMEOUT_NANOS = 15_000_000_000L;
    private static final UUID CONSOLE_CONFIRMATION_ID = new UUID(0L, 0L);
    private static final Map<UUID, PendingDelete> PENDING_DELETES = new HashMap<>();

    private record PendingDelete(String worldName, long expiresAtNanos) {}

    private static final SuggestionProvider<CommandSourceStack> WORLD_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(WorldManager.names(), builder);
    private static final SuggestionProvider<CommandSourceStack> TYPES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(WorldType.values()).map(t -> t.name().toLowerCase()), builder);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("wc")
                .executes(WorldCommands::help)
                .then(Commands.literal("help")
                        .requires(WorldPermissions::canHelp)
                        .executes(WorldCommands::help))
                .then(Commands.literal("create")
                        .requires(WorldPermissions::canCreate)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> create(ctx, WorldType.NORMAL))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(TYPES)
                                        .executes(ctx -> create(ctx,
                                                WorldType.parse(StringArgumentType.getString(ctx, "type"))))
                                        .then(Commands.argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                                .executes(ctx -> create(ctx,
                                                        WorldType.parse(StringArgumentType.getString(ctx, "type")),
                                                        com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "seed")))))))
                .then(Commands.literal("import")
                        .requires(WorldPermissions::canImport)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(WorldCommands::importWorld)))
                .then(Commands.literal("delete")
                        .requires(WorldPermissions::canDelete)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(WORLD_NAMES)
                                .executes(WorldCommands::requestDelete)))
                .then(Commands.literal("confirm")
                        .requires(WorldPermissions::canDelete)
                        .executes(WorldCommands::confirmDelete))
                .then(Commands.literal("switch")
                        .requires(WorldPermissions::canSwitch)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(WORLD_NAMES)
                                .executes(WorldCommands::switchWorld)))
                .then(Commands.literal("list")
                        .requires(WorldPermissions::canList)
                        .executes(WorldCommands::list))
                .then(Commands.literal("info")
                        .requires(WorldPermissions::canInfo)
                        .executes(WorldCommands::infoCurrent)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(WORLD_NAMES)
                                .executes(WorldCommands::infoWorld)))
                .then(Commands.literal("setspawn")
                        .requires(WorldPermissions::canSetSpawn)
                        .then(Commands.argument("world", StringArgumentType.word())
                                .suggests(WORLD_NAMES)
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(WorldCommands::setSpawn))))))
                );

    }

    private static int importWorld(CommandContext<CommandSourceStack> ctx) {
        try {
            String name = StringArgumentType.getString(ctx, "name");
            WorldManager.importWorld(ctx.getSource().getServer(), name);
            ctx.getSource().sendSuccess(() -> Component.literal("Mundo importado. Use /wc switch " + name), true);
            return 1;
        } catch (Exception ex) {
            WorldChanger.LOGGER.error("Falha na importacao", ex);
            ctx.getSource().sendFailure(Component.literal(ex.getMessage() == null ? "Falha ao importar; consulte o log." : ex.getMessage()));
            return 0;
        }
    }

    private static int create(CommandContext<CommandSourceStack> ctx, WorldType type) {
        return create(ctx, type, net.minecraft.util.RandomSource.create().nextLong());
    }

    private static int create(CommandContext<CommandSourceStack> ctx, WorldType type, long seed) {
        String name = StringArgumentType.getString(ctx, "name");
        try {
            WorldManager.create(ctx.getSource().getServer(), name, type, seed);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Mundo '" + name + "' criado (" + type.name().toLowerCase() + "). Use /wc switch " + name), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        } catch (Exception e) {
            WorldChanger.LOGGER.error("Erro ao criar mundo '{}'", name, e);
            ctx.getSource().sendFailure(Component.literal("Erro ao criar o mundo. Veja o log do servidor."));
            return 0;
        }
    }

    private static int switchWorld(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        if (!WorldPermissions.canAccess(ctx.getSource(), name)) {
            ctx.getSource().sendFailure(Component.literal("Voce nao tem permissao para acessar este mundo."));
            return 0;
        }
        try {
            WorldManager.switchWorld(ctx.getSource().getServer(), ctx.getSource().getPlayerOrException(), name);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int requestDelete(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack source = ctx.getSource();
        WorldManager.Entry entry = WorldManager.entry(name);
        if (name.equals("overworld") || entry == null || entry.pendingDeletion) {
            source.sendFailure(Component.literal("Mundo personalizado '" + name + "' nao existe ou nao pode ser removido."));
            return 0;
        }
        long now = System.nanoTime();
        PENDING_DELETES.entrySet().removeIf(pendingEntry -> now - pendingEntry.getValue().expiresAtNanos() >= 0);
        UUID owner = confirmationOwner(source);
        PENDING_DELETES.put(owner, new PendingDelete(name, now + DELETE_CONFIRM_TIMEOUT_NANOS));
        source.sendSuccess(() -> Component.literal("Exclusao de '" + name
                + "' solicitada. Execute /wc confirm em ate 15 segundos para confirmar."), false);
        return 1;
    }

    private static int confirmDelete(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        PendingDelete pending = PENDING_DELETES.remove(confirmationOwner(source));
        if (pending == null) {
            source.sendFailure(Component.literal("Nao ha exclusao pendente. Use /wc delete <mundo> primeiro."));
            return 0;
        }
        if (System.nanoTime() - pending.expiresAtNanos() >= 0) {
            source.sendFailure(Component.literal("O pedido de exclusao expirou. Use /wc delete <mundo> novamente."));
            return 0;
        }

        return deleteWorld(ctx, pending.worldName());
    }

    private static UUID confirmationOwner(CommandSourceStack source) {
        net.minecraft.world.entity.Entity entity = source.getEntity();
        return entity == null ? CONSOLE_CONFIRMATION_ID : entity.getUUID();
    }

    private static int deleteWorld(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            boolean filesRemoved = WorldManager.delete(ctx.getSource().getServer(), name);
            if (filesRemoved) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Mundo '" + name + "' foi descarregado e removido do servidor."), true);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Mundo '" + name + "' foi descarregado. Os arquivos estao em uso e serao removidos na proxima inicializacao."), true);
            }
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        } catch (Exception e) {
            WorldChanger.LOGGER.error("Erro ao excluir o mundo '{}'", name, e);
            ctx.getSource().sendFailure(Component.literal(
                    "Nao foi possivel excluir o mundo com seguranca. Veja o log do servidor."));
            return 0;
        }
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        sendHeader(source, "Mundos disponíveis");
        sendLine(source, "overworld", "principal", ChatFormatting.GREEN);
        for (WorldManager.Entry e : WorldManager.entries()) {
            sendLine(source, e.name, e.type.toLowerCase(), ChatFormatting.AQUA);
        }
        sendFooter(source, "Use /wc switch <nome> para trocar de mundo.");
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!WorldPermissions.canHelp(source)) {
            source.sendFailure(Component.literal("Voce nao tem permissao para usar este comando."));
            return 0;
        }
        sendHeader(source, "World Changer");
        sendLine(source, "/wc list", "lista os mundos", ChatFormatting.WHITE);
        sendLine(source, "/wc info [mundo]", "mostra detalhes", ChatFormatting.WHITE);
        sendLine(source, "/wc setspawn <mundo> <x> <y> <z>", "define o spawn", ChatFormatting.WHITE);
        sendLine(source, "/wc switch <mundo>", "troca de mundo", ChatFormatting.WHITE);
        sendLine(source, "/wc create <nome> [tipo] [seed]", "cria um mundo (op)", ChatFormatting.WHITE);
        sendLine(source, "/wc import <nome>", "importa de worlds/<nome> (op)", ChatFormatting.WHITE);
        sendLine(source, "/wc delete <mundo>", "solicita exclusao (op)", ChatFormatting.WHITE);
        sendLine(source, "/wc confirm", "confirma a exclusao em ate 15 segundos", ChatFormatting.WHITE);
        sendFooter(source, "Tipos disponíveis: normal, flat e void.");
        return 1;
    }

    private static int infoCurrent(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String name = player.level().dimension().equals(Level.OVERWORLD)
                    ? "overworld"
                    : player.level().dimension().location().getPath();
            return showInfo(ctx.getSource(), name);
        } catch (CommandSyntaxException e) {
            return showInfo(ctx.getSource(), "overworld");
        }
    }

    private static int infoWorld(CommandContext<CommandSourceStack> ctx) {
        return showInfo(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
    }

    private static int setSpawn(CommandContext<CommandSourceStack> ctx) {
        String world = StringArgumentType.getString(ctx, "world");
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        try {
            WorldManager.setSpawn(ctx.getSource().getServer(), world, new net.minecraft.core.BlockPos(x, y, z), 0f);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Spawn do mundo '" + world + "' definido em " + x + ", " + y + ", " + z + "."), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int showInfo(CommandSourceStack source, String name) {
        if (WorldManager.level(source.getServer(), name) == null) {
            source.sendFailure(Component.literal("Mundo '" + name + "' não existe.").withStyle(ChatFormatting.RED));
            return 0;
        }

        var level = WorldManager.level(source.getServer(), name);
        String type = name.equals("overworld") ? "principal" : WorldManager.entry(name).type.toLowerCase();
        var spawn = level.getSharedSpawnPos();
        sendHeader(source, "Informações do mundo");
        sendLine(source, "Nome", name, ChatFormatting.AQUA);
        sendLine(source, "Tipo", type, ChatFormatting.GREEN);
        if (source.hasPermission(2)) sendLine(source, "Seed", Long.toString(level.getSeed()), ChatFormatting.YELLOW);
        sendLine(source, "Dimensão", level.dimension().location().toString(), ChatFormatting.LIGHT_PURPLE);
        sendLine(source, "Spawn", spawn.getX() + ", " + spawn.getY() + ", " + spawn.getZ(), ChatFormatting.YELLOW);
        sendLine(source, "Jogadores", Integer.toString(level.players().size()), ChatFormatting.WHITE);
        sendFooter(source, "Gamerules: próprias deste mundo nesta etapa.");
        return 1;
    }

    private static void sendHeader(CommandSourceStack source, String title) {
        source.sendSuccess(() -> Component.literal("╭──── ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(title).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" ────────────────╮").withStyle(ChatFormatting.DARK_AQUA)), false);
    }

    private static void sendLine(CommandSourceStack source, String label, String value, ChatFormatting valueColor) {
        source.sendSuccess(() -> Component.literal("│ ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(value).withStyle(valueColor)), false);
    }

    private static void sendFooter(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal("╰──── ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(text).withStyle(ChatFormatting.GRAY)), false);
    }
}
