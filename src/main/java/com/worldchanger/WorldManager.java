package com.worldchanger;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.LevelEvent;
import com.worldchanger.mixin.AttachmentHolderMixin;

public final class WorldManager {
    private static final String ATTACHMENTS_TAG = "WorldChangerAttachments";
    private static final String PERSISTENT_DATA_TAG = "WorldChangerNeoForgeData";
    private static final String EXTERNAL_DATA_TAG = "WorldChangerExternalData";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();

    /** Registro persistido em worldchanger/worlds.json */
    public static class Entry {
        public String name;
        public String type;
        public boolean pendingDeletion;
        public Entry(String name, String type) { this(name, type, false); }
        public Entry(String name, String type, boolean pendingDeletion) {
            this.name = name;
            this.type = type;
            this.pendingDeletion = pendingDeletion;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, ServerLevel> LEVELS = new LinkedHashMap<>();
    private static final Map<UUID, String> PENDING_DEATH_WORLDS = new LinkedHashMap<>();
    private static final Map<UUID, Boolean> PENDING_DEATH_KEEP_COSMETIC = new LinkedHashMap<>();

    private WorldManager() {}

    public static Collection<String> names() {
        List<String> n = ENTRIES.values().stream().filter(e -> !e.pendingDeletion)
                .map(e -> e.name).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        n.add("overworld");
        return n;
    }

    public static Collection<Entry> entries() {
        return ENTRIES.values().stream().filter(e -> !e.pendingDeletion).toList();
    }

    public static Entry entry(String name) {
        return ENTRIES.get(name);
    }

    public static boolean isWorldChangerLevel(ServerLevel level) {
        return level != null && level.dimension().location().getNamespace().equals(WorldChanger.MOD_ID);
    }

    public static void setSpawn(MinecraftServer server, String name, BlockPos pos, float angle) {
        ServerLevel level = level(server, name);
        if (level == null) throw new IllegalArgumentException("Mundo '" + name + "' nao existe.");
        level.setDefaultSpawnPos(pos, angle);
        if (level.getLevelData() instanceof WorldLevelData data) data.setSpawn(pos, angle);
        if (!name.equals("overworld")) saveWorldState(server, name, level);
    }

    public static ServerLevel level(MinecraftServer server, String name) {
        return name.equals("overworld") ? server.overworld() : LEVELS.get(name);
    }

    public static void clear() {
        ENTRIES.clear();
        LEVELS.clear();
        PENDING_DEATH_WORLDS.clear();
        PENDING_DEATH_KEEP_COSMETIC.clear();
    }

    /** Registra o mundo de uma morte para que o respawn volte ao mesmo level. */
    public static void rememberDeath(ServerPlayer original) {
        String name = worldName(original.serverLevel());
        PENDING_DEATH_WORLDS.put(original.getUUID(), name);
        PENDING_DEATH_KEEP_COSMETIC.put(original.getUUID(),
                original.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY));
    }

    /** Finaliza um respawn ocorrido em um mundo World Changer. */
    public static void handleDeathRespawn(ServerPlayer player) {
        String name = PENDING_DEATH_WORLDS.remove(player.getUUID());
        if (name == null) return;
        boolean keepCosmetic = Boolean.TRUE.equals(PENDING_DEATH_KEEP_COSMETIC.remove(player.getUUID()));
        ServerLevel target = LEVELS.get(name);
        // O inventario novo ja foi criado pelo vanilla. O Cosmetic Armor possui
        // seu proprio tratamento de drops; aqui apenas impedimos que o estado
        // cosmetico do jogador morto seja copiado para o jogador renascido.
        if (!keepCosmetic && !replaceCosmeticArmor(player, new CompoundTag())) {
            WorldChanger.LOGGER.error("Nao foi possivel limpar armaduras cosmeticas apos a morte de {}",
                    player.getName().getString());
        }
        if (target == null) return;
        BlockPos spawn = target.getSharedSpawnPos();
        player.teleportTo(target, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        try {
            saveLastWorld(player.server, player, name);
        } catch (IOException ex) {
            WorldChanger.LOGGER.error("Falha ao salvar o mundo do respawn de {}", player.getName().getString(), ex);
        }
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("worldchanger").resolve("worlds.json");
    }

    private static Path playerStateFile(MinecraftServer server, UUID uuid, String worldName) {
        return server.getWorldPath(LevelResource.ROOT).resolve("worldchanger").resolve(worldName)
                .resolve("players").resolve(uuid.toString() + ".dat");
    }

    private static Path worldStateFile(MinecraftServer server, String worldName) {
        return server.getWorldPath(LevelResource.ROOT).resolve("worldchanger").resolve(worldName)
                .resolve("world.dat");
    }

    private static Path lastWorldFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("worldchanger").resolve("players.dat");
    }

