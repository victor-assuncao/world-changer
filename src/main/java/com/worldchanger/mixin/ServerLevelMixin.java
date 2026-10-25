package com.worldchanger.mixin;

import com.worldchanger.WorldChanger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Faz o cliente receber o indicador flat correto ao entrar em cada dimensao. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @org.spongepowered.asm.mixin.injection.Redirect(method = {"<init>", "getSeed", "findNearestMapStructure"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;worldGenOptions()Lnet/minecraft/world/level/levelgen/WorldOptions;"))
    private net.minecraft.world.level.levelgen.WorldOptions worldchanger$options(
            net.minecraft.world.level.storage.WorldData data) {
        var options = com.worldchanger.WorldManager.importedOptions((ServerLevel) (Object) this);
        return options == null ? data.worldGenOptions() : options;
    }

    @Inject(method = "isFlat", at = @At("HEAD"), cancellable = true)
    private void worldchanger$isFlat(CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (WorldChanger.MOD_ID.equals(level.dimension().location().getNamespace())) {
            cir.setReturnValue(level.getChunkSource().getGenerator() instanceof FlatLevelSource);
        }
    }
}
