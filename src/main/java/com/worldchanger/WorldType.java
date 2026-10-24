package com.worldchanger;

import java.util.Optional;
import java.util.List;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

public enum WorldType {
    NORMAL, FLAT, VOID;

    public static WorldType parse(String s) {
        for (WorldType t : values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        throw new IllegalArgumentException("Tipo invalido: " + s + " (use normal, flat ou void)");
    }

    public ChunkGenerator createGenerator(MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        switch (this) {
            case FLAT: {
                FlatLevelGeneratorSettings settings = FlatLevelGeneratorSettings.getDefault(
                        access.lookupOrThrow(Registries.BIOME),
                        access.lookupOrThrow(Registries.STRUCTURE_SET),
                        access.lookupOrThrow(Registries.PLACED_FEATURE));
                return new FlatLevelSource(settings);
            }
            case VOID: {
                Holder<Biome> voidBiome = access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
                FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                        Optional.of(HolderSet.direct()), voidBiome, List.of());
                settings.updateLayers();
                return new FlatLevelSource(settings);
            }
            default: {
                // O proprio gerador do overworld e a referencia mais fiel ao
                // comportamento vanilla da versao em execucao.
                return server.overworld().getChunkSource().getGenerator();
            }
        }
    }
}