    private static void writeCompressedAtomically(CompoundTag tag, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(tag, temp);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void saveLastWorld(MinecraftServer server, ServerPlayer player, String worldName) throws IOException {
        Path f = lastWorldFile(server);
        CompoundTag tag = Files.exists(f)
                ? NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap()) : new CompoundTag();
        CompoundTag players = tag.contains("Players") ? tag.getCompound("Players") : new CompoundTag();
        CompoundTag playerTag = new CompoundTag();
        playerTag.putString("LastWorld", worldName);
        players.put(player.getUUID().toString(), playerTag);
        tag.put("Players", players);
        writeCompressedAtomically(tag, f);
    }

    private static String loadLastWorld(MinecraftServer server, UUID uuid) {
        Path f = lastWorldFile(server);
        if (Files.exists(f)) {
            try {
                CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
                String world = tag.getCompound("Players").getCompound(uuid.toString()).getString("LastWorld");
                if (!world.isBlank()) return world;
            } catch (IOException ex) {
                WorldChanger.LOGGER.error("Falha ao ler o ultimo mundo do jogador {}", uuid, ex);
            }
        }

        return null;
    }

    private static void loadWorldState(MinecraftServer server, String name, WorldLevelData data) {
        Path f = worldStateFile(server, name);
        if (!Files.exists(f)) return;
        try {
            CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            data.setGameTime(tag.getLong("GameTime"));
            data.setDayTime(tag.getLong("DayTime"));
            data.setClearWeatherTime(tag.getInt("ClearWeatherTime"));
            data.setRainTime(tag.getInt("RainTime"));
            data.setThunderTime(tag.getInt("ThunderTime"));
            data.setRaining(tag.getBoolean("Raining"));
            data.setThundering(tag.getBoolean("Thundering"));
            if (tag.contains("SpawnX")) {
                data.setSpawn(new BlockPos(tag.getInt("SpawnX"), tag.getInt("SpawnY"), tag.getInt("SpawnZ")),
                        tag.getFloat("SpawnAngle"));
            }
            if (tag.contains("GameRules")) {
                data.getGameRules().assignFrom(new GameRules(
                        new Dynamic<>(NbtOps.INSTANCE, tag.get("GameRules"))), server);
            }
        } catch (Exception ex) {
            WorldChanger.LOGGER.error("Falha ao ler os dados persistentes do mundo '{}'", name, ex);
        }
    }

    private static void saveWorldState(MinecraftServer server, String name, ServerLevel level) {
        if (!(level.getLevelData() instanceof WorldLevelData data)) return;
        CompoundTag tag = new CompoundTag();
        tag.putLong("GameTime", data.getGameTime());
        tag.putLong("DayTime", data.getDayTime());
        tag.putInt("ClearWeatherTime", data.getClearWeatherTime());
        tag.putInt("RainTime", data.getRainTime());
        tag.putInt("ThunderTime", data.getThunderTime());
        tag.putBoolean("Raining", data.isRaining());
        tag.putBoolean("Thundering", data.isThundering());
        BlockPos spawn = data.getSpawnPos();
        tag.putInt("SpawnX", spawn.getX());
        tag.putInt("SpawnY", spawn.getY());
        tag.putInt("SpawnZ", spawn.getZ());
        tag.putFloat("SpawnAngle", data.getSpawnAngle());
        tag.put("GameRules", data.getGameRules().createTag());
        Path f = worldStateFile(server, name);
        try {
            writeCompressedAtomically(tag, f);
        } catch (IOException ex) {
            WorldChanger.LOGGER.error("Falha ao salvar os dados persistentes do mundo '{}'", name, ex);
        }
    }

    /** Salva o relogio, clima, spawn e gamerules dos mundos personalizados. */
    public static void saveWorldStates(MinecraftServer server) {
        for (Entry entry : ENTRIES.values()) {
            ServerLevel level = LEVELS.get(entry.name);
            if (level != null) saveWorldState(server, entry.name, level);
        }
    }

