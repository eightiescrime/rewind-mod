package io.github.eightiescrime.rewind.client;

import io.github.eightiescrime.rewind.network.TemporalStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Точка входа клиентской части.
 *
 * <p>Клиент ничего не решает: он получает готовое состояние от сервера и
 * показывает его. Ни одной проверки баланса здесь нет и быть не должно —
 * иначе изменённый клиент смог бы отматываться сам (ТЗ §30).
 */
public final class RewindClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(TemporalStatePayload.ID,
                (payload, context) -> context.client().execute(() -> ClientTemporalState.accept(payload)));

        // при выходе из мира состояние клиента должно исчезнуть, иначе HUD
        // покажет чужие цифры на следующем сервере
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientTemporalState.reset());

        HudRenderCallback.EVENT.register(TemporalHud::render);
    }
}
