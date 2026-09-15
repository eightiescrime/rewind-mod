package io.github.eightiescrime.rewind.block;

import io.github.eightiescrime.rewind.RewindMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/** Блоки мода. Пока он один — постамент разлома. */
public final class RewindBlocks {

    public static final Block TEMPORAL_PEDESTAL = Registry.register(
            Registries.BLOCK, RewindMod.id("temporal_pedestal"),
            new TemporalPedestalBlock(AbstractBlock.Settings.create()
                    // прочность как у бедрока: постамент не унести и не сломать,
                    // погасшая метка должна пережить любого, кто её нашёл
                    .strength(-1.0f, 3600000.0f)
                    .dropsNothing()
                    .sounds(BlockSoundGroup.SCULK_CATALYST)
                    .luminance(state -> state.get(TemporalPedestalBlock.LIT) ? 7 : 0)));

    public static final BlockEntityType<TemporalPedestalBlockEntity> PEDESTAL_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, RewindMod.id("temporal_pedestal"),
            BlockEntityType.Builder.create(TemporalPedestalBlockEntity::new, TEMPORAL_PEDESTAL).build(null));

    private RewindBlocks() {
    }

    /** Регистрация происходит при первом обращении к классу; метод её и вызывает. */
    public static void register() {
    }
}
