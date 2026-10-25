package com.worldchanger.mixin;

import com.worldchanger.WorldAdvancements;
import com.worldchanger.WorldStatistics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "setServerLevel", at = @At("TAIL"))
    private void worldchanger$advancements(ServerLevel level, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.getAdvancements() instanceof WorldAdvancements tracker && !player.isFakePlayer()) {
            tracker.worldchanger$switchWorld(player);
        }
        if (player.getStats() instanceof WorldStatistics counter && !player.isFakePlayer()) {
            counter.worldchanger$switchWorld(player);
        }
    }
}
