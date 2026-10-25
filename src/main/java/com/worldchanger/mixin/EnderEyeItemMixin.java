package com.worldchanger.mixin;

import com.worldchanger.WorldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnderEyeItem.class)
public abstract class EnderEyeItemMixin {
    // Keep vanilla eye placement, consumption and sounds; skip only portal formation.
    @Redirect(method = "useOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/pattern/BlockPattern;find(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/pattern/BlockPattern$BlockPatternMatch;"))
    private BlockPattern.BlockPatternMatch worldchanger$blockActivation(BlockPattern pattern, LevelReader level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel && WorldManager.isWorldChangerLevel(serverLevel)) return null;
        return pattern.find(level, pos);
    }
}
