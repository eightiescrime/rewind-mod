package io.github.eightiescrime.rewind.client;

import io.github.eightiescrime.rewind.network.RewindEffectPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Видимая сторона отмотки: силуэт, уходящий назад по траектории, пелена
 * на кадре и рывок камеры.
 *
 * <p>Силуэт рисуется моделью самого игрока, но без скина и без тени: это
 * след, а не второй игрок. Своей сущности под него не заводится (RULE 5) —
 * модель берётся у уже существующей и рисуется ещё раз в другом месте.
 *
 * <p>Соседи видят ровно то же самое. Это и есть смысл эха: без него чужая
 * отмотка неотличима от телепорта читера.
 */
public final class RewindEffects {

    /** Сколько летит силуэт от места удара к точке возврата. */
    private static final long GHOST_MS = 650L;
    /** Пелена на кадре — только своя отмотка. */
    private static final long VEIL_MS = 450L;
    private static final long KICK_MS = 260L;
    private static final float KICK_STRENGTH = 0.35f;

    /** Бледный холодный тон: временной след, а не подсветка сущности. */
    private static final int GHOST_RGB = 0x8CA8A2;
    private static final int FULL_BRIGHT = 0xF000F0;

    private record Ghost(int entityId, Vec3d from, Vec3d to, long startMs) {
    }

    private static final List<Ghost> ghosts = new ArrayList<>();
    private static long ownRewindMs = Long.MIN_VALUE / 2;

    private RewindEffects() {
    }

    public static void accept(MinecraftClient client, RewindEffectPayload payload) {
        long now = System.currentTimeMillis();
        ghosts.add(new Ghost(payload.entityId(),
                new Vec3d(payload.fromX(), payload.fromY(), payload.fromZ()),
                new Vec3d(payload.toX(), payload.toY(), payload.toZ()), now));

        if (client.player != null && client.player.getId() == payload.entityId()) {
            ownRewindMs = now;
        }
    }

    public static void reset() {
        ghosts.clear();
        ownRewindMs = Long.MIN_VALUE / 2;
    }

    /** Насколько густа серая пелена на кадре в этот момент, 0..1. */
    public static float veil(long nowMs) {
        return decay(nowMs - ownRewindMs, VEIL_MS);
    }

    /** Насколько отдёрнуть камеру в этот кадр, в блоках. */
    public static float cameraKick() {
        float t = decay(System.currentTimeMillis() - ownRewindMs, KICK_MS);
        // квадрат вместо линейки: рывок резкий, возврат мягкий
        return t * t * KICK_STRENGTH;
    }

    private static float decay(long elapsedMs, long durationMs) {
        if (elapsedMs < 0 || elapsedMs >= durationMs) {
            return 0.0f;
        }
        return 1.0f - elapsedMs / (float) durationMs;
    }

    public static void render(WorldRenderContext context) {
        if (ghosts.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        float tickDelta = context.tickCounter().getTickDelta(false);
        long now = System.currentTimeMillis();

        Iterator<Ghost> it = ghosts.iterator();
        while (it.hasNext()) {
            Ghost ghost = it.next();
            float progress = (now - ghost.startMs()) / (float) GHOST_MS;
            if (progress >= 1.0f) {
                it.remove();
                continue;
            }
            Entity entity = context.world().getEntityById(ghost.entityId());
            if (entity == null || matrices == null || context.consumers() == null) {
                continue;
            }
            Vec3d at = ghost.from().lerp(ghost.to(), progress);
            drawGhost(dispatcher, matrices, context, entity, at, camera, 1.0f - progress, tickDelta);
        }
    }

    private static void drawGhost(EntityRenderDispatcher dispatcher, MatrixStack matrices,
                                  WorldRenderContext context, Entity entity, Vec3d at,
                                  Vec3d camera, float alpha, float tickDelta) {
        // ponytail: прозрачность берётся от «невидимой» ветки рендера живых
        // сущностей — она единственная рисует модель по прозрачному слою.
        // Флаг возвращается на место тем же кадром; свой слой рендера — если
        // этого когда-нибудь станет мало.
        boolean wasInvisible = entity.isInvisible();
        entity.setInvisible(true);
        try {
            dispatcher.render(entity,
                    at.x - camera.x, at.y - camera.y, at.z - camera.z,
                    entity.getYaw(), tickDelta, matrices,
                    new GhostVertexConsumers(context.consumers(), GHOST_RGB, alpha),
                    FULL_BRIGHT);
        } finally {
            entity.setInvisible(wasInvisible);
        }
    }
}
