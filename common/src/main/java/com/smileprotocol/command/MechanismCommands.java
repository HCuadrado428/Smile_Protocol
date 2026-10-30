package com.smileprotocol.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.smileprotocol.block.HideBlock;
import com.smileprotocol.block.LabDoorBlock;
import com.smileprotocol.blockentity.FluorescentLightBlockEntity;
import com.smileprotocol.blockentity.FuseBoxBlockEntity;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.blockentity.ValveBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.interact.SPInventoryOps;
import com.smileprotocol.mechanism.DoorType;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import com.smileprotocol.registry.ModBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Comandos de construccion de mecanismos y escondites, mirando un bloque (README 5.1). */
final class MechanismCommands {

    static final SuggestionProvider<CommandSourceStack> DOOR_TYPES = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(DoorType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), b);
    static final SuggestionProvider<CommandSourceStack> HIDE_TYPES = (c, b) ->
            SharedSuggestionProvider.suggest(new String[]{"locker", "wardrobe", "container", "gurney", "curtain"}, b);
    static final SuggestionProvider<CommandSourceStack> GIVE_ITEMS = (c, b) ->
            SharedSuggestionProvider.suggest(new String[]{"fuse", "fuse_blown", "key", "keycard", "wheel", "medkit", "battery", "flare", "adrenaline", "can", "bottle"}, b);

    private static final Map<String, Supplier<HideBlock>> HIDES = Map.of(
            "locker", ModBlocks.LOCKER, "wardrobe", ModBlocks.WARDROBE, "container", ModBlocks.WASTE_CONTAINER,
            "gurney", ModBlocks.GURNEY, "curtain", ModBlocks.CURTAIN);

    private MechanismCommands() {}

    @Nullable
    private static BlockPos looked(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HitResult hr = p.pick(8.0, 0f, false);
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) return bh.getBlockPos();
        ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.look_block"));
        return null;
    }

    private static BlockEntity rootEntity(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof LabDoorBlock && st.getValue(LabDoorBlock.HALF) == DoubleBlockHalf.UPPER) pos = pos.below();
        return level.getBlockEntity(pos);
    }

    static int lockSet(CommandContext<CommandSourceStack> ctx, boolean consume) throws CommandSyntaxException {
        BlockPos pos = looked(ctx);
        if (pos == null) return 0;
        String lock = StringArgumentType.getString(ctx, "lock_id");
        if (rootEntity(ctx.getSource().getLevel(), pos) instanceof LabDoorBlockEntity d) {
            d.configure(DoorType.LOCKED, lock, consume);
            ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.lock.set", lock), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.not_door"));
        return 0;
    }

    static int circuitSet(CommandContext<CommandSourceStack> ctx, int fuses) throws CommandSyntaxException {
        BlockPos pos = looked(ctx);
        if (pos == null) return 0;
        String circuit = StringArgumentType.getString(ctx, "circuit_id");
        ServerLevel level = ctx.getSource().getLevel();
        BlockEntity be = rootEntity(level, pos);
        if (be instanceof FuseBoxBlockEntity fb) {
            fb.configure(circuit, fuses < 0 ? fb.slots() : fuses);
        } else if (be instanceof LabDoorBlockEntity d) {
            d.configure(DoorType.ELECTRIC, circuit, false);
            LabDoorBlock.refresh(level, be.getBlockPos());
        } else if (be instanceof FluorescentLightBlockEntity fl) {
            fl.setCircuit(circuit);
            fl.onCircuit(level, CircuitManager.isPowered(level, pos, circuit));
        } else {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.not_circuit"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.circuit.set", circuit), true);
        return 1;
    }

    static int doorSet(CommandContext<CommandSourceStack> ctx, String param) throws CommandSyntaxException {
        BlockPos pos = looked(ctx);
        if (pos == null) return 0;
        DoorType type = DoorType.parse(StringArgumentType.getString(ctx, "type"));
        ServerLevel level = ctx.getSource().getLevel();
        if (rootEntity(level, pos) instanceof LabDoorBlockEntity d) {
            d.configure(type, param, false);
            LabDoorBlock.refresh(level, d.getBlockPos());
            ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.door.set",
                    type.name().toLowerCase(Locale.ROOT), param), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.not_door"));
        return 0;
    }

    static int valveSet(CommandContext<CommandSourceStack> ctx, int order, boolean coop) throws CommandSyntaxException {
        BlockPos pos = looked(ctx);
        if (pos == null) return 0;
        String group = StringArgumentType.getString(ctx, "group");
        if (ctx.getSource().getLevel().getBlockEntity(pos) instanceof ValveBlockEntity v) {
            v.configure(group, order, coop);
            ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.valve.set", group, order), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.not_valve"));
        return 0;
    }

    /** Sustituye el bloque mirado por un escondite (README 15.6). */
    static int hideSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = looked(ctx);
        if (pos == null) return 0;
        String type = StringArgumentType.getString(ctx, "type");
        Supplier<HideBlock> block = HIDES.get(type);
        if (block == null) {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.hide.unknown", type));
            return 0;
        }
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        Direction facing = p.getDirection().getOpposite();
        HideBlock hb = block.get();
        BlockState lower = hb.defaultBlockState().setValue(HideBlock.FACING, facing);
        level.setBlock(pos, lower, Block.UPDATE_ALL);
        if (hb.isTall()) level.setBlock(pos.above(), lower.setValue(HideBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.hide.set", type), true);
        return 1;
    }

    /** Mete un objeto de la partida en los 4 huecos (para probar). */
    static int give(CommandContext<CommandSourceStack> ctx, String param) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!SPPlayerData.inSP(p)) {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.give.in_game"));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "item");
        ItemStack stack = com.smileprotocol.instance.ZoneSetupAccess.item(param.isEmpty() ? id : id + ":" + param);
        if (stack.isEmpty() || !SPInventoryOps.give(p, stack)) {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.interact.full"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.give.done", stack.getHoverName()), false);
        return 1;
    }

    static int noiseTest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        float r = FloatArgumentType.getFloat(ctx, "radius");
        NoiseSystem.emit(p.serverLevel(), p.position(), r, NoiseType.TEST, p);
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.noise.test", r), false);
        return 1;
    }

    static int mobSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String type = StringArgumentType.getString(ctx, "type");
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        switch (type) {
            case "berserker" -> com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.BERSERKER.get(), pos, level.random);
            case "smiler_test" -> com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.SMILER_TEST.get(), pos, level.random);
            case "smiler_kill" -> com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.SMILER_KILL.get(), pos, level.random);
            case "paciente" -> com.smileprotocol.entity.MobManager.spawn(level, com.smileprotocol.registry.ModEntities.PACIENTE.get(), pos, level.random);
            default -> {
                ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.mob.unknown", type));
                return 0;
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.mob.spawned", type), true);
        return 1;
    }

    static int mobClear(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.world.phys.Vec3 c = ctx.getSource().getPosition();
        int n = 0;
        for (com.smileprotocol.entity.SPMonster m : level.getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class,
                new net.minecraft.world.phys.AABB(c, c).inflate(96))) {
            m.discard();
            n++;
        }
        int k = n;
        ctx.getSource().sendSuccess(() -> Component.literal("Mobs quitados: " + k), true);
        return n;
    }

    static int selfDown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        com.smileprotocol.downed.DownedManager.down(ctx.getSource().getPlayerOrException(), "comando");
        return 1;
    }

    /** {@code /spadmin player <jugador> down|revive|kill}. */
    static int playerState(CommandContext<CommandSourceStack> ctx, String what) throws CommandSyntaxException {
        ServerPlayer p = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target");
        if (!SPPlayerData.inSP(p)) {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.player.not_in_sp", p.getDisplayName()));
            return 0;
        }
        switch (what) {
            case "down" -> com.smileprotocol.downed.DownedManager.down(p, "comando");
            case "revive" -> com.smileprotocol.downed.DownedManager.revive(p, null);
            default -> com.smileprotocol.downed.DeathManager.die(p, "comando");
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.player." + what, p.getDisplayName()), true);
        return 1;
    }

    /**
     * {@code /spadmin mob execute|kill <jugador>}: el mob mas cercano al jugador empieza su remate
     * (derribandolo antes si hace falta) o su muerte instantanea.
     */
    static int mobSequence(CommandContext<CommandSourceStack> ctx, boolean execution) throws CommandSyntaxException {
        ServerPlayer p = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target");
        com.smileprotocol.entity.SPMonster mob = p.serverLevel().getEntitiesOfClass(com.smileprotocol.entity.SPMonster.class,
                        p.getBoundingBox().inflate(24), m -> m.isAlive()).stream()
                .min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(p))).orElse(null);
        if (mob == null) {
            ctx.getSource().sendFailure(Component.translatable("smileprotocol.command.mob.none"));
            return 0;
        }
        boolean ok;
        if (execution) {
            com.smileprotocol.downed.DownedManager.down(p, "comando");
            ok = com.smileprotocol.sequence.SequenceManager.start(mob, p, mob.def().executionSequence());
        } else {
            ok = com.smileprotocol.sequence.SequenceManager.start(mob, p, mob.def().killSequence());
        }
        if (ok) ctx.getSource().sendSuccess(() -> Component.translatable("smileprotocol.command.mob.execute", mob.getDisplayName(), p.getDisplayName()), true);
        return ok ? 1 : 0;
    }
}
