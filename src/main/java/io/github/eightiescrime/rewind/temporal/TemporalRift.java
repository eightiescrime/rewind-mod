package io.github.eightiescrime.rewind.temporal;

import io.github.eightiescrime.rewind.block.RewindBlocks;
import io.github.eightiescrime.rewind.block.TemporalPedestalBlock;
import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.feedback.ServerFeedback;
import io.github.eightiescrime.rewind.item.RewindItems;
import io.github.eightiescrime.rewind.persistence.TemporalAttachments;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import io.github.eightiescrime.rewind.progression.ProgressionManager;
import io.github.eightiescrime.rewind.sound.RewindSounds;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Первое обретение способности (ТЗ §38).
 *
 * <p>Способность не выдаётся: она случается. Игрок трогает реликвию, время
 * срывается назад само собой, и только через несколько секунд появляется HUD
 * с объяснением. До этого момента происходящее должно читаться как «что-то
 * пошло не так», а не как выданная награда.
 *
 * <p>Сама реликвия уходит в руки, а постамент гаснет навсегда — метка того,
 * что место уже отдало своё.
 */
public final class TemporalRift {

    private TemporalRift() {
    }

    public static void touch(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        RewindConfig config = RewindConfig.get();
        TemporalState state = TemporalAttachments.of(player);
        boolean firstTime = !state.unlocked;

        extinguish(world, pos);
        player.getInventory().offerOrDrop(new ItemStack(RewindItems.TEMPORAL_RELIC));

        if (!firstTime) {
            // способность уже есть — реликвия остаётся памятным знаком, и только
            return;
        }

        ProgressionManager.unlock(player, state, TemporalRules.secondsToTicks(config.unlockRevealSeconds));

        Vec3d from = player.getPos();
        if (RewindExecutor.rewind(player, config.riftRewindSeconds, null, false) == RewindResult.SUCCESS) {
            ServerFeedback.rewind(player, from);
        }

        // первый срыв не стоит игроку ничего: он ещё не знает, что такое энергия
        state.energy = config.maxEnergy;
        state.autoCooldown = 0;
        TemporalAttachments.markDirty(player, state);
    }

    private static void extinguish(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isOf(RewindBlocks.TEMPORAL_PEDESTAL)) {
            world.setBlockState(pos, state.with(TemporalPedestalBlock.LIT, false));
        }
        world.playSound(null, pos, RewindSounds.TEMPORAL_INSTABILITY, SoundCategory.BLOCKS, 1.0f, 0.7f);
    }
}
