package io.github.eightiescrime.rewind.network;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Держит клиент в курсе временного состояния — но только когда есть что сказать.
 *
 * <p>Пакет уходит не по расписанию, а когда огрублённое состояние отличается от
 * последнего отправленного. В покое это ноль трафика, в бою — несколько десятков
 * байт в секунду на игрока.
 */
public final class TemporalSync {

    /** Реже, чем раз в четверть секунды, HUD всё равно не успевает соврать. */
    private static final int TICKS_BETWEEN_CHECKS = 5;

    private static final Map<UUID, TemporalStatePayload> lastSent = new HashMap<>();

    private TemporalSync() {
    }

    public static void maybeSend(ServerPlayerEntity player, TemporalState state, long tick) {
        if (tick % TICKS_BETWEEN_CHECKS != 0) {
            return;
        }
        TemporalStatePayload payload = snapshot(state);
        if (payload.equals(lastSent.get(player.getUuid()))) {
            return;
        }
        lastSent.put(player.getUuid(), payload);
        ServerPlayNetworking.send(player, payload);
    }

    public static void forget(ServerPlayerEntity player) {
        lastSent.remove(player.getUuid());
    }

    private static TemporalStatePayload snapshot(TemporalState state) {
        RewindConfig config = RewindConfig.get();
        int percent = (int) Math.round(100.0 * state.energy / Math.max(1.0, config.maxEnergy));
        float debt = Math.round(state.debtSeconds * 10.0) / 10.0f;
        return new TemporalStatePayload(
                state.unlocked,
                state.level,
                Math.clamp(percent, 0, 100),
                debt,
                Math.max(state.autoCooldown, state.manualCooldown),
                state.fractureTicks);
    }
}
