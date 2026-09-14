package io.github.eightiescrime.rewind.feedback;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.damage.DamageContext;
import io.github.eightiescrime.rewind.temporal.RewindResult;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Отмотку должно быть видно и слышно — иначе её нельзя ни оценить, ни проверить.
 *
 * <p>В фазах 1–3 клиентского кода нет, поэтому событие показывается ванильными
 * средствами сервера: строкой над хотбаром, звуком и частицами. Их видят и
 * соседние игроки — ровно как требует ТЗ §31: игрок A исчезает и появляется
 * там, где был, а игрок B ничего не теряет.
 *
 * <p>ponytail: звук и частицы — ванильные заглушки. Свои rewind_trigger,
 * rewind_complete и настоящий афтеримидж приходят в шестой фазе.
 */
public final class ServerFeedback {

    private ServerFeedback() {
    }

    public static void rewind(ServerPlayerEntity player, DamageContext ctx) {
        RewindConfig config = RewindConfig.get();
        if (!config.serverFeedbackEnabled) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (config.soundEnabled) {
            world.playSound(null, ctx.position().x, ctx.position().y, ctx.position().z,
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.6f);
        }
        // временной след остаётся частицами, а не сущностью (RULE 5)
        world.spawnParticles(ParticleTypes.SOUL,
                ctx.position().x, ctx.position().y + 1.0, ctx.position().z,
                12, 0.25, 0.5, 0.25, 0.01);
        player.sendMessage(Text.translatable("rewind.feedback.rewound"), true);
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
