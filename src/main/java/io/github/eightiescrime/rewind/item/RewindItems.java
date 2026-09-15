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

/** Предметы мода. Пока он один. */
public final class RewindItems {

    public static final Item TEMPORAL_JOURNAL = Registry.register(
            Registries.ITEM, RewindMod.id("temporal_journal"),
            new TemporalJournalItem(new Item.Settings().maxCount(1)));

    private RewindItems() {
    }

    public static void register() {
        // ключи ванильных вкладок в этой версии приватные, поэтому собираем свой
        RegistryKey<ItemGroup> tools = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.ofVanilla("tools"));
        ItemGroupEvents.modifyEntriesEvent(tools).register(entries -> entries.add(TEMPORAL_JOURNAL));
    }
}
