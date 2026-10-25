package com.worldchanger;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Run twice with -PintegrationTests runIntegrationServer to test a real restart. */
@EventBusSubscriber(modid = WorldChanger.MOD_ID)
public final class IndependentWorldsIntegration {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void run(ServerStartedEvent event) {
        if (!Boolean.getBoolean("worldchanger.integrationTests")) return;
        MinecraftServer server = event.getServer();
        Path marker = server.getWorldPath(LevelResource.ROOT).resolve("integration-passed.txt");
        try {
            Files.deleteIfExists(marker.resolveSibling("integration-failed.txt"));
            boolean restart = Files.exists(marker);
            long vanillaSeed = server.overworld().getSeed();
            create(server, "seed_a", WorldType.NORMAL, 123456L);
            create(server, "seed_b", WorldType.NORMAL, -987654L);
            create(server, "seed_copy", WorldType.NORMAL, 123456L);
            create(server, "test_flat", WorldType.FLAT, 42L);
            create(server, "test_void", WorldType.VOID, 84L);
            ServerLevel a = WorldManager.level(server, "seed_a");
            ServerLevel b = WorldManager.level(server, "seed_b");
            ServerLevel copy = WorldManager.level(server, "seed_copy");
            check(a.getSeed() == 123456L && b.getSeed() == -987654L, "independent/restarted seeds");
            check(server.overworld().getSeed() == vanillaSeed, "overworld seed unchanged");
            check(a.getChunkSource().getGenerator() != server.overworld().getChunkSource().getGenerator(), "separate generator");
            String terrain = heights(a);
            check(terrain.equals(heights(copy)), "same seed reproduces vanilla terrain");
            check(!terrain.equals(heights(b)), "different seeds generate different terrain");
            ServerLevel flat = WorldManager.level(server, "test_flat");
            ServerLevel empty = WorldManager.level(server, "test_void");
            check(flat.getBlockState(new BlockPos(8, -64, 8)).is(Blocks.BEDROCK), "vanilla flat layers");
            check(empty.getBlockState(new BlockPos(8, 63, 8)).isAir(), "void empty away from platform");
            check(empty.getBlockState(new BlockPos(0, 63, 0)).is(Blocks.STONE), "void spawn platform");

            ServerPlayer player = player(server);
            AdvancementHolder advancement = server.getAdvancements().get(ResourceLocation.withDefaultNamespace("adventure/adventuring_time"));
            check(advancement != null, "advancement exists");
            player.setServerLevel(a);
            if (restart) check(player.getAdvancements().getOrStartProgress(advancement).isDone(), "advancement survived restart");
            else check(!player.getAdvancements().getOrStartProgress(advancement).isDone(), "new world starts clean");
            int before = player.totalExperience;
            award(player, advancement);
            if (!restart) check(player.totalExperience > before, "completion grants reward");
            int rewarded = player.totalExperience;
            award(player, advancement);
            check(player.totalExperience == rewarded, "reward cannot be reclaimed");
            player.setServerLevel(b);
            check(!player.getAdvancements().getOrStartProgress(advancement).isDone(), "progress isolated from world A");
            player.setServerLevel(server.overworld());
            check(!player.getAdvancements().getOrStartProgress(advancement).isDone(), "vanilla progress isolated");
            player.setServerLevel(server.getLevel(net.minecraft.world.level.Level.NETHER));
            check(!player.getAdvancements().getOrStartProgress(advancement).isDone(), "vanilla dimensions share vanilla bucket");
            player.setServerLevel(a);
            check(player.getAdvancements().getOrStartProgress(advancement).isDone(), "progress restored on return");
            player.getAdvancements().save();
            player.getAdvancements().stopListening();
            // A fresh tracker simulates disconnect/reconnect rather than reusing the cached object.
            var reconnected = new net.minecraft.server.PlayerAdvancements(server.getFixerUpper(), server.getPlayerList(),
                    server.getAdvancements(), server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(PLAYER + ".json"), player);
            check(reconnected.getOrStartProgress(advancement).isDone(), "fresh tracker reloads current world");
            reconnected.stopListening();

            for (ServerLevel level : new ServerLevel[]{empty, server.overworld(),
                    server.getLevel(net.minecraft.world.level.Level.NETHER), server.getLevel(net.minecraft.world.level.Level.END)}) {
                testPortals(level, player, WorldManager.isWorldChangerLevel(level));
            }
            WorldManager.saveWorldStates(server);
            WorldProgressIntegration.run(server);
            Files.writeString(marker, (restart ? "RESTART PASS\n" : "FIRST PASS\n") + terrain);
            WorldChanger.LOGGER.info("WORLDCHANGER INTEGRATION {} PASS: seeds, terrain, advancements, rewards, reconnect, portals", restart ? "RESTART" : "FIRST");
        } catch (Throwable failure) {
            WorldChanger.LOGGER.error("WORLDCHANGER INTEGRATION FAILED", failure);
            try { Files.writeString(marker.resolveSibling("integration-failed.txt"), failure.toString()); }
            catch (Exception ignored) { }
        } finally {
            server.halt(false);
        }
    }

