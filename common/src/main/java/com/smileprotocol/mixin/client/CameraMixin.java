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
            // Grabacion de animaciones: camara fija mirando al jugador o al mob
            setPosition(testCam);
            setRotation(com.smileprotocol.client.ClientSelfTest.TEST_CAM_YAW, com.smileprotocol.client.ClientSelfTest.TEST_CAM_PITCH);
            return;
        }
        com.smileprotocol.client.light.Flashlight.update((Camera) (Object) this, pt);
        if (this.detached) return;
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
