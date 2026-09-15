package io.github.eightiescrime.rewind;

import io.github.eightiescrime.rewind.command.RewindCommand;
import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.damage.DamageInterceptor;
import io.github.eightiescrime.rewind.network.RewindNetworking;
import io.github.eightiescrime.rewind.persistence.TemporalAttachments;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import io.github.eightiescrime.rewind.progression.ProgressionManager;
import io.github.eightiescrime.rewind.temporal.TemporalManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа мода «Отмотка».
 *
 * <p>Вся логика способности серверная. Клиент ничего не решает и в фазах 1–3
 * вообще не получает своего кода: обратная связь даётся ванильными средствами
 * сервера.
 *
 * <p>Здесь же собраны все моменты, после которых старые снимки становятся
 * опасными (ТЗ §14, §41): смена измерения, смерть, возрождение и выход.
 * В каждом из них буфер обнуляется — вернуться «из Нижнего мира в Верхний»
 * невозможно не по проверке, а потому, что возвращаться уже некуда.
 */
public final class RewindMod implements ModInitializer {
    public static final String MOD_ID = "rewind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        RewindConfig.loadOrCreate(FabricLoader.getInstance().getConfigDir().resolve("rewind.json"));

        RewindNetworking.register();

        ServerTickEvents.END_SERVER_TICK.register(TemporalManager.INSTANCE::tick);
        DamageInterceptor.register();
        RewindCommand.register();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TemporalManager.INSTANCE.forget(handler.player));

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (RewindConfig.get().dimensionChangeClearsBuffer) {
                TemporalManager.INSTANCE.clearBuffer(player);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            TemporalManager.INSTANCE.forget(oldPlayer);
            TemporalManager.INSTANCE.clearBuffer(newPlayer);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player && RewindConfig.get().deathClearsBuffer) {
                TemporalManager.INSTANCE.clearBuffer(player);
            }
        });

        LOGGER.info("Rewind: временное ядро инициализировано");
    }

    private static void onJoin(ServerPlayerEntity player) {
        // после входа старых снимков не существует — это закрывает целый класс
        // эксплойтов с отмоткой через перезаход (ТЗ §41)
        TemporalManager.INSTANCE.clearBuffer(player);

        if (RewindConfig.get().grantOnFirstJoin) {
            TemporalState state = TemporalAttachments.of(player);
            ProgressionManager.unlock(player, state);
        }
    }
}
