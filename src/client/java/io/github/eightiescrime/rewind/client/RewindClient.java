package io.github.eightiescrime.rewind.client;

import io.github.eightiescrime.rewind.item.RewindItems;
import io.github.eightiescrime.rewind.network.JournalPayload;
import io.github.eightiescrime.rewind.network.RewindEffectPayload;
import io.github.eightiescrime.rewind.network.TemporalStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import io.github.eightiescrime.rewind.client.mixin.ModelPredicateRegistryInvoker;
import net.minecraft.client.item.CompassAnglePredicateProvider;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.util.Identifier;

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
        // клавиша должна встать в GameOptions до того, как те соберутся,
        // поэтому регистрация идёт первой строкой и только отсюда
        ManualTimeline.register();

        // своей стрелки у мода нет: положение разлома лежит в ванильном
        // компоненте лодстоуна, и крутит её тот же предикат, что у компаса
        ModelPredicateRegistryInvoker.rewind$register(RewindItems.TEMPORAL_COMPASS,
                Identifier.ofVanilla("angle"), new CompassAnglePredicateProvider((world, stack, entity) -> {
                    LodestoneTrackerComponent tracker = stack.get(DataComponentTypes.LODESTONE_TRACKER);
                    return tracker == null ? null : tracker.target().orElse(null);
                }));

        ClientPlayNetworking.registerGlobalReceiver(TemporalStatePayload.ID,
                (payload, context) -> context.client().execute(() -> ClientTemporalState.accept(payload)));

        ClientPlayNetworking.registerGlobalReceiver(RewindEffectPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> RewindEffects.accept(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(JournalPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> context.client().setScreen(new JournalScreen(payload))));

        // при выходе из мира состояние клиента должно исчезнуть, иначе HUD
        // покажет чужие цифры, а силуэты — чужие отмотки на следующем сервере
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientTemporalState.reset();
            RewindEffects.reset();
            ManualTimeline.reset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(ManualTimeline::tick);

        HudRenderCallback.EVENT.register(TemporalHud::render);
        WorldRenderEvents.AFTER_ENTITIES.register(RewindEffects::render);
    }
}