    private static void create(MinecraftServer server, String name, WorldType type, long seed) throws Exception {
        if (WorldManager.entry(name) == null) WorldManager.create(server, name, type, seed);
    }

    private static ServerPlayer player(MinecraftServer server) {
        GameProfile profile = new GameProfile(PLAYER, "IntegrationTest");
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static String heights(ServerLevel level) {
        StringBuilder result = new StringBuilder();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            result.append(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)).append(',');
        return result.toString();
    }

    private static void award(ServerPlayer player, AdvancementHolder advancement) {
        for (String criterion : advancement.value().criteria().keySet()) player.getAdvancements().award(advancement, criterion);
    }

    private static void testPortals(ServerLevel level, ServerPlayer player, boolean blocked) {
        BlockPos base = new BlockPos(8, 100, 8);
        for (int x = 0; x < 4; x++) for (int y = 0; y < 5; y++) {
            level.setBlockAndUpdate(base.offset(x, y, 0),
                    (x == 0 || x == 3 || y == 0 || y == 4 ? Blocks.OBSIDIAN : Blocks.AIR).defaultBlockState());
        }
        PortalShape shape = new PortalShape(level, base.offset(1, 1, 0), Direction.Axis.X);
        check(shape.isValid(), "nether frame valid");
        shape.createPortalBlocks();
        check(level.getBlockState(base.offset(1, 1, 0)).is(Blocks.NETHER_PORTAL) != blocked, "nether activation " + level.dimension());

        BlockPos center = base.offset(0, 0, 10);
        BlockPos last = center.offset(0, 0, -2);
        for (int offset = -1; offset <= 1; offset++) {
            frame(level, center.offset(offset, 0, -2), Direction.SOUTH);
            frame(level, center.offset(offset, 0, 2), Direction.NORTH);
            frame(level, center.offset(-2, 0, offset), Direction.EAST);
            frame(level, center.offset(2, 0, offset), Direction.WEST);
        }
        // Clear previous activation when repeating this test after restart.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setBlockAndUpdate(center.offset(x, 0, z), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(last, level.getBlockState(last).setValue(EndPortalFrameBlock.HAS_EYE, false));
        player.setServerLevel(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE, 2));
        Items.ENDER_EYE.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(last), Direction.UP, last, false)));
        check(level.getBlockState(last).getValue(EndPortalFrameBlock.HAS_EYE), "decorative ender eye placed");
        check(level.getBlockState(center).is(Blocks.END_PORTAL) != blocked, "end activation " + level.dimension());
        // Destination checks also cover existing portals placed by commands or imported structures.
        for (Portal portal : new Portal[]{(Portal) Blocks.NETHER_PORTAL, (Portal) Blocks.END_PORTAL}) {
            player.portalProcess = null;
            BlockPos entry = base.offset(1, 1, 0);
            player.seenCredits = true;
            player.moveTo(Vec3.atCenterOf(entry));
            ((net.minecraft.world.level.block.Block) portal).defaultBlockState().entityInside(level, entry, player);
            check((player.portalProcess == null) == blocked, "portal entry " + level.dimension() + " " + portal);
            check((portal.getPortalDestination(level, player, base.offset(1, 1, 0)) == null) == blocked,
                    "portal destination " + level.dimension() + " " + portal);
        }
    }

    private static void frame(ServerLevel level, BlockPos pos, Direction direction) {
        level.setBlockAndUpdate(pos, Blocks.END_PORTAL_FRAME.defaultBlockState()
                .setValue(EndPortalFrameBlock.FACING, direction).setValue(EndPortalFrameBlock.HAS_EYE, true));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
