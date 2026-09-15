package io.github.eightiescrime.rewind.worldgen;

import com.mojang.serialization.Codec;
import io.github.eightiescrime.rewind.block.RewindBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Разлом времени (ТЗ §38): полость в толще камня с постаментом посередине.
 *
 * <p>Пещеру рисует код, а не файл структуры, ровно по одной причине: так она
 * каждый раз получается разной формы и прирастает к тому камню, в котором
 * оказалась. Место при этом всё равно остаётся полноценной структурой — оболочка
 * в {@code worldgen/structure} нужна, чтобы работали {@code /locate} и компас,
 * которые умеют искать в ещё не сгенерированных чанках.
 */
public class RiftFeature extends Feature<DefaultFeatureConfig> {

    private static final int RADIUS = 5;
    private static final int HEIGHT = 3;
    /** Сколько клякс иссушённого камня раскидать по полу. */
    private static final int SCULK_PATCHES = 14;

    public RiftFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        Random random = context.getRandom();

        // разлом должен быть замурован в камне: если сюда уже пришла пещера или
        // вода, находка теряет смысл — пусть лучше не будет никакой
        if (!world.getBlockState(origin).isSolidBlock(world, origin)
                || !world.getBlockState(origin.up(HEIGHT)).isSolidBlock(world, origin.up(HEIGHT))) {
            return false;
        }

        BlockPos center = origin.up(2);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -HEIGHT; dy <= HEIGHT; dy++) {
                    double falloff = (dx * dx + dz * dz) / (double) (RADIUS * RADIUS)
                            + (dy * dy) / (double) (HEIGHT * HEIGHT);
                    if (falloff > 1.0 - random.nextDouble() * 0.15) {
                        continue;
                    }
                    BlockPos pos = center.add(dx, dy, dz);
                    if (world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
                        continue;
                    }
                    world.setBlockState(pos, Blocks.CAVE_AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        BlockPos floor = origin.down();
        for (int i = 0; i < SCULK_PATCHES; i++) {
            BlockPos pos = floor.add(random.nextInt(RADIUS * 2 + 1) - RADIUS, 0,
                    random.nextInt(RADIUS * 2 + 1) - RADIUS);
            BlockState below = world.getBlockState(pos);
            if (below.isSolidBlock(world, pos) && world.isAir(pos.up())) {
                world.setBlockState(pos, Blocks.SCULK.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        // пол под самим постаментом должен быть, даже если полость вскрыла пустоту
        if (!world.getBlockState(floor).isSolidBlock(world, floor)) {
            world.setBlockState(floor, Blocks.DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        world.setBlockState(origin, RewindBlocks.TEMPORAL_PEDESTAL.getDefaultState(),
                Block.NOTIFY_LISTENERS);
        return true;
    }
}
