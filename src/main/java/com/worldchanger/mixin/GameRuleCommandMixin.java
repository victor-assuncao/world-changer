package com.worldchanger.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.worldchanger.WorldManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Faz o comando vanilla gamerule usar as regras do ServerLevel atual. */
@Mixin(GameRuleCommand.class)
public abstract class GameRuleCommandMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "setRule", at = @At("HEAD"), cancellable = true)
    private static void worldchanger$setRule(CommandContext<CommandSourceStack> context,
            GameRules.Key key, CallbackInfoReturnable<Integer> cir) {
        CommandSourceStack source = context.getSource();
        if (!WorldManager.isWorldChangerLevel(source.getLevel())) return;
        GameRules.Value value = source.getLevel().getGameRules().getRule(key);
        value.setFromArgument(context, "value");
        source.sendSuccess(() -> Component.translatable("commands.gamerule.set", key.getId(), value.toString()), true);
        cir.setReturnValue(value.getCommandResult());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "queryRule", at = @At("HEAD"), cancellable = true)
    private static void worldchanger$queryRule(CommandSourceStack source, GameRules.Key key,
            CallbackInfoReturnable<Integer> cir) {
        if (!WorldManager.isWorldChangerLevel(source.getLevel())) return;
        GameRules.Value value = source.getLevel().getGameRules().getRule(key);
        source.sendSuccess(() -> Component.translatable("commands.gamerule.query", key.getId(), value.toString()), false);
        cir.setReturnValue(value.getCommandResult());
    }
}
