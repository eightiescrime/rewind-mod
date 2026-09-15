package io.github.eightiescrime.rewind.client.mixin;

import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Доступ к приватной регистрации предикатов модели.
 *
 * <p>Fabric API когда-то давал для этого {@code FabricModelPredicateProviderRegistry},
 * но в версии под 1.21.1 его уже нет. Метод в ванили приватный и статический,
 * поэтому единственный путь к нему — инвокер: своей логики здесь нет, только
 * открытая дверь.
 */
@Mixin(ModelPredicateProviderRegistry.class)
public interface ModelPredicateRegistryInvoker {

    @Invoker("register")
    static void rewind$register(Item item, Identifier id, ClampedModelPredicateProvider provider) {
        throw new AssertionError("миксин не применился");
    }
}
