package com.smileprotocol.mixin.client;

import com.smileprotocol.client.ClientMotion;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camara dentro de la partida (README 12.5, 12.6): mirar atras suma 180° al yaw (solo la
 * camara, no la direccion de movimiento) y asomarse desplaza la camara a un lado y la baja un
 * poco en el dive y el vault.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow public abstract Vec3 getPosition();
    @Shadow public abstract float getYRot();
    @Shadow public abstract float getXRot();
    @Shadow private boolean detached;

    @Inject(method = "setup", at = @At("TAIL"))
    private void smileprotocol$setup(BlockGetter level, Entity entity, boolean detached, boolean reverse, float pt, CallbackInfo ci) {
        Vec3 testCam = com.smileprotocol.client.ClientSelfTest.TEST_CAM_POS;
        if (testCam != null) {
            // Grabacion de animaciones: camara fija, o siguiendo al jugador o a un mob
            Entity follow = com.smileprotocol.client.ClientSelfTest.followed();
            if (follow != null) {
                Vec3 b = follow.getPosition(pt);
                Vec3 cam = b.add(com.smileprotocol.client.ClientSelfTest.TEST_FOLLOW_OFFSET);
                Vec3 look = b.add(0, com.smileprotocol.client.ClientSelfTest.TEST_FOLLOW_LOOK_Y, 0);
                double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
                setPosition(cam);
                setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)),
                        (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
                return;
            }
            setPosition(testCam);
            setRotation(com.smileprotocol.client.ClientSelfTest.TEST_CAM_YAW, com.smileprotocol.client.ClientSelfTest.TEST_CAM_PITCH);
            return;
        }
        if (!this.detached) {
            // Remate o muerte instantanea: la camara la lleva la secuencia (README 9.3)
            float[] seq = com.smileprotocol.client.ClientSequence.camera(getPosition(), pt);
            if (seq != null) {
                setRotation(seq[0], seq[1]);
                if (seq[2] > 0) {
                    float y = seq[0] * Mth.DEG_TO_RAD, x = seq[1] * Mth.DEG_TO_RAD;
                    Vec3 look = new Vec3(-Mth.sin(y) * Mth.cos(x), -Mth.sin(x), Mth.cos(y) * Mth.cos(x));
                    setPosition(getPosition().subtract(look.scale(seq[2])));
                }
                com.smileprotocol.client.light.Flashlight.update((Camera) (Object) this, pt);
                return;
            }
        }
        com.smileprotocol.client.light.Flashlight.update((Camera) (Object) this, pt);
        if (this.detached) return;
        float scare = com.smileprotocol.client.ClientScare.shake(pt);
        if (scare > 0) {
            float t = com.smileprotocol.client.ClientScare.time(pt);
            setRotation(getYRot() + Mth.sin(t * 2.7f) * scare, getXRot() + Mth.cos(t * 3.3f) * scare * 0.7f);
        }
        float yawOffset = ClientMotion.cameraYawOffset(entity, pt);
        float lean = ClientMotion.cameraLeanOffset(pt);
        float dip = ClientMotion.cameraDip(pt);
        if (yawOffset != 0) setRotation(getYRot() + yawOffset, getXRot());
        if (lean != 0 || dip != 0) {
            float yaw = entity.getViewYRot(pt) * Mth.DEG_TO_RAD;
            // Izquierda del cuerpo: (cos yaw, 0, sin yaw); lean < 0 = izquierda
            Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
            setPosition(getPosition().add(left.scale(-lean)).add(0, dip, 0));
        }
    }
}
