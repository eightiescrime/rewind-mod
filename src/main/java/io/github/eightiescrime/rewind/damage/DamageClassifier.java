package io.github.eightiescrime.rewind.damage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Переводит ванильный {@link DamageSource} в класс угрозы.
 *
 * <p>Опора — теги типов урона, а не имена источников: модовый урон, честно
 * помеченный {@code is_projectile} или {@code is_explosion}, классифицируется
 * сам собой и попадает под нужный уровень способности (ТЗ §2, §20).
 */
public final class DamageClassifier {

    private DamageClassifier() {
    }

    /**
     * Порядок проверок значим: снаряд может быть огненным, взрыв — огненным,
     * лава входит в тег огня. Сначала более узкое, потом более общее.
     */
    public static TemporalDamageType classify(DamageSource source, LivingEntity victim) {
        if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
            return TemporalDamageType.PROJECTILE;
        }
        if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            return TemporalDamageType.EXPLOSION;
        }
        if (source.isOf(DamageTypes.LAVA)) {
            return TemporalDamageType.LAVA;
        }
        if (source.isIn(DamageTypeTags.IS_FIRE)) {
            return TemporalDamageType.FIRE;
        }
        if (source.isIn(DamageTypeTags.IS_FALL)) {
            return TemporalDamageType.FALL;
        }
        if (source.isIn(DamageTypeTags.IS_DROWNING)) {
            return TemporalDamageType.DROWNING;
        }
        if (source.isIn(DamageTypeTags.IS_FREEZING)) {
            return TemporalDamageType.FREEZING;
        }
        if (source.isOf(DamageTypes.IN_WALL)) {
            return TemporalDamageType.SUFFOCATION;
        }
        if (source.isOf(DamageTypes.CACTUS) || source.isOf(DamageTypes.SWEET_BERRY_BUSH)) {
            return TemporalDamageType.CACTUS;
        }
        if (source.isOf(DamageTypes.WITHER) || source.isOf(DamageTypes.WITHER_SKULL)) {
            return TemporalDamageType.WITHER;
        }
        if (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC)) {
            // ванильный яд бьёт источником MAGIC — отличаем по активному эффекту
            return victim.hasStatusEffect(StatusEffects.POISON)
                    ? TemporalDamageType.POISON
                    : TemporalDamageType.MAGIC;
        }
        if (source.getAttacker() != null && source.getSource() == source.getAttacker()) {
            return TemporalDamageType.MELEE;
        }
        if (source.isOf(DamageTypes.STARVE) || source.isOf(DamageTypes.HOT_FLOOR)
                || source.isOf(DamageTypes.FALLING_BLOCK) || source.isOf(DamageTypes.FLY_INTO_WALL)) {
            return TemporalDamageType.ENVIRONMENTAL;
        }
        return TemporalDamageType.OTHER;
    }

    public static DamageContext context(ServerPlayerEntity player, DamageSource source, float amount) {
        TemporalDamageType type = classify(source, player);
        return new DamageContext(
                type,
                source,
                source.getAttacker(),
                source.getSource(),
                player.getPos(),
                amount,
                player.getServerWorld().getTime(),
                sourceKey(source, type));
    }

    /**
     * Ключ источника угрозы.
     *
     * <p>У удара мобом это UUID моба, поэтому защита после отмотки действует
     * против конкретного зомби, а не против всего ближнего боя (ТЗ §10).
     * У лавы или падения атакующего нет, и ключом становится имя типа урона.
     */
    public static String sourceKey(DamageSource source, TemporalDamageType type) {
        Entity attacker = source.getAttacker();
        if (attacker != null && !type.isEnvironmental()) {
            return attacker.getUuidAsString();
        }
        return source.getName();
    }
}
