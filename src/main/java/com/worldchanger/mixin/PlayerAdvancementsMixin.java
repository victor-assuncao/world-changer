package com.worldchanger.mixin;

import com.worldchanger.WorldAdvancements;
import com.worldchanger.WorldManager;
import java.nio.file.Path;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin implements WorldAdvancements {
    @Shadow @Final @Mutable private Path playerSavePath;
    @Shadow private ServerPlayer player;
    @Shadow public abstract void save();
    @Shadow public abstract void reload(ServerAdvancementManager manager);
    @Shadow public abstract void flushDirty(ServerPlayer player);
    @Unique private Path worldchanger$vanillaPath;

    @Inject(method = "load", at = @At("HEAD"))
    private void worldchanger$initialPath(ServerAdvancementManager manager, CallbackInfo ci) {
        if (worldchanger$vanillaPath == null) {
            worldchanger$vanillaPath = playerSavePath;
            playerSavePath = worldchanger$path(player);
        }
    }

    @Unique
    private Path worldchanger$path(ServerPlayer player) {
        if (!WorldManager.isWorldChangerLevel(player.serverLevel())) return worldchanger$vanillaPath;
        return player.server.getWorldPath(LevelResource.ROOT).resolve("worldchanger")
                .resolve(player.serverLevel().dimension().location().getPath())
                .resolve("advancements").resolve(player.getUUID() + ".json");
    }

    @Override
    public void worldchanger$switchWorld(ServerPlayer player) {
        Path destination = worldchanger$path(player);
        if (destination.equals(playerSavePath)) return;
        save();
        playerSavePath = destination;
        // reload removes old criterion listeners, clears progress and resets the client tree.
        reload(player.server.getAdvancements());
        if (player.connection != null) {
            // Vanilla flushDirty skips empty packets, so an empty destination needs an explicit reset.
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket(
                    true, java.util.List.of(), java.util.Set.of(), java.util.Map.of()));
            flushDirty(player);
        }
    }
}