    private static String worldName(ServerLevel level) {
        return level.dimension().equals(Level.OVERWORLD) ? "overworld" : level.dimension().location().getPath();
    }

    /** Salva o estado que deve ser separado por mundo para este jogador. */
    public static boolean savePlayerState(MinecraftServer server, ServerPlayer player) {
        try {
            CompoundTag tag = new CompoundTag();
            player.addAdditionalSaveData(tag);
            // NeoForge guarda Curios, backpacks e dados de outros mods neste
            // armazenamento comum. Salvar o composto inteiro evita uma lista
            // de compatibilidade específica para cada mod.
            CompoundTag attachments = player.serializeAttachments(player.registryAccess());
            if (attachments != null) tag.put(ATTACHMENTS_TAG, attachments.copy());
            tag.put(PERSISTENT_DATA_TAG, player.getPersistentData().copy());
            if (!saveCosmeticArmor(player, tag)) {
                throw new IOException("Nao foi possivel capturar os dados do Cosmetic Armor Reworked");
            }
            tag.putDouble("WorldChangerX", player.getX());
            tag.putDouble("WorldChangerY", player.getY());
            tag.putDouble("WorldChangerZ", player.getZ());
            tag.putFloat("WorldChangerYaw", player.getYRot());
            tag.putFloat("WorldChangerPitch", player.getXRot());
            tag.putInt("WorldChangerGameMode", player.gameMode.getGameModeForPlayer().getId());
            String currentWorld = worldName(player.serverLevel());
            Path f = playerStateFile(server, player.getUUID(), currentWorld);
            writeCompressedAtomically(tag, f);
            saveLastWorld(server, player, currentWorld);
            return true;
        } catch (Exception ex) {
            WorldChanger.LOGGER.error("Falha ao salvar estado do jogador {} no mundo {}", player.getName().getString(),
                    worldName(player.serverLevel()), ex);
            return false;
        }
    }

    /** Restaura o ultimo mundo salvo quando o jogador entra no servidor. */
    public static void restoreLastWorld(MinecraftServer server, ServerPlayer player) {
        String name = loadLastWorld(server, player.getUUID());
        if (name == null || name.isBlank() || name.equals("overworld")) return;
        ServerLevel target = LEVELS.get(name);
        if (target == null) {
            WorldChanger.LOGGER.warn("O ultimo mundo salvo '{}' do jogador {} nao existe mais", name,
                    player.getName().getString());
            return;
        }
        CompoundTag state = loadPlayerState(server, player.getUUID(), name);
        if (state == null) return;
        double x = state.getDouble("WorldChangerX");
        double y = state.getDouble("WorldChangerY");
        double z = state.getDouble("WorldChangerZ");
        float yaw = state.getFloat("WorldChangerYaw");
        float pitch = state.getFloat("WorldChangerPitch");
        if (!applyPlayerState(player, state)) {
            WorldChanger.LOGGER.error("Restauracao do estado cosmetico falhou para {}; jogador permanece no mundo atual",
                    player.getName().getString());
            return;
        }
        player.teleportTo(target, x, y, z, yaw, pitch);
    }

    private static CompoundTag loadPlayerState(MinecraftServer server, UUID uuid, String worldName) {
        Path f = playerStateFile(server, uuid, worldName);
        if (!Files.exists(f)) return null;
        try {
            return NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
        } catch (IOException ex) {
            WorldChanger.LOGGER.error("Falha ao ler estado do jogador {} no mundo {}", uuid, worldName, ex);
            return null;
        }
    }

    private static boolean clearPlayerState(ServerPlayer player) {
        if (!replaceCosmeticArmor(player, new CompoundTag())) return false;
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.getFoodData().setExhaustion(0.0f);
        player.experienceProgress = 0.0f;
        player.experienceLevel = 0;
        player.totalExperience = 0;
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        clearNeoForgeData(player);
        return true;
    }

    private static boolean applyPlayerState(ServerPlayer player, CompoundTag tag) {
        CompoundTag cosmetic = tag.contains(EXTERNAL_DATA_TAG)
                ? tag.getCompound(EXTERNAL_DATA_TAG) : new CompoundTag();
        if (!replaceCosmeticArmor(player, cosmetic)) return false;
        player.readAdditionalSaveData(tag);
        restoreNeoForgeData(player, tag);
        if (tag.contains("WorldChangerGameMode")) {
            player.gameMode.changeGameModeForPlayer(GameType.byId(tag.getInt("WorldChangerGameMode")));
        }
        return true;
    }

