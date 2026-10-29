package com.smileprotocol.noise;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Un ruido (README 17.1): donde, con que radio, de que tipo y quien lo hizo. Se guarda en la
 * instancia donde ocurre para que los mobs de esa instancia lo oigan (fase 11).
 */
public record NoiseEvent(Vec3 pos, float radius, NoiseType type, @Nullable UUID source, long gameTime) {
}
