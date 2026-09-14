package io.github.eightiescrime.rewind.damage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Всё, что способность знает о конкретном входящем уроне.
 *
 * <p>Собирается один раз при перехвате и дальше ходит по системе целиком,
 * чтобы каждый слой не разбирал {@link DamageSource} заново.
 *
 * @param position где находился игрок в момент удара — там и появится
 *                 временной след
 * @param sourceKey устойчивый ключ источника: UUID атакующего, если он есть,
 *                  иначе имя типа урона. По нему работают и убывающее
 *                  мастерство, и адресная временная защита.
 */
public record DamageContext(
        TemporalDamageType type,
        DamageSource source,
        @Nullable Entity attacker,
        @Nullable Entity direct,
        Vec3d position,
        float amount,
        long tick,
        String sourceKey
) {
}
