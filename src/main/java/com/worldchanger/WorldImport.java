package com.worldchanger;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;

/** Strict import validation: never substitutes a generator when decoding fails. */
public final class WorldImport {
    private WorldImport() {}
    public record Settings(CompoundTag data, LevelStem stem, WorldOptions options) {}
    public static final ThreadLocal<WorldOptions> CONSTRUCTING = new ThreadLocal<>();

    public static Path directory(MinecraftServer server) throws IOException {
        Path root = server.getServerDirectory().toAbsolutePath().normalize().resolve("worlds");
        if (Files.isSymbolicLink(root)) throw new IOException("A pasta worlds nao pode ser um link simbolico.");
        Files.createDirectories(root);
        return root.toRealPath();
    }

    public static Path source(MinecraftServer server, String folder) throws IOException {
        if (folder.isBlank() || folder.equals(".") || folder.equals("..")
                || folder.contains("/") || folder.contains("\\") || folder.contains(":"))
            throw new IOException("Informe apenas o nome de uma pasta dentro de worlds, sem caminho absoluto ou subpastas.");
        Path root = directory(server);
        Path candidate = root.resolve(folder).normalize();
        if (!root.equals(candidate.getParent()) || Files.isSymbolicLink(candidate))
            throw new IOException("A origem deve ser uma pasta diretamente dentro de worlds, sem links simbolicos.");
        if (!Files.isDirectory(candidate)) throw new IOException("Pasta de mundo nao encontrada: " + candidate);
        Path real = candidate.toRealPath();
        if (!root.equals(real.getParent())) throw new IOException("A origem esta fora da pasta worlds.");
        return real;
    }

    public static Settings read(MinecraftServer server, Path folder) throws IOException {
        CompoundTag root = NbtIo.readCompressed(folder.resolve("level.dat"), NbtAccounter.create(64L * 1024 * 1024));
        if (!root.contains("Data", Tag.TAG_COMPOUND)) throw new IOException("level.dat sem Data.");
        CompoundTag data = root.getCompound("Data");
        for (Tag modTag : root.getCompound("fml").getList("LoadingModList", Tag.TAG_COMPOUND)) {
            String id = ((CompoundTag) modTag).getString("ModId");
            // LoadingModList is historical metadata, not a dependency declaration.
            // Utility mods may have been removed without contributing world content.
            if (!net.neoforged.fml.ModList.get().isLoaded(id)) {
                WorldChanger.LOGGER.warn("O save em '{}' foi salvo anteriormente com o mod '{}', que nao esta instalado. "
                        + "A lista historica nao bloqueia a importacao; registros de geracao serao validados separadamente.", folder, id);
            }
        }
        if (data.getInt("DataVersion") != net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion())
            throw new IOException("Versao do mundo diferente. Converta uma copia no Minecraft desta versao antes de importar.");
        var enabled = server.getPackRepository().getSelectedIds();
        for (Tag pack : data.getCompound("DataPacks").getList("Enabled", Tag.TAG_STRING)) {
            // All managed levels use the server's active resources. Saved pack IDs
            // describe the source installation and need not match this repository.
            if (!enabled.contains(pack.getAsString())) {
                WorldChanger.LOGGER.warn("Datapack historico '{}' do save em '{}' nao esta ativo com esse ID. "
                        + "O mundo usara os datapacks compartilhados do servidor; a geracao sera validada nos registros ativos.",
                        pack.getAsString(), folder);
            }
        }
        CompoundTag generation = data.getCompound("WorldGenSettings");
        if (!generation.contains("seed", Tag.TAG_LONG) || !generation.getCompound("dimensions").contains("minecraft:overworld"))
            throw new IOException("WorldGenSettings incompleto; geracao original nao pode ser recuperada.");
        try {
            var ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
            // Validate every saved dimension, including custom mod generators and registry references.
            LevelStem overworld = null;
            CompoundTag dimensions = generation.getCompound("dimensions");
            for (String id : dimensions.getAllKeys()) {
                LevelStem stem = LevelStem.CODEC.parse(ops, dimensions.get(id)).getOrThrow();
                if (id.equals("minecraft:overworld")) overworld = stem;
            }
            WorldOptions options = WorldOptions.CODEC.codec().parse(NbtOps.INSTANCE, generation).getOrThrow();
            return new Settings(data, Objects.requireNonNull(overworld), options);
        } catch (RuntimeException ex) {
            throw new IOException("Registro, mod ou configuracao de geracao indisponivel: " + ex.getMessage(), ex);
        }
    }

    /** Copies only from an unlocked save. Does not create or modify source lock files. */
    public static void copy(MinecraftServer server, Path source, Path target) throws IOException {
        Path real = source.toRealPath();
        if (!Files.isRegularFile(real.resolve("session.lock")))
            throw new IOException("Falta session.lock: use uma copia completa de um mundo salvo e fechado.");
        try (FileChannel channel = FileChannel.open(real.resolve("session.lock"), StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Mundo de origem em uso. Pare o servidor de origem ou use um backup consistente.");
            read(server, real);
            Files.walkFileTree(real, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                    // Only the source overworld is registered. Do not traverse vanilla dimension saves.
                    if (real.equals(path.getParent())
                            && Set.of("DIM-1", "DIM1").contains(path.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Files.createDirectories(target.resolve(real.relativize(path)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    if (Files.isSymbolicLink(path)) throw new IOException("Links simbolicos nao sao aceitos: " + path);
                    Path relative = real.relativize(path);
                    if (!relative.toString().equals("session.lock")) {
                        Files.copy(path, target.resolve(relative), StandardCopyOption.COPY_ATTRIBUTES);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            read(server, target);
        } catch (java.nio.channels.OverlappingFileLockException ex) {
            throw new IOException("Mundo de origem em uso neste servidor.", ex);
        }
    }
}

