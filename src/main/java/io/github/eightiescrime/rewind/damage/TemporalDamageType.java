package io.github.eightiescrime.rewind.damage;

import java.util.EnumSet;
import java.util.Set;

/**
 * Классы угроз, которые «Отмотка» различает.
 *
 * <p>Уровень способности определяет, от чего она срабатывает автоматически
 * (ТЗ §3): сначала ближний бой, потом снаряды, потом взрывы, потом окружение.
 * {@link #OTHER} — всё, что не опознано; оно доступно только на пятом уровне,
 * то есть в осознанном режиме.
 */
public enum TemporalDamageType {
    MELEE(1),
    PROJECTILE(2),
    EXPLOSION(3),
    FIRE(4),
    LAVA(4),
    FALL(4),
    DROWNING(4),
    POISON(4),
    MAGIC(4),
    WITHER(4),
    FREEZING(4),
    SUFFOCATION(4),
    CACTUS(4),
    ENVIRONMENTAL(4),
    OTHER(5);

    private final int requiredLevel;

    TemporalDamageType(int requiredLevel) {
        this.requiredLevel = requiredLevel;
    }

    /** С какого уровня способность реагирует на эту угрозу автоматически. */
    public int requiredLevel() {
        return requiredLevel;
    }

    /** Угрозы окружения — те, у которых обычно нет атакующего. */
    public boolean isEnvironmental() {
        return requiredLevel == 4;
    }

    /**
     * С какими угрозами игрок должен столкнуться, чтобы открыть уровень.
     *
     * <p>Пустое множество означает «встреча не требуется, хватит мастерства»
     * (ТЗ §37: уровень 5 не привязан к новому типу угрозы).
     */
    public static Set<TemporalDamageType> unlockedAtLevel(int level) {
        return switch (level) {
            case 2 -> EnumSet.of(PROJECTILE);
            case 3 -> EnumSet.of(EXPLOSION);
            case 4 -> environmental();
            default -> EnumSet.noneOf(TemporalDamageType.class);
        };
    }

    public static Set<TemporalDamageType> environmental() {
        EnumSet<TemporalDamageType> set = EnumSet.noneOf(TemporalDamageType.class);
        for (TemporalDamageType type : values()) {
            if (type.isEnvironmental()) {
                set.add(type);
            }
        }
        return set;
    }
}
