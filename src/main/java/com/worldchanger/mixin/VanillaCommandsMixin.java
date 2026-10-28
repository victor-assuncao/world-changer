package com.worldchanger.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.worldchanger.VanillaPermissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Commands.class)
public abstract class VanillaCommandsMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;onCommandRegister(Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/commands/Commands$CommandSelection;Lnet/minecraft/commands/CommandBuildContext;)V"))
    private void worldchanger$capture(CommandDispatcher<CommandSourceStack> dispatcher,
            Commands.CommandSelection selection, CommandBuildContext context) {
        VanillaPermissions.capture(dispatcher);
        EventHooks.onCommandRegister(dispatcher, selection, context);
    }
}
