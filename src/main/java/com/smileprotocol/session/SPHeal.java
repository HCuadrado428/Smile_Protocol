package com.smileprotocol.session;

import net.minecraft.world.entity.LivingEntity;

/**
 * Dentro de la partida no hay regeneracion natural (README 9.2): {@code LivingHealEvent} se
 * cancela salvo para las curaciones que hace el propio mod, que pasan por aqui.
 */
public final class SPHeal {

    private static final ThreadLocal<Boolean> ALLOWED = ThreadLocal.withInitial(() -> false);

    private SPHeal() {}

    /** Cura a la entidad saltandose el bloqueo de regeneracion. */
    public static void heal(LivingEntity entity, float amount) {
        ALLOWED.set(true);
        try {
            entity.heal(amount);
        } finally {
            ALLOWED.set(false);
        }
    }

    public static boolean isAllowed() {
        return ALLOWED.get();
    }
}
