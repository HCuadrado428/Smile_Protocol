package com.smileprotocol.blockentity;

import com.smileprotocol.block.ElevatorDoorBlock;
import com.smileprotocol.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hoja de puerta de ascensor animada con GeckoLib (README 6.2). Vive en la mitad inferior.
 * <p>
 * Al abrirse, la hoja se desliza de lado hasta esconderse en el marco. En puertas de mas de
 * dos hojas el deslizamiento es telescopico: cada hoja recorre la distancia hasta su lado del
 * marco ({@link #slide}, en bloques, positivo hacia {@code FACING.getCounterClockWise()}).
 */
public class ElevatorDoorBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** Ticks que tarda en abrirse o cerrarse del todo. */
    private static final float SPEED = 1f / 14f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int slide = 1;
    private float progress = -1f;
    private float prevProgress;

    public ElevatorDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELEVATOR_DOOR.get(), pos, state);
    }

    public int getSlide() {
        return slide;
    }

    public void setSlide(int slide) {
        if (this.slide == slide) return;
        this.slide = slide;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private boolean targetOpen() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof ElevatorDoorBlock && s.getValue(ElevatorDoorBlock.OPEN);
    }

    /** Cliente: avanza la animacion hacia el estado de la puerta. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, ElevatorDoorBlockEntity be) {
        float target = be.targetOpen() ? 1f : 0f;
        if (be.progress < 0f) be.progress = target; // al cargar, sin animacion
        be.prevProgress = be.progress;
        if (be.progress < target) be.progress = Math.min(target, be.progress + SPEED);
        else if (be.progress > target) be.progress = Math.max(target, be.progress - SPEED);
    }

    /** Apertura de 0 (cerrada) a 1 (abierta), suavizada. */
    public float openAmount(float partialTick) {
        float p = progress < 0f ? (targetOpen() ? 1f : 0f) : prevProgress + (progress - prevProgress) * partialTick;
        return p * p * (3f - 2f * p);
    }

    /**
     * Reparte los recorridos de las hojas de una misma puerta: las de un lado van hacia un
     * lado del marco y las del otro hacia el otro. Con hojas impares, la central va al lado
     * positivo.
     */
    public static void assignSlides(Level level, List<BlockPos> doors) {
        List<ElevatorDoorBlockEntity> leaves = new ArrayList<>();
        Direction ccw = null;
        for (BlockPos p : doors) {
            if (level.getBlockEntity(p) instanceof ElevatorDoorBlockEntity be) {
                leaves.add(be);
                if (ccw == null) ccw = be.getBlockState().getValue(ElevatorDoorBlock.FACING).getCounterClockWise();
            }
        }
        if (leaves.isEmpty()) return;
        final Direction axis = ccw;
        leaves.sort(Comparator.comparingInt(be -> be.getBlockPos().getX() * axis.getStepX() + be.getBlockPos().getZ() * axis.getStepZ()));
        int n = leaves.size();
        for (int i = 0; i < n; i++) {
            leaves.get(i).setSlide(i < n / 2 ? -(i + 1) : n - i);
        }
    }

    // GeckoLib -------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // El deslizamiento lo calcula el renderer a partir de openAmount(); no hay animaciones fijas.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // Guardado y sincronizacion ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Slide", slide);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Slide")) slide = tag.getInt("Slide");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Slide", slide);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // Dos bloques de alto y hasta tres de recorrido lateral
        return new AABB(worldPosition).inflate(3.5, 0, 3.5).expandTowards(0, 1, 0);
    }
}
