package io.github.eightiescrime.rewind.compat;

import io.github.eightiescrime.rewind.item.RewindItems;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Мягкая совместимость с Accessories.
 *
 * <p>Curios живёт на Forge и на Fabric не существует, поэтому слот реликвии даёт
 * Accessories. Зависимость мягкая: без этого мода реликвия остаётся обычным
 * предметом, а способность работает ровно так же — надетая реликвия только
 * ускоряет восстановление энергии.
 *
 * <p>Классы Accessories упоминаются исключительно во вложенном {@code Equipped}.
 * Пока мод не стоит, до него не доходит и загрузка класса, поэтому
 * {@code NoClassDefFoundError} взяться неоткуда.
 */
public final class AccessoriesRelic {

    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("accessories");

    private AccessoriesRelic() {
    }

    public static boolean wearing(ServerPlayerEntity player) {
        return PRESENT && Equipped.check(player);
    }

    private static final class Equipped {
        static boolean check(ServerPlayerEntity player) {
            return AccessoriesCapability.getOptionally(player)
                    .map(capability -> capability.isEquipped(RewindItems.TEMPORAL_RELIC))
                    .orElse(false);
        }
    }
}