    private static void clearNeoForgeData(ServerPlayer player) {
        clearAttachments(player);
        clearTag(player.getPersistentData());
    }

    private static void restoreNeoForgeData(ServerPlayer player, CompoundTag tag) {
        clearAttachments(player);
        if (tag.contains(ATTACHMENTS_TAG)) {
            ((AttachmentHolderMixin) (Object) player).worldchanger$deserializeAttachments(
                    player.registryAccess(), tag.getCompound(ATTACHMENTS_TAG));
        }
        clearTag(player.getPersistentData());
        if (tag.contains(PERSISTENT_DATA_TAG)) {
            player.getPersistentData().merge(tag.getCompound(PERSISTENT_DATA_TAG));
        }
    }

    private static void clearAttachments(ServerPlayer player) {
        Map<net.neoforged.neoforge.attachment.AttachmentType<?>, Object> attachments =
                ((AttachmentHolderMixin) (Object) player).worldchanger$getAttachments();
        if (attachments == null) return;
        for (net.neoforged.neoforge.attachment.AttachmentType<?> type
                : new ArrayList<>(attachments.keySet())) {
            player.removeData(type);
        }
        attachments.clear();
    }

    private static void clearTag(CompoundTag tag) {
        for (String key : new ArrayList<>(tag.getAllKeys())) tag.remove(key);
    }

    private static Object cosmeticInventory(ServerPlayer player) throws Exception {
        if (!ModList.get().isLoaded("cosmeticarmorreworked")) return null;
        Class<?> api = Class.forName("lain.mods.cos.api.CosArmorAPI");
        return api.getMethod("getCAStacks", UUID.class).invoke(null, player.getUUID());
    }

    private static CompoundTag cosmeticSnapshot(ServerPlayer player, Object inventory) throws Exception {
        Object value = inventory.getClass().getMethod("serializeNBT", HolderLookup.Provider.class)
                .invoke(inventory, player.registryAccess());
        return value instanceof CompoundTag nbt ? nbt.copy() : null;
    }

    private static void applyCosmeticSnapshot(ServerPlayer player, Object inventory, CompoundTag snapshot)
            throws Exception {
        inventory.getClass().getMethod("deserializeNBT", HolderLookup.Provider.class, CompoundTag.class)
                .invoke(inventory, player.registryAccess(), snapshot);
    }

    private static boolean saveCosmeticArmor(ServerPlayer player, CompoundTag playerTag) {
        try {
            Object inventory = cosmeticInventory(player);
            if (inventory == null) return true;
            CompoundTag snapshot = cosmeticSnapshot(player, inventory);
            if (snapshot == null) return false;
            playerTag.put(EXTERNAL_DATA_TAG, snapshot);
            return true;
        } catch (Exception ex) {
            WorldChanger.LOGGER.error("Nao foi possivel salvar armaduras cosmeticas de {}",
                    player.getName().getString(), ex);
            return false;
        }
    }

    /** Substitui os slots e repõe o snapshot anterior se qualquer etapa falhar. */
    private static boolean replaceCosmeticArmor(ServerPlayer player, CompoundTag target) {
        Object inventory = null;
        CompoundTag before = null;
        try {
            inventory = cosmeticInventory(player);
            if (inventory == null) return true;
            before = cosmeticSnapshot(player, inventory);
            if (before == null) return false;
            Method clear = inventory.getClass().getMethod("clearContent");
            clear.invoke(inventory);
            applyCosmeticSnapshot(player, inventory, target);
            return true;
        } catch (Exception ex) {
            if (inventory != null && before != null) {
                try {
                    inventory.getClass().getMethod("clearContent").invoke(inventory);
                    applyCosmeticSnapshot(player, inventory, before);
                } catch (Exception rollbackEx) {
                    WorldChanger.LOGGER.error("Falha ao reverter armaduras cosmeticas de {}",
                            player.getName().getString(), rollbackEx);
                }
            }
            WorldChanger.LOGGER.error("Falha ao trocar armaduras cosmeticas de {}",
                    player.getName().getString(), ex);
            return false;
        }
    }

