package io.github.eightiescrime.rewind.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import io.github.eightiescrime.rewind.temporal.TemporalProtection;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Личное временное состояние игрока: то, что переживает выход и перезапуск
 * сервера (ТЗ §40).
 *
 * <p>Кольцевой буфер снимков сюда намеренно не входит — он живёт только
 * в памяти и обнуляется при любом разрыве непрерывности (ТЗ §41).
 *
 * <p>Класс мутабельный: он меняется каждый тик, и городить пересоздание
 * записи двадцать раз в секунду ради «чистоты» смысла нет. Изменения
 * помечаются грязными через {@link TemporalAttachments#markDirty}.
 */
public final class TemporalState {
    public boolean unlocked;
    public int level = 1;
    public double mastery;
    public double energy = 100.0;
    public double debtSeconds;

    public int autoCooldown;
    public int manualCooldown;
    public int fractureTicks;

    /** С какими угрозами игрок уже сталкивался — условие открытия уровней. */
    public final Map<TemporalDamageType, Integer> encounters = new EnumMap<>(TemporalDamageType.class);
    /** Сколько раз конкретный источник уже давал мастерство с прошлого сброса. */
    public final Map<String, Integer> recentSources = new HashMap<>();
    /** Тиков до сброса убывающих множителей. */
    public int recentResetTicks;

    /** Не сохраняется: после перезахода защищать уже не от чего. */
    public final transient TemporalProtection protection = new TemporalProtection();

    public static final Codec<TemporalDamageType> TYPE_CODEC =
            Codec.STRING.xmap(TemporalDamageType::valueOf, TemporalDamageType::name);

    public static final Codec<TemporalState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("unlocked", false).forGetter(s -> s.unlocked),
            Codec.INT.optionalFieldOf("level", 1).forGetter(s -> s.level),
            Codec.DOUBLE.optionalFieldOf("mastery", 0.0).forGetter(s -> s.mastery),
            Codec.DOUBLE.optionalFieldOf("energy", 100.0).forGetter(s -> s.energy),
            Codec.DOUBLE.optionalFieldOf("debtSeconds", 0.0).forGetter(s -> s.debtSeconds),
            Codec.INT.optionalFieldOf("autoCooldown", 0).forGetter(s -> s.autoCooldown),
            Codec.INT.optionalFieldOf("manualCooldown", 0).forGetter(s -> s.manualCooldown),
            Codec.INT.optionalFieldOf("fractureTicks", 0).forGetter(s -> s.fractureTicks),
            Codec.unboundedMap(TYPE_CODEC, Codec.INT)
                    .optionalFieldOf("encounters", Map.of()).forGetter(s -> s.encounters),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("recentSources", Map.of()).forGetter(s -> s.recentSources),
            Codec.INT.optionalFieldOf("recentResetTicks", 0).forGetter(s -> s.recentResetTicks)
    ).apply(instance, TemporalState::restore));

    /** Собирает состояние из сохранения. Используется только кодеком. */
    private static TemporalState restore(boolean unlocked, int level, double mastery, double energy,
                                         double debtSeconds, int autoCooldown, int manualCooldown,
                                         int fractureTicks, Map<TemporalDamageType, Integer> encounters,
                                         Map<String, Integer> recentSources, int recentResetTicks) {
        TemporalState state = new TemporalState();
        state.unlocked = unlocked;
        state.level = level;
        state.mastery = mastery;
        state.energy = energy;
        state.debtSeconds = debtSeconds;
        state.autoCooldown = autoCooldown;
        state.manualCooldown = manualCooldown;
        state.fractureTicks = fractureTicks;
        state.encounters.putAll(encounters);
        state.recentSources.putAll(recentSources);
        state.recentResetTicks = recentResetTicks;
        return state;
    }

    public Set<TemporalDamageType> encountered() {
        return encounters.keySet();
    }

    public boolean hasMet(TemporalDamageType type) {
        return encounters.containsKey(type);
    }
}
