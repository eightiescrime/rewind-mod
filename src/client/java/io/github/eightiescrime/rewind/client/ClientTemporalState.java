package io.github.eightiescrime.rewind.client;

import io.github.eightiescrime.rewind.network.TemporalStatePayload;
import org.jetbrains.annotations.Nullable;

/**
 * Последнее, что сказал сервер, плюс прозрачность HUD.
 *
 * <p>HUD «проявляется по делу»: в покое его нет вовсе, он всплывает, когда
 * временем действительно пользуются, и гаснет через несколько секунд после
 * того, как всё вернулось в норму.
 */
public final class ClientTemporalState {

    /** Сколько держать HUD на экране после того, как всё улеглось. */
    private static final long HOLD_MS = 3000L;
    private static final long FADE_OUT_MS = 700L;
    private static final long FADE_IN_MS = 200L;

    @Nullable
    private static TemporalStatePayload current;
    private static long activeSinceMs;
    private static long lastActiveMs;
    private static boolean wasActive;
    private static float lastAlpha;

    private ClientTemporalState() {
    }

    public static void accept(TemporalStatePayload payload) {
        current = payload;
    }

    public static void reset() {
        current = null;
        wasActive = false;
        lastAlpha = 0.0f;
    }

    @Nullable
    public static TemporalStatePayload get() {
        return current;
    }

    /** Есть ли о чём говорить: полная энергия без шрама и кулдаунов — молчим. */
    private static boolean active(TemporalStatePayload state) {
        return state.energyPercent() < 100 || state.scarPercent() > 0
                || state.cooldownTicks() > 0 || state.fractureTicks() > 0;
    }

    /** Прозрачность HUD в этот кадр, 0..1. */
    public static float alpha(long nowMs) {
        TemporalStatePayload state = current;
        if (state == null || !state.unlocked()) {
            return 0.0f;
        }
        boolean active = active(state);
        if (active) {
            if (!wasActive) {
                // если HUD ещё догорал, проявление продолжается с текущей
                // прозрачности, а не прыгает в ноль
                activeSinceMs = nowMs - (long) (lastAlpha * FADE_IN_MS);
            }
            lastActiveMs = nowMs;
            wasActive = true;
            return lastAlpha = Math.min(1.0f, (nowMs - activeSinceMs) / (float) FADE_IN_MS);
        }

        wasActive = false;
        if (lastAlpha == 0.0f) {
            // ничего не показывали и показывать нечего: ни удержания, ни затухания
            return 0.0f;
        }
        long since = nowMs - lastActiveMs;
        if (since < HOLD_MS) {
            return lastAlpha = 1.0f;
        }
        return lastAlpha = Math.max(0.0f, 1.0f - (since - HOLD_MS) / (float) FADE_OUT_MS);
    }
}
