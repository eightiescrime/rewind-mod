package io.github.eightiescrime.rewind.client;

import net.minecraft.client.gui.DrawContext;

/**
 * Общая палитра и общий лист бумаги для HUD и журнала.
 *
 * <p>Своих текстур мод не заводит: и строка в углу, и открытая тетрадь
 * рисуются одними и теми же прямоугольниками. Цвета приглушённые — чистые
 * читались бы как интерфейс другой игры.
 */
final class Parchment {

    static final int BORDER = 0x2E2418;
    static final int PAPER = 0xC8B68F;
    static final int SHADE = 0xB2A07B;
    static final int INK = 0x33291B;
    static final int INK_FADED = 0x7A6B52;
    static final int ACCENT = 0x6B3A2E;
    static final int BAR_EMPTY = 0x9C8B67;
    static final int BAR_FULL = 0x3E5C56;
    static final int BAR_WAITING = 0x7A6B52;

    private Parchment() {
    }

    static int argb(int rgb, float alpha) {
        return ((int) (Math.clamp(alpha, 0.0f, 1.0f) * 255) << 24) | rgb;
    }

    /** Лист: рамка, бумага и тень по нижнему краю — страница, а не панель. */
    static void page(DrawContext context, int x, int y, int width, int height, float alpha) {
        context.fill(x, y, x + width, y + height, argb(BORDER, alpha));
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, argb(PAPER, alpha));
        context.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, argb(SHADE, alpha));
    }

    /** Тонкая линейка поперёк страницы. */
    static void rule(DrawContext context, int x, int y, int width, float alpha) {
        context.fill(x, y, x + width, y + 1, argb(SHADE, alpha));
    }
}
