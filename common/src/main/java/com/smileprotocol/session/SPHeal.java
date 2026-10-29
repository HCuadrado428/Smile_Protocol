package com.smileprotocol.session;

import net.minecraft.world.entity.LivingEntity;

/**
 * Dentro de la partida no hay regeneracion natural (README 9.2): la curacion se bloquea
 * salvo la que hace el propio mod, que pasa por aqui.
 */
public final class SPHeal {

    private static final ThreadLocal<Boolean> ALLOWED = ThreadLocal.withInitial(() -> false);

    private SPHeal() {}

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
