package io.github.eightiescrime.rewind.item;

import io.github.eightiescrime.rewind.RewindMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.Optional;

/**
 * Компас разлома: стрелка, которая один раз нашла — и больше не отпускает.
 *
 * <p>Своей стрелки у мода нет и не нужно: положение цели пишется в ванильный
 * компонент {@code lodestone_tracker}, а крутит стрелку тот же механизм, что
 * и у компаса на лодстоуне. Поиск идёт по тегу структур, то есть достаёт
 * и те разломы, чьи чанки ещё никогда не грузились.
 */
public class TemporalCompassItem extends Item {

    /** Тег с единственной структурой — разломом. Через него же работает {@code /locate}. */
    public static final TagKey<Structure> RIFTS =
            TagKey.of(RegistryKeys.STRUCTURE, RewindMod.id("temporal_rift"));

    /** Радиус поиска в чанках. Дальше — уже не «рядом», а генерация мира на ровном месте. */
    private static final int SEARCH_CHUNKS = 64;
    /** Поиск тяжёлый, поэтому между попытками пять секунд. */
    private static final int COOLDOWN_TICKS = 100;

    public TemporalCompassItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack, world.isClient);
        }

        player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
        ServerWorld serverWorld = player.getServerWorld();
        BlockPos found = serverWorld.locateStructure(RIFTS, player.getBlockPos(), SEARCH_CHUNKS, false);
        if (found == null) {
            player.sendMessage(Text.translatable("rewind.compass.silent"), true);
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_CONDUIT_DEACTIVATE,
                    SoundCategory.PLAYERS, 0.4f, 1.4f);
            return TypedActionResult.success(stack, false);
        }

        stack.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(
                Optional.of(GlobalPos.create(serverWorld.getRegistryKey(), found)), false));
        player.sendMessage(Text.translatable("rewind.compass.found",
                found.getX(), found.getZ()), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_LODESTONE_COMPASS_LOCK,
                SoundCategory.PLAYERS, 0.6f, 1.2f);
        return TypedActionResult.success(stack, false);
    }
}
