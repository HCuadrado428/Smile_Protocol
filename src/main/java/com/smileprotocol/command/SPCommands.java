package com.smileprotocol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Esqueleto de comandos (README 5.1).
 * <ul>
 *   <li>{@code /sp} — jugadores (nivel 0).</li>
 *   <li>{@code /smileprotocol} — administradores (nivel 2): partidas, grupos, modo del lobby...</li>
 *   <li>{@code /spadmin} — herramientas de construccion y depuracion (nivel 2).</li>
 * </ul>
 * De momento cada subcomando responde en que fase se implementa. Cada fase sustituye
 * su stub por la logica real.
 */
public final class SPCommands {

    /** Nivel de permiso de administrador (README 5.1). */
    public static final int ADMIN_LEVEL = 2;

    private SPCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        registerPlayer(d);
        registerAdmin(d);
        registerBuild(d);
    }

    // /sp ----------------------------------------------------------------------

    private static void registerPlayer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("sp")
                .then(Commands.literal("lobby").executes(c -> notYet(c, "F02"))));
    }

    // /smileprotocol -----------------------------------------------------------

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("smileprotocol")
                .requires(s -> s.hasPermission(ADMIN_LEVEL));

        root.then(stub("lobby", "F02"));
        root.then(stub("leave", "F02"));
        root.then(stub("mode", "F05"));
        root.then(stub("start", "F05"));
        root.then(stub("runs", "F06"));
        root.then(stub("newrun", "F06"));
        root.then(stub("groups", "F06"));
        root.then(stub("skipcinematic", "F16"));
        root.then(stub("setlobby", "F05"));
        root.then(stub("zone", "F16"));
        root.then(stub("mission", "F14"));
        root.then(stub("spawn", "F11"));

        d.register(root);
    }

    // /spadmin -----------------------------------------------------------------

    private static void registerBuild(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("spadmin")
                .requires(s -> s.hasPermission(ADMIN_LEVEL));

        root.then(Commands.literal("lobby")
                .then(stub("region", "F05"))
                .then(stub("elevator", "F05")));
        root.then(stub("lock", "F09"));
        root.then(stub("circuit", "F09"));
        root.then(stub("hide", "F10"));
        root.then(Commands.literal("noise").then(stub("test", "F11")));
        root.then(stub("debug", "F03"));

        d.register(root);
    }

    // Utilidades ---------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> stub(String name, String phase) {
        return Commands.literal(name).executes(c -> notYet(c, phase));
    }

    private static int notYet(CommandContext<CommandSourceStack> ctx, String phase) {
        ctx.getSource().sendFailure(Component.translatable("commands.smileprotocol.not_implemented", phase));
        return 0;
    }
}
