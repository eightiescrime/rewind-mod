package io.github.eightiescrime.rewind.temporal;

import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.List;

/**
 * Неизменяемый срез состояния одного игрока на одном тике.
 *
 * <p>Здесь намеренно нет ни инвентаря, ни опыта, ни прочности, ни продвижений
 * (ТЗ §16–§19): того, чего снимок не хранит, он и не может вернуть, поэтому
 * дюп предметов конструктивно невозможен, а не «запрещён проверкой».
 *
 * <p>Снимок собирается каждый тик, поэтому состоит из примитивов и одного
 * небольшого списка эффектов — никакого Player NBT (RULE 3).
 */
public record TemporalSnapshot(
        long tick,
        double x, double y, double z,
        double velX, double velY, double velZ,
        float yaw, float pitch,
        float health, float absorption,
        int foodLevel, float saturation, float exhaustion,
        int air, int fireTicks, int frozenTicks,
        float fallDistance, boolean onGround,
        int selectedSlot,
        List<StatusEffectInstance> effects
) {
    public TemporalSnapshot {
        effects = List.copyOf(effects);
    }
}
