package io.github.eightiescrime.rewind.temporal;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.feedback.ServerFeedback;
import io.github.eightiescrime.rewind.persistence.TemporalAttachments;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Приём просьбы об отмотке от клиента (ТЗ §23).
 *
 * <p>Ровно то место, где кончается доверие клиенту. Всё, что пришло по сети, —
 * это одно число, и оно проходит те же проверки, что и автоматическая отмотка,
 * плюс собственный потолок глубины.
 */
public final class ManualRewind {

    /**
     * Чаще этого запросы не рассматриваются.
     *
     * <p>Кулдаун из конфига отсекает успешные отмотки, но не отказы: без этой
     * заслонки изменённый клиент мог бы слать запросы каждый тик и заставлять
     * сервер искать безопасный снимок двадцать раз в секунду.
     */
    private static final int MIN_TICKS_BETWEEN_REQUESTS = 5;

    private ManualRewind() {
    }

    public static void handle(ServerPlayerEntity player, int requestedSeconds) {
        RewindConfig config = RewindConfig.get();
        TemporalState state = TemporalAttachments.of(player);

        int sinceLast = player.age - state.lastManualRequestAge;
        if (sinceLast >= 0 && sinceLast < MIN_TICKS_BETWEEN_REQUESTS) {
            return;
        }
        state.lastManualRequestAge = player.age;

        // клиент прислал подсказку, а не приказ: глубину назначает сервер
        double seconds = Math.clamp(requestedSeconds, 1, (int) config.manualMaxSeconds);

        Vec3d from = player.getPos();
        RewindResult result = RewindExecutor.rewind(player, seconds, null, true);
        if (result.ok()) {
            // ручные возвраты считаются отдельно от спасений: это не «меня
            // спасло», а «я вернулся сам», и в журнале это разные строки
            state.manualRewinds++;
            ServerFeedback.rewind(player, from);
        } else {
            // об отказе говорим сразу: игрок нажал клавишу и ждёт ответа
            ServerFeedback.denied(player, result);
        }
        TemporalAttachments.markDirty(player, state);
    }
}
