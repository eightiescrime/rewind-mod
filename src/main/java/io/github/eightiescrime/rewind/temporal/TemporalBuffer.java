package io.github.eightiescrime.rewind.temporal;

import java.util.Optional;

/**
 * Кольцевой буфер снимков одного игрока.
 *
 * <p>Размер фиксирован (ТЗ §13): старые записи вытесняются молча, память не
 * растёт. Буфер живёт только в памяти сервера и никогда не сохраняется —
 * после выхода, смены измерения, смерти и перезапуска он пуст (ТЗ §41).
 *
 * <p>Доступ только с серверного треда, поэтому синхронизации нет.
 */
public final class TemporalBuffer {
    private final TemporalSnapshot[] slots;
    /** Индекс следующей записи. */
    private int head;
    private int size;

    public TemporalBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Ёмкость буфера должна быть положительной: " + capacity);
        }
        this.slots = new TemporalSnapshot[capacity];
    }

    public void push(TemporalSnapshot snapshot) {
        slots[head] = snapshot;
        head = (head + 1) % slots.length;
        if (size < slots.length) {
            size++;
        }
    }

    /**
     * Ближайший снимок не новее указанного тика.
     *
     * <p>Поиск линейный от новых к старым. В буфере максимум пара сотен
     * записей, так что бинарный поиск здесь ничего бы не выиграл.
     * ponytail: линейный поиск по ≤200 снимкам, бинарный если буфер вырастет на порядок.
     */
    public Optional<TemporalSnapshot> findAtOrBefore(long tick) {
        for (int i = size - 1; i >= 0; i--) {
            TemporalSnapshot candidate = at(i);
            if (candidate.tick() <= tick) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Выбрасывает указанный тик и всё, что новее.
     *
     * <p>Вызывается после успешной отмотки: использованная временная точка
     * перестаёт существовать, поэтому отмотаться в неё второй раз нельзя
     * (ТЗ §15).
     */
    public void dropFrom(long tick) {
        while (size > 0 && at(size - 1).tick() >= tick) {
            head = (head - 1 + slots.length) % slots.length;
            slots[head] = null;
            size--;
        }
    }

    public void clear() {
        java.util.Arrays.fill(slots, null);
        head = 0;
        size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int capacity() {
        return slots.length;
    }

    /** Тик самой старой записи. Бросает, если буфер пуст. */
    public long oldestTick() {
        requireNotEmpty();
        return at(0).tick();
    }

    /** Тик самой свежей записи. Бросает, если буфер пуст. */
    public long newestTick() {
        requireNotEmpty();
        return at(size - 1).tick();
    }

    /** Запись по порядковому номеру, где 0 — самая старая. */
    private TemporalSnapshot at(int indexFromOldest) {
        int oldest = (head - size + slots.length) % slots.length;
        return slots[(oldest + indexFromOldest) % slots.length];
    }

    private void requireNotEmpty() {
        if (size == 0) {
            throw new IllegalStateException("Буфер пуст");
        }
    }
}
