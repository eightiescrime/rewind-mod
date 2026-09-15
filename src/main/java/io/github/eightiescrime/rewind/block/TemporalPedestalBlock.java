package io.github.eightiescrime.rewind.block;

import io.github.eightiescrime.rewind.temporal.TemporalRift;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Постамент разлома: единственное место в мире, где способность можно обрести
 * (ТЗ §38).
 *
 * <p>Состояний у него два и переход между ними односторонний. Зажжённый держит
 * реликвию и гудит; погасший не делает ничего и остаётся стоять как метка того,
 * что сюда уже приходили. Сломать постамент нельзя ни в одном из состояний —
 * иначе метка исчезала бы вместе с историей места.
 */
public class TemporalPedestalBlock extends Block implements BlockEntityProvider {

    public static final BooleanProperty LIT = Properties.LIT;

    public TemporalPedestalBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(LIT, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (!state.get(LIT)) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            TemporalRift.touch(serverPlayer, (ServerWorld) world, pos);
        }
        return ActionResult.CONSUME;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) {
            return;
        }
        world.addParticle(ParticleTypes.REVERSE_PORTAL,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                pos.getY() + 1.0,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                0.0, 0.04, 0.0);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TemporalPedestalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                 BlockEntityType<T> type) {
        // гудит только сервер: звук слышат все, кто рядом, а не только хозяин чанка
        if (world.isClient || type != RewindBlocks.PEDESTAL_ENTITY) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<TemporalPedestalBlockEntity>)
                TemporalPedestalBlockEntity::serverTick;
    }
}
