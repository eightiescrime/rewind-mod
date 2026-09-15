package io.github.eightiescrime.rewind.item;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Реликвия из разлома.
 *
 * <p>Сама по себе она не делает ничего: её ценность в том, что она была там,
 * где способность началась. Надетая в слот Accessories — ускоряет
 * восстановление энергии.
 *
 * <p>Подсказка меняется от того, стоит ли Accessories: обещать слот, которого
 * в сборке нет, было бы враньём.
 */
public class TemporalRelicItem extends Item {

    public TemporalRelicItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.rewind.temporal_relic.lore").formatted(Formatting.GRAY));
        boolean slots = FabricLoader.getInstance().isModLoaded("accessories");
        tooltip.add(Text.translatable(slots
                ? "item.rewind.temporal_relic.worn"
                : "item.rewind.temporal_relic.no_slot").formatted(Formatting.DARK_GRAY));
    }
}
