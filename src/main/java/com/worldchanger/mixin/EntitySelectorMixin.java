package com.worldchanger.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restringe alvos dos comandos vanilla ao mundo em que o comando foi emitido. */
@Mixin(EntitySelector.class)
public abstract class EntitySelectorMixin {
    @Inject(method = "findEntities", at = @At("RETURN"), cancellable = true)
    private void worldchanger$limitEntities(CommandSourceStack source,
            CallbackInfoReturnable<List<? extends Entity>> callback) {
        // Comandos emitidos por jogadores nunca podem atingir entidades de outra dimensão.
        // O console e outras fontes não-jogador continuam com o comportamento global.
        if (!(source.getEntity() instanceof ServerPlayer)) return;
        List<? extends Entity> selected = callback.getReturnValue();
        List<Entity> sameWorld = new ArrayList<>();
        for (Entity entity : selected) {
            if (entity.level() == source.getLevel()) sameWorld.add(entity);
        }
        callback.setReturnValue(sameWorld);
    }

    @Inject(method = "findPlayers", at = @At("RETURN"), cancellable = true)
    private void worldchanger$limitPlayers(CommandSourceStack source,
            CallbackInfoReturnable<List<ServerPlayer>> callback) {
        // A mesma regra vale para alvos de comandos que aceitam apenas jogadores.
        if (!(source.getEntity() instanceof ServerPlayer)) return;
        List<ServerPlayer> selected = callback.getReturnValue();
        List<ServerPlayer> sameWorld = new ArrayList<>();
        for (ServerPlayer player : selected) {
            if (player.level() == source.getLevel()) sameWorld.add(player);
        }
        callback.setReturnValue(sameWorld);
    }
}
