package io.github.eightiescrime.rewind.network;

import io.github.eightiescrime.rewind.temporal.ManualRewind;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Регистрация типов пакетов. Вызывается на обеих сторонах: регистрация типа
 * ничего не рисует и ничего не отправляет, она только объявляет формат.
 */
public final class RewindNetworking {

    private RewindNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TemporalStatePayload.ID, TemporalStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RewindEffectPayload.ID, RewindEffectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(JournalPayload.ID, JournalPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RewindRequestPayload.ID, RewindRequestPayload.CODEC);
    }

    /**
     * Приём того единственного, что клиенту позволено просить.
     *
     * <p>Живёт отдельно от {@link #register()} и вызывается только серверной
     * точкой входа: на клиенте этот обработчик не нужен и не должен грузиться.
     */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(RewindRequestPayload.ID, (payload, context) ->
                context.server().execute(() -> ManualRewind.handle(context.player(), payload.seconds())));
    }
}
