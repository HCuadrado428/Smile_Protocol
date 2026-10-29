package com.smileprotocol.interact;

import net.minecraft.network.chat.Component;

/**
 * Como se usa algo (README 11): texto del prompt, tiempo de mantenido (0 = instantaneo), ruido,
 * si se cancela al moverse y cuantos jugadores hacen falta a la vez. Si {@code blocked} es true,
 * el prompt explica por que no se puede (falta la llave, no hay corriente...).
 *
 * @param anim tipo de animacion ({@code PoseCalculator.INTERACT_*})
 */
public record InteractionSpec(Component prompt, int ticks, float noise, boolean cancelOnMove, int minPlayers,
                              int anim, boolean blocked) {

    public static InteractionSpec of(Component prompt, int ticks, float noise, int anim) {
        return new InteractionSpec(prompt, ticks, noise, true, 1, anim, false);
    }

    public static InteractionSpec blocked(Component reason) {
        return new InteractionSpec(reason, 0, 0, true, 1, 0, true);
    }

    public InteractionSpec coop(int min) {
        return new InteractionSpec(prompt, ticks, noise, cancelOnMove, min, anim, blocked);
    }
}
