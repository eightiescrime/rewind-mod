package io.github.eightiescrime.rewind.block;

import io.github.eightiescrime.rewind.sound.RewindSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Голос разлома.
 *
 * <p>Разлом должно быть слышно раньше, чем видно: игрок копает, ловит краем уха
 * гул и идёт на него. Радиус слышимости не считается кодом — в Minecraft он
 * равен шестнадцати громкостям, поэтому нужные сорок блоков задаются одним
 * числом {@link #HUM_VOLUME}.
 */
public class TemporalPedestalBlockEntity extends BlockEntity {

    /** Гул раз в пять секунд: чаще — навязчиво, реже — не поймать направление. */
    private static final int HUM_INTERVAL = 100;
    /** 16 × 2.5 = 40 блоков. */
    private static final float HUM_VOLUME = 2.5f;
    /** Щелчок в среднем раз в полминуты, вразнобой с гулом. */
    private static final int CLICK_CHANCE = 600;

    public TemporalPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(RewindBlocks.PEDESTAL_ENTITY, pos, state);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state,
                                  TemporalPedestalBlockEntity entity) {
        if (!state.get(TemporalPedestalBlock.LIT)) {
            return;
        }
        if (world.getTime() % HUM_INTERVAL == 0) {
            world.playSound(null, pos, RewindSounds.TEMPORAL_RIFT_HUM,
                    SoundCategory.AMBIENT, HUM_VOLUME, 1.0f);
        } else if (world.random.nextInt(CLICK_CHANCE) == 0) {
            world.playSound(null, pos, RewindSounds.TEMPORAL_RIFT_CLICK,
                    SoundCategory.AMBIENT, HUM_VOLUME, 1.0f);
        }
    }
}
