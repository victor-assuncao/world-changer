package com.worldchanger.mixin;

import com.worldchanger.WorldManager;
import com.worldchanger.WorldStatistics;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerStatsCounter.class)
public abstract class ServerStatsCounterMixin extends StatsCounter implements WorldStatistics {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final @Mutable private File file;
    @Shadow @Final private Set<Stat<?>> dirty;
    @Shadow public abstract void save();
    @Shadow public abstract void parseLocal(com.mojang.datafixers.DataFixer fixer, String json);
    @Unique private File worldchanger$vanillaFile;

    @Override
    public void worldchanger$switchWorld(ServerPlayer player) {
        if (worldchanger$vanillaFile == null) worldchanger$vanillaFile = file;
        File destination = WorldManager.isWorldChangerLevel(player.serverLevel())
                ? server.getWorldPath(LevelResource.ROOT).resolve("worldchanger")
                        .resolve(player.serverLevel().dimension().location().getPath())
                        .resolve("stats").resolve(player.getUUID() + ".json").toFile()
                : worldchanger$vanillaFile;
        if (file.equals(destination)) return;

        // Read before changing the live counter: unreadable data must not be overwritten with zeros.
        String json;
        try {
            json = destination.isFile() ? Files.readString(destination.toPath()) : null;
        } catch (IOException failure) {
            throw new IllegalStateException("Nao foi possivel ler as estatisticas de " + destination, failure);
        }
        save();
        Set<Stat<?>> previous = new HashSet<>(stats.keySet());
        file = destination;
        stats.clear();
        dirty.clear();
        if (json != null) parseLocal(server.getFixerUpper(), json);
        dirty.addAll(stats.keySet());
        if (player.connection != null) {
            // The vanilla packet merges values. Zero old keys explicitly to clear the client cache.
            var values = new Object2IntOpenHashMap<Stat<?>>();
            for (Stat<?> stat : previous) values.put(stat, 0);
            values.putAll(stats);
            player.connection.send(new ClientboundAwardStatsPacket(values));
        }
    }
}
