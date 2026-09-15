package io.github.eightiescrime.rewind.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

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
    }
}
