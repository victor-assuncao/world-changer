package com.worldchanger.mixin;

import com.worldchanger.WorldManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PortalShape.class)
public abstract class PortalShapeMixin {
    @Shadow @Final private LevelAccessor level;

    @Inject(method = "createPortalBlocks", at = @At("HEAD"), cancellable = true)
    private void worldchanger$blockCreation(CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel && WorldManager.isWorldChangerLevel(serverLevel)) ci.cancel();
    }
}
