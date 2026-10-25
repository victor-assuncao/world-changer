package com.worldchanger;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

@Mod(WorldChanger.MOD_ID)
public class WorldChanger {
    public static final String MOD_ID = "worldchanger";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WorldChanger(IEventBus modEventBus) {
        WorldPermissions.register();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        WorldCommands.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        try {
            WorldImport.directory(event.getServer());
        } catch (java.io.IOException ex) {
            LOGGER.error("Nao foi possivel preparar a pasta worlds para importacao", ex);
        }
        WorldManager.loadAll(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        WorldManager.clear();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        WorldManager.saveWorldStates(event.getServer());
        for (net.minecraft.server.level.ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            WorldManager.savePlayerState(event.getServer(), player);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            WorldManager.savePlayerState(player.server, player);
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            WorldManager.restoreLastWorld(player.server, player);
        }
    }

    private void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()
                && event.getOriginal() instanceof net.minecraft.server.level.ServerPlayer original) {
            WorldManager.rememberDeath(original);
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            WorldManager.handleDeathRespawn(player);
        }
    }
}
