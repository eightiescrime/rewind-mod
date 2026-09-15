package io.github.eightiescrime.rewind.feedback;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.network.RewindEffectPayload;
import io.github.eightiescrime.rewind.sound.RewindSounds;
import io.github.eightiescrime.rewind.temporal.RewindResult;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Отмотку должно быть видно и слышно — иначе её нельзя ни оценить, ни проверить.
 *
 * <p>Всё здесь работает и на ванильном клиенте: строка над хотбаром, звук
 * и след из частиц по пройденной назад траектории. Клиентский мод добавляет
 * поверх этого силуэт, серую пелену и рывок камеры, но ничего не заменяет —
 * игрок без мода по-прежнему понимает, что произошло.
 *
 */
public final class ServerFeedback {

    /** Сколько частиц оставлять на каждый блок пройденного назад пути. */
    private static final double TRAIL_PER_BLOCK = 3.0;
    private static final int TRAIL_MAX = 24;

    private ServerFeedback() {
    }

    public static void rewind(ServerPlayerEntity player, Vec3d from) {
        RewindConfig config = RewindConfig.get();
        if (!config.serverFeedbackEnabled) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        Vec3d to = player.getPos();

        if (config.soundEnabled) {
            // звук уходит оттуда, где ударило, и приходит туда, где игрок
            // оказался: пара, по которой отмотка слышна как движение
            world.playSound(null, from.x, from.y, from.z,
                    RewindSounds.REWIND_TRIGGER, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.playSound(null, to.x, to.y, to.z,
                    RewindSounds.REWIND_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }

        // временной след остаётся частицами, а не сущностью (RULE 5)
        world.spawnParticles(ParticleTypes.SOUL, from.x, from.y + 1.0, from.z,
                12, 0.25, 0.5, 0.25, 0.01);
        trail(world, from, to);

        sendEffect(player, new RewindEffectPayload(player.getId(),
                from.x, from.y, from.z, to.x, to.y, to.z));

        player.sendMessage(Text.translatable("rewind.feedback.rewound"), true);
    }

    /** Ниточка частиц по тому пути, который игрок только что прошёл назад. */
    private static void trail(ServerWorld world, Vec3d from, Vec3d to) {
        int steps = (int) Math.clamp(Math.round(from.distanceTo(to) * TRAIL_PER_BLOCK), 2, TRAIL_MAX);
        for (int i = 1; i <= steps; i++) {
            Vec3d point = from.lerp(to, i / (double) steps);
            world.spawnParticles(ParticleTypes.SOUL, point.x, point.y + 0.9, point.z,
                    1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /**
     * Шлёт эффект самому игроку и всем, кто его видит.
     *
     * <p>{@code canSend} обязателен: на ванильном клиенте канала нет, и без
     * проверки отправка стала бы ошибкой на ровном месте.
     */
    private static void sendEffect(ServerPlayerEntity player, RewindEffectPayload payload) {
        if (ServerPlayNetworking.canSend(player, RewindEffectPayload.ID)) {
            ServerPlayNetworking.send(player, payload);
        }
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
            if (ServerPlayNetworking.canSend(viewer, RewindEffectPayload.ID)) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
    }

    public static void levelUp(ServerPlayerEntity player, int level) {
        if (!RewindConfig.get().serverFeedbackEnabled) {
            return;
        }
        player.sendMessage(Text.translatable("rewind.feedback.level_up", level), false);
        if (RewindConfig.get().soundEnabled) {
            player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7f, 0.8f);
        }
    }

    public static void unlocked(ServerPlayerEntity player) {
        if (!RewindConfig.get().serverFeedbackEnabled) {
            return;
        }
        player.sendMessage(Text.translatable("rewind.feedback.unlocked"), false);
    }

    /** Почему отмотка не случилась. Показывается только по явному запросу игрока. */
    public static void denied(ServerPlayerEntity player, RewindResult result) {
        if (!RewindConfig.get().serverFeedbackEnabled) {
            return;
        }
        player.sendMessage(Text.translatable(result.translationKey()), true);
    }
}
