package io.github.eightiescrime.rewind.temporal;

import io.github.eightiescrime.rewind.damage.TemporalDamageType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Короткая защита после отмотки (ТЗ §10).
 *
 * <p>Смысл не в неуязвимости, а в разрыве цикла: игрока вернуло на полсекунды
 * назад, и та же лава или тот же зомби ударят снова буквально следующим тиком.
 * Поэтому защита адресная — она гасит повтор именно той угрозы, от которой
 * была отмотка, и не мешает всему остальному миру достать игрока.
 *
 * <p>У угроз окружения атакующего нет, поэтому для них ключ игнорируется
 * и защита действует по типу целиком.
 *
 * <p>Живёт только в памяти: после перезахода защиты нет, и это правильно.
 */
public final class TemporalProtection {
    private final Map<String, Integer> byKey = new HashMap<>();
    private final Map<TemporalDamageType, Integer> byType = new HashMap<>();

    public void grant(String sourceKey, TemporalDamageType type, int ticks) {
        if (ticks <= 0) {
            return;
        }
        if (type.isEnvironmental()) {
            byType.merge(type, ticks, Math::max);
        } else {
            byKey.merge(sourceKey + '|' + type.name(), ticks, Math::max);
        }
    }

    public boolean covers(String sourceKey, TemporalDamageType type) {
        if (type.isEnvironmental()) {
            return byType.containsKey(type);
        }
        return byKey.containsKey(sourceKey + '|' + type.name());
    }

    public void tick() {
        decrement(byKey);
        decrement(byType);
    }

    public void clear() {
        byKey.clear();
        byType.clear();
    }

    public boolean isEmpty() {
        return byKey.isEmpty() && byType.isEmpty();
    }

    /** Человекочитаемый список активных защит — только для {@code /rewind debug}. */
    public String describe() {
        if (isEmpty()) {
            return "нет";
        }
        StringBuilder sb = new StringBuilder();
        byType.forEach((type, ticks) -> sb.append(type.name()).append('=').append(ticks).append(' '));
        byKey.forEach((key, ticks) -> sb.append(key).append('=').append(ticks).append(' '));
        return sb.toString().trim();
    }

    private static <K> void decrement(Map<K, Integer> map) {
        Iterator<Map.Entry<K, Integer>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            if (left <= 0) {
                it.remove();
            } else {
                entry.setValue(left);
            }
        }
    }
}
