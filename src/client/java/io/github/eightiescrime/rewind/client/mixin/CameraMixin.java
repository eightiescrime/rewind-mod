package io.github.eightiescrime.rewind.client.mixin;

import io.github.eightiescrime.rewind.client.RewindEffects;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Рывок камеры в момент собственной отмотки.
 *
 * <p>Единственный миксин в моде. События Fabric камеру не отдают, а
 * {@code moveBy} и {@code setPos} у неё защищённые — законный случай по
 * RULE 6. Ничего, кроме смещения уже посчитанной камеры, здесь не делается.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void moveBy(float x, float y, float z);

    @Inject(method = "update", at = @At("TAIL"))
    private void rewind$kick(BlockView area, Entity focusedEntity, boolean thirdPerson,
                             boolean inverseView, float tickDelta, CallbackInfo ci) {
        float kick = RewindEffects.cameraKick();
        if (kick > 0.0f) {
            moveBy(-kick, kick * 0.3f, 0.0f);
        }
    }
}
