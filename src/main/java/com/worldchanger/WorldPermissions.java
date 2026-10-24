package com.worldchanger;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContextKey;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public final class WorldPermissions {
    public static final PermissionDynamicContextKey<String> WORLD_CONTEXT =
            new PermissionDynamicContextKey<>(String.class, "world", value -> value);
    public static final PermissionNode<Boolean> SWITCH = new PermissionNode<>(
            WorldChanger.MOD_ID, "switch", PermissionTypes.BOOLEAN, (player, uuid, context) -> true);
    public static final PermissionNode<Boolean> CREATE = adminCommand("create");
    public static final PermissionNode<Boolean> DELETE = adminCommand("delete");
    public static final PermissionNode<Boolean> LIST = command("list");
    public static final PermissionNode<Boolean> HELP = command("help");
    public static final PermissionNode<Boolean> INFO = command("info");
    public static final PermissionNode<Boolean> SETSPAWN = adminCommand("setspawn");
    public static final PermissionNode<Boolean> WORLD_ACCESS = new PermissionNode<>(
            WorldChanger.MOD_ID, "world", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> true, WORLD_CONTEXT);

    private WorldPermissions() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PermissionGatherEvent.Nodes.class,
                event -> event.addNodes(SWITCH, CREATE, DELETE, LIST, HELP, INFO, SETSPAWN, WORLD_ACCESS));
    }

    public static boolean canSwitch(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? permission(player, SWITCH) : true;
    }

    public static boolean canCreate(CommandSourceStack source) { return can(source, CREATE); }
    public static boolean canDelete(CommandSourceStack source) { return can(source, DELETE); }
    public static boolean canList(CommandSourceStack source) { return can(source, LIST); }
    public static boolean canHelp(CommandSourceStack source) { return can(source, HELP); }
    public static boolean canInfo(CommandSourceStack source) { return can(source, INFO); }
    public static boolean canSetSpawn(CommandSourceStack source) { return can(source, SETSPAWN); }

    public static boolean canAccess(CommandSourceStack source, String worldName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        String dimension = worldName.equals("overworld") ? "minecraft:overworld"
                : WorldChanger.MOD_ID + ":" + worldName;
        try {
            return PermissionAPI.getPermission(player, WORLD_ACCESS, WORLD_CONTEXT.createContext(dimension));
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private static PermissionNode<Boolean> command(String name) {
        return new PermissionNode<>(WorldChanger.MOD_ID, name, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> true);
    }

    private static PermissionNode<Boolean> adminCommand(String name) {
        return new PermissionNode<>(WorldChanger.MOD_ID, name, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && player.hasPermissions(2));
    }

    private static boolean can(CommandSourceStack source, PermissionNode<Boolean> node) {
        return source.getEntity() instanceof ServerPlayer player ? permission(player, node) : true;
    }

    private static boolean permission(ServerPlayer player, PermissionNode<Boolean> node) {
        try {
            return PermissionAPI.getPermission(player, node);
        } catch (RuntimeException ex) {
            return true;
        }
    }
}
