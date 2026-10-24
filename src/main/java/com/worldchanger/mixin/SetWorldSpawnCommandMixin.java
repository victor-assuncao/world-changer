package com.worldchanger.mixin;

import com.worldchanger.WorldManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.SetWorldSpawnCommand;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SetWorldSpawnCommand.class)
public abstract class SetWorldSpawnCommandMixin {
    @Inject(method = "setSpawn", at = @At("HEAD"), cancellable = true)
    private static void worldchanger$setSpawn(CommandSourceStack source, BlockPos pos, float angle,
            CallbackInfoReturnable<Integer> callback) {
        ServerLevel level = source.getLevel();
        if (!WorldManager.isWorldChangerLevel(level)) return;
        level.setDefaultSpawnPos(pos, angle);
        source.sendSuccess(() -> Component.translatable("commands.setworldspawn.success",
                pos.getX(), pos.getY(), pos.getZ(), angle), true);
        callback.setReturnValue(1);
    }
}
