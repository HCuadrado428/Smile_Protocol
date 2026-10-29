package com.smileprotocol.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.smileprotocol.event.SPEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Comandos permitidos dentro de la partida (README 7.3). */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void smileprotocol$performCommand(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        List<ParsedCommandNode<CommandSourceStack>> nodes = parse.getContext().getNodes();
        String root = nodes.isEmpty() ? null : nodes.get(0).getNode().getName();
        if (SPEvents.cancelCommand(parse.getContext().getSource(), root)) ci.cancel();
    }
}
