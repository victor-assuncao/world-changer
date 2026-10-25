package com.worldchanger;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;

/** Exercises the command dispatcher, real teleports and packets sent to a vanilla client. */
final class WorldProgressIntegration {
    static void run(MinecraftServer server) throws Exception {
        var dispatcher = server.getCommands().getDispatcher();
        var console = server.createCommandSourceStack();
        String exact = "wc create flatulencia normal 324792834";
        check(!dispatcher.parse(exact, console).getReader().canRead(), "seed command from screenshot parses");
        if (WorldManager.entry("flatulencia") == null) check(dispatcher.execute(exact, console) == 1, "seed command succeeds");
        check(WorldManager.level(server, "flatulencia").getSeed() == 324792834L, "command seed reaches generator");
        check(WorldManager.entry("flatulencia").seed == 324792834L, "command seed persisted");
        for (String value : new String[]{"-9223372036854775808", "9223372036854775807"})
            check(!dispatcher.parse("wc create syntax normal " + value, console).getReader().canRead(), "64 bit seeds");
        if (WorldManager.entry("progress_a") == null) dispatcher.execute("wc create progress_a void 7349", console);
        if (WorldManager.entry("progress_b") == null) dispatcher.execute("wc create progress_b void -8732", console);

        var marker = server.getWorldPath(LevelResource.ROOT).resolve("command-progress-passed.txt");
        boolean restart = Files.exists(marker);
        GameProfile profile = new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000456"), "ProgressTest");
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        RecordingConnection connection = new RecordingConnection();
        new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        Stat<?> jumps = Stats.CUSTOM.get(Stats.JUMP);
        Stat<?> deaths = Stats.CUSTOM.get(Stats.DEATHS);
        AdvancementHolder advancement = server.getAdvancements().get(ResourceLocation.withDefaultNamespace("adventure/adventuring_time"));
        check(advancement != null, "reward advancement found");
        player.getStats().setValue(player, jumps, 11);
        player.getStats().setValue(player, deaths, 2);
        switchCommand(server, player, "progress_a");
        check(connection.values.getInt(deaths) == 0, "client removes vanilla-only statistic");
        if (restart) {
            check(player.getStats().getValue(jumps) == 73, "stats survive server restart");
            check(player.getAdvancements().getOrStartProgress(advancement).isDone(), "command advancement survives restart");
        } else {
            check(player.getStats().getValue(jumps) == 0, "new world's statistics start at zero");
            check(!player.getAdvancements().getOrStartProgress(advancement).isDone(), "new world has no global advancement");
        }
        player.getStats().setValue(player, jumps, 73);
        for (String criterion : advancement.value().criteria().keySet()) player.getAdvancements().award(advancement, criterion);
        int earned = player.totalExperience;
        check(earned > 0, "world-specific advancement reward");
        switchCommand(server, player, "progress_b");
        check(player.getStats().getValue(jumps) == 0 && connection.values.getInt(jumps) == 0, "server and client statistics isolated");
        check(!player.getAdvancements().getOrStartProgress(advancement).isDone(), "command switches advancement tracker");
        check(connection.resets > 0, "advancement reset sent to client");
        switchCommand(server, player, "overworld");
        check(player.getStats().getValue(jumps) == 11 && player.getStats().getValue(deaths) == 2, "vanilla statistics restored");
        check(connection.values.getInt(jumps) == 11, "vanilla client statistics restored");
        check(!player.getAdvancements().getOrStartProgress(advancement).isDone(), "vanilla advancement unmodified");
        switchCommand(server, player, "progress_a");
        check(player.getStats().getValue(jumps) == 73 && connection.values.getInt(jumps) == 73, "world statistics restored");
        check(player.getAdvancements().getOrStartProgress(advancement).isDone(), "world advancement restored");
        check(player.totalExperience == earned, "world reward XP restored without replay");
        for (String criterion : advancement.value().criteria().keySet()) player.getAdvancements().award(advancement, criterion);
        check(player.totalExperience == earned, "no duplicate reward after return");
        player.getStats().save();
        player.getAdvancements().save();
        var fresh = new net.minecraft.stats.ServerStatsCounter(server,
                server.getWorldPath(LevelResource.ROOT).resolve("worldchanger/progress_a/stats/" + profile.getId() + ".json").toFile());
        check(fresh.getValue(jumps) == 73, "fresh stats counter reloads saved values");
        switchCommand(server, player, "overworld");
        player.getAdvancements().stopListening();
        server.overworld().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        Files.writeString(marker, restart ? "RESTART PASS" : "FIRST PASS");
        WorldChanger.LOGGER.info("WORLDCHANGER COMMAND/CLIENT/STATS {} PASS", restart ? "RESTART" : "FIRST");
    }

    private static void switchCommand(MinecraftServer server, ServerPlayer player, String name) throws Exception {
        check(server.getCommands().getDispatcher().execute("wc switch " + name, player.createCommandSourceStack()) == 1,
                "switch command " + name);
        check(WorldManager.level(server, name) == player.serverLevel(), "actual teleport " + name);
    }

    private static final class RecordingConnection extends Connection {
        private final io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel();
        private final Object2IntOpenHashMap<Stat<?>> values = new Object2IntOpenHashMap<>();
        private int resets;
        RecordingConnection() { super(PacketFlow.SERVERBOUND); }

        @Override public io.netty.channel.Channel channel() { return channel; }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
            if (packet instanceof ClientboundAwardStatsPacket stats) values.putAll(stats.stats());
            if (packet instanceof ClientboundUpdateAdvancementsPacket advancement && advancement.shouldReset()) resets++;
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
