package io.github.eightiescrime.rewind.item;

import io.github.eightiescrime.rewind.RewindMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/** Предметы мода. */
public final class RewindItems {

    public static final Item TEMPORAL_JOURNAL = register("temporal_journal",
            new TemporalJournalItem(new Item.Settings().maxCount(1)));

    /**
     * Реликвия из разлома. Ничего не делает в руках: её ценность в том, что
     * она была там, где способность началась. Надетая через Accessories
     * ускоряет восстановление энергии.
     */
    public static final Item TEMPORAL_RELIC = register("temporal_relic",
            new TemporalRelicItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    public static final Item TEMPORAL_COMPASS = register("temporal_compass",
            new TemporalCompassItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

    private RewindItems() {
    }

    private static Item register(String path, Item item) {
        return Registry.register(Registries.ITEM, RewindMod.id(path), item);
    }

    public static void register() {
        // ключи ванильных вкладок в этой версии приватные, поэтому собираем свой
        RegistryKey<ItemGroup> tools = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.ofVanilla("tools"));
        ItemGroupEvents.modifyEntriesEvent(tools).register(entries -> {
            entries.add(TEMPORAL_JOURNAL);
            entries.add(TEMPORAL_RELIC);
            entries.add(TEMPORAL_COMPASS);
        });
    }
}
