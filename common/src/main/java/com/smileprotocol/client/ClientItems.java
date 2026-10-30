package com.smileprotocol.client;

import com.smileprotocol.interact.InteractionSpec;
import com.smileprotocol.item.ConsumableItem;
import com.smileprotocol.item.ThrowableItem;
import com.smileprotocol.network.Payloads;
import com.smileprotocol.network.SPNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Objeto de la mano en el cliente (README 11.1, punto 2): si no hay nada interactuable delante, el
 * click derecho mantenido usa el consumible o apunta el lanzable (con una trayectoria discreta de
 * puntos) y el click izquierdo lo lanza. El servidor valida y lleva el progreso.
 */
public final class ClientItems {

    private static final DustParticleOptions DOT = new DustParticleOptions(new Vector3f(0.85f, 0.82f, 0.7f), 0.45f);
    private static final DustParticleOptions HIT = new DustParticleOptions(new Vector3f(0.9f, 0.25f, 0.2f), 0.8f);
    /** Fuerza y gravedad para la prevision (las mismas que el proyectil del servidor). */
    private static final double SPEED = 1.1, GRAVITY = 0.04, DRAG = 0.99;

    private static boolean using;
    private static boolean aiming;
    private static int slot = -1;
    private static int aimTicks;

    private ClientItems() {}

    public static boolean aiming() {
        return aiming;
    }

    public static void reset() {
        using = false;
        aiming = false;
        slot = -1;
    }

    /** Si lo que hay en la mano se puede usar ahora: el prompt (o null). */
    @Nullable
    public static InteractionSpec prompt(boolean downed) {
        ItemStack st = ClientState.slot(ClientState.selected());
        if (st.getItem() instanceof ConsumableItem) {
            return InteractionSpec.of(Component.translatable("smileprotocol.use.prompt", st.getHoverName()), 1, 0, 0);
        }
        if (st.getItem() instanceof ThrowableItem && !downed) {
            return InteractionSpec.of(Component.translatable(aiming ? "smileprotocol.use.aim" : "smileprotocol.use.prompt",
                    st.getHoverName()), 1, 0, 0);
        }
        return null;
    }

    /** Cada tick, cuando no hay nada interactuable delante. */
    public static void tick(Minecraft mc, LocalPlayer player, boolean useDown, boolean downed) {
        int sel = ClientState.selected();
        ItemStack st = ClientState.slot(sel);
        boolean consumable = st.getItem() instanceof ConsumableItem;
        boolean throwable = st.getItem() instanceof ThrowableItem && !downed;

        if (consumable && useDown) {
            if (!using || slot != sel) {
                using = true;
                slot = sel;
                SPNetwork.sendToServer(new Payloads.UseItem(Payloads.UseItem.START, sel, 0, 0));
            }
        } else if (using) {
            using = false;
            SPNetwork.sendToServer(new Payloads.UseItem(Payloads.UseItem.STOP, slot, 0, 0));
        }

        if (throwable && useDown && !player.isSprinting()) {
            if (!aiming || slot != sel) {
                aiming = true;
                slot = sel;
                aimTicks = 0;
                SPNetwork.sendToServer(new Payloads.UseItem(Payloads.UseItem.AIM, sel, 0, 0));
            }
            if (aimTicks++ % 3 == 0) trajectory(mc, player);
        } else if (aiming) {
            aiming = false;
            SPNetwork.sendToServer(new Payloads.UseItem(Payloads.UseItem.AIM_STOP, slot, 0, 0));
        }
    }

    /** Click izquierdo mientras se apunta (lo llama el mixin de {@code startAttack}). */
    public static boolean tryThrow() {
        Minecraft mc = Minecraft.getInstance();
        if (!aiming || mc.player == null) return false;
        aiming = false;
        SPNetwork.sendToServer(new Payloads.UseItem(Payloads.UseItem.THROW, slot, mc.player.getYRot(), mc.player.getXRot()));
        return true;
    }

    /** Puntos de la trayectoria hasta donde caeria (cada pocos ticks, se desvanecen solos). */
    private static void trajectory(Minecraft mc, LocalPlayer p) {
        if (mc.level == null) return;
        Vec3 pos = p.getEyePosition().add(0, -0.1, 0);
        Vec3 vel = p.getLookAngle().scale(SPEED);
        for (int i = 0; i < 80; i++) {
            Vec3 next = pos.add(vel);
            BlockHitResult hit = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 at = hit.getLocation();
                mc.level.addParticle(HIT, at.x, at.y + 0.05, at.z, 0, 0, 0);
                return;
            }
            if (i % 2 == 1) mc.level.addParticle(DOT, next.x, next.y, next.z, 0, 0, 0);
            pos = next;
            vel = vel.scale(DRAG).add(0, -GRAVITY, 0);
        }
    }
}
