package com.worldchanger.mixin;

import com.worldchanger.WorldChanger;
import com.worldchanger.WorldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({NetherPortalBlock.class, EndPortalBlock.class})
public abstract class PortalBlockMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void worldchanger$blockEntry(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (WorldChanger.MOD_ID.equals(level.dimension().location().getNamespace())) ci.cancel();
    }

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void worldchanger$blockTravel(ServerLevel level, Entity entity, BlockPos pos,
            CallbackInfoReturnable<DimensionTransition> cir) {
        if (WorldManager.isWorldChangerLevel(level)) cir.setReturnValue(null);
    }
}