    public static void loadAll(MinecraftServer server) {
        clear();
        Path f = file(server);
        if (!Files.exists(f)) return;
        boolean registryChanged = false;
        try (Reader r = Files.newBufferedReader(f)) {
            List<Entry> list = GSON.fromJson(r, LIST_TYPE);
            if (list == null) return;
            for (Entry e : list) {
                if (e.pendingDeletion) {
                    try {
                        deleteWorldFiles(server, e.name);
                        registryChanged = true;
                        WorldChanger.LOGGER.info("Dados pendentes do mundo '{}' removidos durante a inicializacao", e.name);
                    } catch (Exception ex) {
                        ENTRIES.put(e.name, e);
                        WorldChanger.LOGGER.warn("A exclusao do mundo '{}' continua pendente; os arquivos ainda estao em uso", e.name, ex);
                    }
                    continue;
                }
                try {
                    ENTRIES.put(e.name, e);
                    LEVELS.put(e.name, createLevel(server, e.name, WorldType.parse(e.type)));
                    WorldChanger.LOGGER.info("Mundo '{}' carregado ({})", e.name, e.type);
                } catch (Exception ex) {
                    ENTRIES.remove(e.name);
                    WorldChanger.LOGGER.error("Falha ao carregar o mundo '{}'", e.name, ex);
                }
            }
        } catch (IOException ex) {
            WorldChanger.LOGGER.error("Falha ao ler worlds.json", ex);
        }
        if (registryChanged) saveAll(server);
    }

