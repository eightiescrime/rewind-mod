package io.github.eightiescrime.rewind.client.mixin;

import io.github.eightiescrime.rewind.client.ManualTimeline;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Колёсико достаётся шкале времени, пока она открыта.
 *
 * <p>Законный случай для миксина по RULE 6: событий прокрутки колёсика
 * Fabric не даёт вовсе, а без перехвата выбор глубины заодно перебирал бы
 * слоты хотбара. Инъекция уходит сразу же, как шкала закрывается: в остальное
 * время метод отрабатывает ванильно, будто мода нет.
 */
@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void rewind$timelineScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ManualTimeline.scroll(vertical)) {
            ci.cancel();
        }
    }
}
