package com.worldchanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/** Applies only to the vanilla tree captured before mod command registration. */
public final class VanillaPermissions {
    private static final Field REQUIREMENT = requirementField();
    private static final ThreadLocal<Boolean> DEFAULT = new ThreadLocal<>();
    private static final Map<String, PermissionNode<Boolean>> NODES = new LinkedHashMap<>();
    static {
        for (String command : ("advancement attribute execute bossbar clear clone damage data datapack debug "
                + "defaultgamemode difficulty effect me enchant experience fill fillbiome forceload function "
                + "gamemode gamerule give help item kick kill list locate loot msg particle place playsound "
                + "random reload recipe return ride say schedule scoreboard seed setblock spawnpoint "
                + "setworldspawn spectate spreadplayers stopsound summon tag team teammsg teleport tellraw "
                + "tick time title trigger weather worldborder jfr ban-ip banlist ban deop op pardon pardon-ip "
                + "perf save-all save-off save-on setidletimeout stop transfer whitelist publish").split(" ")) {
            NODES.put(command, new PermissionNode<>(WorldChanger.MOD_ID, "vanilla." + command,
                    PermissionTypes.BOOLEAN, (player, uuid, contexts) -> Boolean.TRUE.equals(DEFAULT.get()),
                    WorldPermissions.WORLD_CONTEXT));
        }
    }
    private VanillaPermissions() {}

    public static void gather(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(PermissionNode<?>[]::new));
    }

    private static Field requirementField() {
        try {
            // Brigadier is loaded by MC-BOOTSTRAP and cannot be targeted by game mixins.
            // Keep the original dispatcher/nodes: vanilla callbacks capture these objects.
            Field field = CommandNode.class.getDeclaredField("requirement");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static void capture(CommandDispatcher<CommandSourceStack> dispatcher) {
        Set<CommandNode<CommandSourceStack>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var node : dispatcher.getRoot().getChildren()) {
            String command = node.getRedirect() != null ? node.getRedirect().getName() : node.getName();
            if (NODES.containsKey(command)) mark(node, command, visited);
        }
    }

    private static void mark(CommandNode<CommandSourceStack> node, String command,
            Set<CommandNode<CommandSourceStack>> visited) {
        if (!visited.add(node)) return;
        Predicate<CommandSourceStack> original = node.getRequirement();
        Predicate<CommandSourceStack> wrapped = source -> test(command, original, source);
        try {
            REQUIREMENT.set(node, wrapped);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot install vanilla command permissions", ex);
        }
        // Do not follow redirects: execute/run returns to the dispatcher root.
        for (var child : node.getChildren()) mark(child, command, visited);
    }
    public static boolean test(String command, Predicate<CommandSourceStack> predicate, CommandSourceStack source) {
        if (command == null || !(source.getEntity() instanceof ServerPlayer player)) return predicate.test(source);
        boolean original = predicate.test(source);
        Boolean previousDefault = DEFAULT.get();
        try {
            DEFAULT.set(original);
            // Resolve against the player's actual world, even for /execute in ... .
            boolean allowed = PermissionAPI.getPermission(player, NODES.get(command),
                    WorldPermissions.WORLD_CONTEXT.createContext(player.serverLevel().dimension().location().toString()));
            if (!allowed) return false;
            // Elevate only the predicate input; execution retains the original source.
            return predicate.test(source.withPermission(4));
        } catch (RuntimeException ex) {
            // Provider failures must never grant administrative access, including to OPs.
            return false;
        } finally {
            if (previousDefault == null) DEFAULT.remove(); else DEFAULT.set(previousDefault);
        }
    }
}
