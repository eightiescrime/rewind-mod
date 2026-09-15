package io.github.eightiescrime.rewind.client;

import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Временной шрам (ТЗ §57): накопленный долг видно на самом кадре.
 *
 * <p>Своего счётчика у шрама нет — это тот же временной долг, который уже
 * тормозит восстановление энергии и сам тает со временем. Здесь у него
 * появляется лицо: края кадра истираются, и по картинке изредка проходит
 * рваная полоса, как по засвеченной плёнке. Чем больше шрам, тем чаще.
 *
 * <p>Рисуется прямоугольниками, без своих текстур и без гладких градиентов:
 * истирание набрано ступенями, поэтому остаётся пиксельным.
 *
 * <p>ponytail: искажения звука и ghost images из §57 не сделаны — первое
 * требует миксина в звуковой движок, второе рендера лишней модели каждый кадр.
 * Добавлять, если истирания кадра окажется мало.
 */
public final class TemporalScar {

    /** Ниже этой доли шрама кадр чист: одна мелкая отмотка портить его не должна. */
    static final int FLOOR_PERCENT = 20;

    /** Выцветшая бумага по краям — не тёмная виньетка: мод про время, а не про кровь. */
    private static final int WORN = 0x6E6552;
    private static final int TEAR = 0xC8B68F;

    /** Сколько ступеней истирания и насколько плотна каждая. */
    private static final int BANDS = 5;
    private static final int BAND_THICKNESS = 6;
    private static final float BAND_ALPHA = 0.10f;

    /** Границы паузы между рваными полосами: слева полный шрам, справа едва заметный. */
    static final long TEAR_FAST_MS = 1200L;
    static final long TEAR_SLOW_MS = 9000L;
    private static final long TEAR_LIFE_MS = 110L;

    /** Место и толщина полосы — единственное, что тут случайно. */
    private static final Random RANDOM = new Random();

    private static long nextTearMs;
    private static long tearStartMs = Long.MIN_VALUE / 2;
    private static float tearAt;
    private static int tearHeight;

    private TemporalScar() {
    }

    /** Насколько шрам заметен, 0..1. */
    public static float intensity(int scarPercent) {
        return Math.clamp((scarPercent - FLOOR_PERCENT) / (float) (100 - FLOOR_PERCENT), 0.0f, 1.0f);
    }

    /** Пауза до следующей рваной полосы: чем сильнее шрам, тем короче. */
    static long tearDelayMs(float intensity) {
        float t = Math.clamp(intensity, 0.0f, 1.0f);
        return (long) (TEAR_SLOW_MS - (TEAR_SLOW_MS - TEAR_FAST_MS) * t);
    }

    public static void reset() {
        nextTearMs = 0L;
        tearStartMs = Long.MIN_VALUE / 2;
    }

    public static void render(DrawContext context, int scarPercent, long nowMs) {
        float intensity = intensity(scarPercent);
        if (intensity <= 0.0f) {
            nextTearMs = 0L;
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        worn(context, width, height, intensity);
        tear(context, width, height, intensity, nowMs);
    }

    /** Истирание по краям: ступени, а не градиент. */
    private static void worn(DrawContext context, int width, int height, float intensity) {
        for (int band = 0; band < BANDS; band++) {
            // внешняя ступень самая плотная, дальше внутрь всё бледнее
            float alpha = BAND_ALPHA * intensity * (BANDS - band) / (float) BANDS;
            int near = band * BAND_THICKNESS;
            int far = near + BAND_THICKNESS;
            int color = Parchment.argb(WORN, alpha);
            context.fill(0, near, width, far, color);
            context.fill(0, height - far, width, height - near, color);
            context.fill(near, far, far, height - far, color);
            context.fill(width - far, far, width - near, height - far, color);
        }
    }

    /** Рваная полоса: короткая засветка поперёк кадра в случайном месте. */
    private static void tear(DrawContext context, int width, int height, float intensity, long nowMs) {
        if (nowMs >= nextTearMs) {
            nextTearMs = nowMs + tearDelayMs(intensity);
            tearStartMs = nowMs;
            tearAt = RANDOM.nextFloat();
            tearHeight = 1 + RANDOM.nextInt(3);
        }

        long age = nowMs - tearStartMs;
        if (age < 0 || age >= TEAR_LIFE_MS) {
            return;
        }
        float fade = 1.0f - age / (float) TEAR_LIFE_MS;
        int y = (int) (tearAt * Math.max(1, height - tearHeight));
        context.fill(0, y, width, y + tearHeight,
                Parchment.argb(TEAR, fade * 0.22f * intensity));
    }
}
