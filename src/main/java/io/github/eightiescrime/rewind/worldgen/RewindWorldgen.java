package io.github.eightiescrime.rewind.worldgen;

import io.github.eightiescrime.rewind.RewindMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

/**
 * Мировая генерация мода. Сам разлом описан файлами в {@code data/rewind/worldgen},
 * здесь регистрируется только та его часть, которую нельзя выразить json-ом.
 */
public final class RewindWorldgen {

    public static final Feature<DefaultFeatureConfig> RIFT = Registry.register(
            Registries.FEATURE, RewindMod.id("temporal_rift"), new RiftFeature(DefaultFeatureConfig.CODEC));

    private RewindWorldgen() {
    }

    /** Регистрация происходит при первом обращении к классу; метод её и вызывает. */
    public static void register() {
    }
}