    private static void saveAll(MinecraftServer server) {
        Path f = file(server);
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f)) {
                GSON.toJson(new ArrayList<>(ENTRIES.values()), LIST_TYPE, w);
            }
        } catch (IOException ex) {
            WorldChanger.LOGGER.error("Falha ao salvar worlds.json", ex);
        }
    }

    public static void create(MinecraftServer server, String name, WorldType type) {
        if (!name.matches("[a-z0-9_]{1,32}")) {
            throw new IllegalArgumentException("Nome invalido: use apenas letras minusculas, numeros e _ (max 32).");
        }
        if (name.equals("overworld") || ENTRIES.containsKey(name)) {
            throw new IllegalArgumentException("Ja existe um mundo com esse nome ou a exclusao anterior ainda esta pendente.");
        }
        ServerLevel level = createLevel(server, name, type);
        ENTRIES.put(name, new Entry(name, type.name()));
        LEVELS.put(name, level);
        saveAll(server);
    }

    /** Descarrega um mundo e tenta remover seus arquivos; arquivos bloqueados ficam pendentes para o proximo inicio. */
    public static boolean delete(MinecraftServer server, String name) throws IOException {
        if (name.equals("overworld")) {
            throw new IllegalArgumentException("O overworld principal nao pode ser removido.");
        }
        Entry entry = ENTRIES.get(name);
        ServerLevel level = LEVELS.get(name);
        if (entry == null || level == null) {
            throw new IllegalArgumentException("Mundo '" + name + "' nao existe.");
        }
        if (!name.matches("[a-z0-9_]{1,32}")) {
            throw new IllegalArgumentException("O nome registrado para este mundo e invalido; exclusao cancelada.");
        }
        if (!level.players().isEmpty()) {
            throw new IllegalArgumentException("Ainda ha jogadores nesse mundo. Mova todos para outro mundo antes de exclui-lo.");
        }

        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        ResourceKey<Level> key = level.dimension();
        Path dimensionPath = server.storageSource.getDimensionPath(key).toAbsolutePath().normalize();
        Path metadataPath = root.resolve("worldchanger").resolve(name).normalize();
        if (!isSafeWorldPath(root, dimensionPath) || !isSafeWorldPath(root, metadataPath)) {
            throw new IllegalArgumentException("O caminho dos arquivos deste mundo e invalido; exclusao cancelada.");
        }

        byte[] previousPlayerData = null;
        Path playerDataFile = lastWorldFile(server);
        if (Files.exists(playerDataFile)) previousPlayerData = Files.readAllBytes(playerDataFile);

        saveWorldState(server, name, level);
        level.save(null, true, false);
        if (previousPlayerData != null) updateLastWorldReferences(server, name, "overworld");
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        level.close();
        server.levels.remove(key, level);
        server.markWorldsDirty();

        Entry pendingEntry = new Entry(name, entry.type, true);
        List<Entry> entriesWithPendingDeletion = new ArrayList<>();
        for (Entry existing : ENTRIES.values()) {
            entriesWithPendingDeletion.add(existing.name.equals(name) ? pendingEntry : existing);
        }
        try {
            // O registro tombstone evita que o level volte a carregar se outro mod
            // mantiver arquivos abertos no Windows durante a exclusao fisica.
            writeEntriesAtomically(server, entriesWithPendingDeletion);
            ENTRIES.put(name, pendingEntry);
            LEVELS.remove(name);
        } catch (Exception failure) {
            try {
                if (previousPlayerData != null) {
                    writeBytesAtomically(lastWorldFile(server), previousPlayerData);
                }
                ServerLevel restored = createLevel(server, name, WorldType.parse(entry.type));
                LEVELS.put(name, restored);
            } catch (Exception rollbackFailure) {
                WorldChanger.LOGGER.error("Falha ao restaurar o mundo '{}' apos uma exclusao incompleta", name,
                        rollbackFailure);
                throw new IOException("A exclusao falhou e a restauracao automatica tambem falhou. "
                        + "Reinicie o servidor e verifique os arquivos do mundo.", failure);
            }
            throw new IOException("Nao foi possivel registrar a exclusao; o mundo foi restaurado no servidor.", failure);
        }

        try {
            deleteWorldFiles(server, name);
            List<Entry> remainingEntries = new ArrayList<>(ENTRIES.values());
            remainingEntries.removeIf(existing -> existing.name.equals(name));
            writeEntriesAtomically(server, remainingEntries);
            ENTRIES.remove(name);
            return true;
        } catch (IOException cleanupFailure) {
            WorldChanger.LOGGER.warn("O mundo '{}' foi descarregado; a remocao dos arquivos ficou pendente ate o proximo inicio do servidor",
                    name, cleanupFailure);
            return false;
        }
    }

    private static boolean isSafeWorldPath(Path root, Path candidate) {
        return !candidate.equals(root) && candidate.startsWith(root);
    }

    private static void deleteWorldFiles(MinecraftServer server, String name) throws IOException {
        if (name == null || !name.matches("[a-z0-9_]{1,32}")) {
            throw new IOException("Nome invalido na fila de exclusao pendente.");
        }
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(WorldChanger.MOD_ID, name));
        Path dimensionPath = server.storageSource.getDimensionPath(key).toAbsolutePath().normalize();
        Path metadataPath = root.resolve("worldchanger").resolve(name).normalize();
        if (!isSafeWorldPath(root, dimensionPath) || !isSafeWorldPath(root, metadataPath)) {
            throw new IOException("Caminho invalido na fila de exclusao pendente.");
        }
        deleteTree(dimensionPath);
        deleteTree(metadataPath);
        deleteMatchingStaging(root, dimensionPath.getParent(), ".worldchanger-delete-" + name + "-");
        deleteMatchingStaging(root, metadataPath.getParent(), ".worldchanger-delete-" + name + "-");
    }

    private static void deleteMatchingStaging(Path root, Path parent, String prefix) throws IOException {
        if (!Files.isDirectory(parent)) return;
        try (Stream<Path> paths = Files.list(parent)) {
            for (Path candidate : paths.filter(path -> path.getFileName().toString().startsWith(prefix)).toList()) {
                Path normalized = candidate.toAbsolutePath().normalize();
                if (!isSafeWorldPath(root, normalized)) throw new IOException("Caminho temporario invalido.");
                deleteTree(normalized);
            }
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private static void updateLastWorldReferences(MinecraftServer server, String oldWorld, String replacement)
            throws IOException {
        Path path = lastWorldFile(server);
        if (!Files.exists(path)) return;
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        CompoundTag players = root.getCompound("Players");
        for (String uuid : players.getAllKeys()) {
            CompoundTag player = players.getCompound(uuid);
            if (player.getString("LastWorld").equals(oldWorld)) {
                player.putString("LastWorld", replacement);
                players.put(uuid, player);
            }
        }
        root.put("Players", players);
        writeCompressedAtomically(root, path);
    }

    private static void writeBytesAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void writeEntriesAtomically(MinecraftServer server, Collection<Entry> entries) throws IOException {
        Path target = file(server);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(new ArrayList<>(entries), LIST_TYPE, writer);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Cria e registra um ServerLevel novo no servidor em execucao. */
    private static ServerLevel createLevel(MinecraftServer server, String name, WorldType type) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(WorldChanger.MOD_ID, name));
        ServerLevel overworld = server.overworld();

        Holder<DimensionType> dimType = server.registryAccess()
                .lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        // Para o tipo NORMAL, reutilizamos a mesma configuracao de geracao do
        // overworld vanilla. Isso evita divergir de futuras alteracoes do
        // preset vanilla (biomas, ruido e estruturas).
        LevelStem stem = new LevelStem(dimType, type.createGenerator(server));

        // Em 1.21.1 GameRules nao recebe FeatureFlagSet no construtor.
        // Cada mundo inicia com uma instancia propria dos valores padrao.
        GameRules rules = new GameRules();
        WorldLevelData data = new WorldLevelData(server.getWorldData(), server.getWorldData().overworldData(), rules);
        loadWorldState(server, name, data);

        long seed = server.getWorldData().worldGenOptions().seed();
        List<CustomSpawner> spawners = type == WorldType.NORMAL
                ? ImmutableList.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(),
                        new VillageSiege(), new WanderingTraderSpawner(data))
                : ImmutableList.of();

        ServerLevel level = new ServerLevel(server, Util.backgroundExecutor(), server.storageSource, data, key, stem,
                new NoopListener(), false, BiomeManager.obfuscateSeed(seed), spawners, true,
                overworld.getRandomSequences());

        server.levels.put(key, level);
        // MinecraftServer usa um array em cache para percorrer os mundos no
        // tick. Sem invalidar o cache, o level criado em runtime nao recebe
        // ticks de blocos, entidades, iluminacao, tempo e clima.
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));

        prepareSpawn(level, data, type);
        return level;
    }

    private static void prepareSpawn(ServerLevel level, WorldLevelData data, WorldType type) {
        level.getChunk(0, 0); // forca a geracao do chunk inicial
        BlockPos spawn;
        if (type == WorldType.VOID) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos p = new BlockPos(x, 63, z);
                    if (level.getBlockState(p).isAir()) level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
                }
            }
            spawn = new BlockPos(0, 64, 0);
        } else {
            spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
        }
        if (!data.hasSpawn()) data.setSpawn(spawn, 0f);
    }

    public static void switchWorld(MinecraftServer server, ServerPlayer player, String name) {
        String currentName = worldName(player.serverLevel());
        ServerLevel target = name.equals("overworld") ? server.overworld() : LEVELS.get(name);
        if (target == null) throw new IllegalArgumentException("Mundo '" + name + "' nao existe.");
        if (currentName.equals(name)) return;

        if (!savePlayerState(server, player)) {
            throw new IllegalArgumentException("Nao foi possivel salvar seu estado atual; troca cancelada para evitar perda de itens.");
        }
        CompoundTag destination = loadPlayerState(server, player.getUUID(), name);
        if (Files.exists(playerStateFile(server, player.getUUID(), name)) && destination == null) {
            throw new IllegalArgumentException("Os dados do jogador neste mundo estao ilegiveis; troca cancelada para evitar perda de itens.");
        }
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        if (destination != null && destination.contains("WorldChangerX")) {
            x = destination.getDouble("WorldChangerX");
            y = destination.getDouble("WorldChangerY");
            z = destination.getDouble("WorldChangerZ");
            yaw = destination.getFloat("WorldChangerYaw");
            pitch = destination.getFloat("WorldChangerPitch");
        } else {
            BlockPos p = target.getSharedSpawnPos();
            x = p.getX() + 0.5;
            y = p.getY();
            z = p.getZ() + 0.5;
            yaw = player.getYRot();
            pitch = player.getXRot();
        }
        // O estado precisa ser aplicado antes do teleporte. O pacote de
        // respawn enviado pelo teleportTo inclui o gamemode atual; aplicar
        // depois deixava cliente e servidor dessincronizados (HUD criativo
        // com regras de sobrevivencia, por exemplo).
        boolean applied = destination == null ? clearPlayerState(player) : applyPlayerState(player, destination);
        if (!applied) {
            throw new IllegalArgumentException("Nao foi possivel trocar os dados cosmeticos com seguranca; troca cancelada.");
        }
        player.teleportTo(target, x, y, z, yaw, pitch);
    }

    private static class NoopListener implements ChunkProgressListener {
        @Override public void updateSpawnPos(ChunkPos center) {}
        @Override public void onStatusChange(ChunkPos pos, ChunkStatus status) {}
        @Override public void start() {}
        @Override public void stop() {}
    }
}
