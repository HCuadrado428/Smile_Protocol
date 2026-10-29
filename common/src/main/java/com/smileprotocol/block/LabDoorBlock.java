package com.smileprotocol.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.smileprotocol.blockentity.LabDoorBlockEntity;
import com.smileprotocol.circuit.CircuitManager;
import com.smileprotocol.client.anim.PoseCalculator;
import com.smileprotocol.interact.InteractionSpec;
import com.smileprotocol.interact.SPInteractable;
import com.smileprotocol.interact.SPInventoryOps;
import com.smileprotocol.item.KeyItem;
import com.smileprotocol.item.KeycardItem;
import com.smileprotocol.mechanism.DoorType;
import com.smileprotocol.mechanism.LabEvents;
import com.smileprotocol.mechanism.ValveGroups;
import com.smileprotocol.noise.NoiseSystem;
import com.smileprotocol.noise.NoiseType;
import com.smileprotocol.player.SPPlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Puerta del laboratorio (README 14.3). Dentro de la partida se abre con interaccion mantenida
 * segun su tipo: normal, con llave, con tarjeta, electrica, hidraulica o atascada.
 */
public class LabDoorBlock extends DoorBlock implements EntityBlock, SPInteractable {

    public static final MapCodec<LabDoorBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockSetType.CODEC.fieldOf("block_set_type").forGetter(DoorBlock::type),
            propertiesCodec()).apply(i, LabDoorBlock::new));

    public LabDoorBlock(BlockSetType type, Properties props) {
        super(type, props);
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new LabDoorBlockEntity(pos, state) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Dentro se usa con el click derecho mantenido; fuera solo las normales, para construir
        if (SPPlayerData.inSP(player)) return InteractionResult.PASS;
        LabDoorBlockEntity be = config(level, pos, state);
        if (be != null && be.type() != DoorType.NORMAL && !player.isCreative()) return InteractionResult.PASS;
        boolean open = !state.getValue(OPEN);
        setOpen(player, level, state, pos, open);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockPos root(Level level, BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Nullable
    public static LabDoorBlockEntity config(Level level, BlockPos pos, BlockState state) {
        BlockPos root = state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return level.getBlockEntity(root) instanceof LabDoorBlockEntity be ? be : null;
    }

    /** Abre o cierra la puerta de esa posicion (cualquier mitad). */
    public static void setOpenAt(Level level, BlockPos pos, boolean open) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof DoorBlock door)) return;
        if (st.getValue(OPEN) == open) return;
        door.setOpen(null, level, st, pos, open);
    }

    private static boolean hasKey(Player p, String lock, boolean client) {
        return SPInventoryOps.find(p, s -> s.getItem() instanceof KeyItem && KeyItem.lock(s).equals(lock), client) >= 0;
    }

    private static boolean hasCard(Player p, int level, boolean client) {
        return SPInventoryOps.find(p, s -> s.getItem() instanceof KeycardItem k && k.level() >= level, client) >= 0;
    }

    @Nullable
    @Override
    public InteractionSpec spec(Level level, BlockPos pos, BlockState state, Player player) {
        LabDoorBlockEntity be = config(level, pos, state);
        DoorType type = be == null ? DoorType.NORMAL : be.type();
        boolean open = state.getValue(OPEN);
        boolean client = level.isClientSide;
        boolean free = be == null || be.unlocked() || type == DoorType.NORMAL;

        if (free || open) {
            if (open && (type == DoorType.ELECTRIC || type == DoorType.HYDRAULIC)) {
                return null; // las mueve su mecanismo
            }
            Component prompt = Component.translatable(open ? "smileprotocol.door.close" : "smileprotocol.door.open");
            // Agachado: lenta y sin ruido (README 14.3)
            if (player.isCrouching()) return InteractionSpec.of(prompt, 24, 0, PoseCalculator.INTERACT_DOOR);
            return InteractionSpec.of(prompt, 8, 6, PoseCalculator.INTERACT_DOOR);
        }
        return switch (type) {
            case LOCKED -> hasKey(player, be.param(), client)
                    ? InteractionSpec.of(Component.translatable("smileprotocol.door.unlock", KeyItem.lockName(be.param())), 20, 5,
                    PoseCalculator.INTERACT_DOOR)
                    : InteractionSpec.blocked(Component.translatable("smileprotocol.door.locked", KeyItem.lockName(be.param())));
            case KEYCARD -> hasCard(player, be.level(), client)
                    ? InteractionSpec.of(Component.translatable("smileprotocol.door.card"), 12, 3, PoseCalculator.INTERACT_CARD)
                    : InteractionSpec.blocked(Component.translatable("smileprotocol.door.need_card", be.level()));
            case ELECTRIC -> InteractionSpec.blocked(Component.translatable("smileprotocol.door.no_power"));
            case HYDRAULIC -> InteractionSpec.blocked(Component.translatable("smileprotocol.door.pressure"));
            case STUCK -> InteractionSpec.of(Component.translatable("smileprotocol.door.stuck"), 60, 12,
                    PoseCalculator.INTERACT_DOOR).coop(2);
            default -> null;
        };
    }

    @Override
    public void complete(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        LabDoorBlockEntity be = config(level, pos, state);
        DoorType type = be == null ? DoorType.NORMAL : be.type();
        boolean open = state.getValue(OPEN);
        if (be != null && !be.unlocked() && !open && type != DoorType.NORMAL) {
            switch (type) {
                case LOCKED -> {
                    int slot = SPInventoryOps.find(player, s -> s.getItem() instanceof KeyItem && KeyItem.lock(s).equals(be.param()));
                    if (slot < 0) return;
                    if (be.consume()) SPInventoryOps.take(player, slot);
                    level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, 1.8f);
                }
                case KEYCARD -> {
                    if (!hasCard(player, be.level(), false)) return;
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
                }
                case STUCK -> level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 0.9f, 0.7f);
                default -> {
                    return;
                }
            }
            be.unlock();
            setOpen(player, level, state, pos, true);
            LabEvents.fire(level, pos, player, type == DoorType.STUCK ? "door_forced" : "door_opened", be.param());
            return;
        }
        setOpen(player, level, state, pos, !open);
        LabEvents.fire(level, pos, player, open ? "door_closed" : "door_opened", be == null ? "" : be.param());
    }

    /** Al colocar una zona: electricas segun su circuito e hidraulicas segun sus valvulas. */
    public static void refresh(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        LabDoorBlockEntity be = config(level, pos, st);
        if (be == null) return;
        if (be.type() == DoorType.ELECTRIC) setOpenAt(level, pos, CircuitManager.isPowered(level, pos, be.param()));
        if (be.type() == DoorType.HYDRAULIC) setOpenAt(level, pos, ValveGroups.isComplete(level, pos, be.param()));
    }

    /** Ruido de una puerta movida por un mob (fase 11). */
    public static void noise(ServerLevel level, BlockPos pos, float r) {
        NoiseSystem.emit(level, Vec3.atCenterOf(pos), r, NoiseType.DOOR, null);
    }

    static boolean unused(ItemStack s) {
        return s.isEmpty();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (!level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof com.smileprotocol.blockentity.AnimatedBlockEntity a) {
                com.smileprotocol.blockentity.AnimatedBlockEntity.clientTick(lvl, pos, st, a);
            }
        };
    }
}
