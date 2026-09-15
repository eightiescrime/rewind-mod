package io.github.eightiescrime.rewind.client;

import io.github.eightiescrime.rewind.network.TemporalStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Страница дневника в углу экрана: уровень, полоса энергии, долг.
 *
 * <p>Рисуется прямоугольниками — никаких своих текстур в репозитории не
 * заводится, и картинка остаётся пиксельной, без градиентов. Появляется только
 * когда есть что показать, и сама гаснет.
 */
public final class TemporalHud {

    private static final int MARGIN = 8;
    private static final int PADDING = 4;
    private static final int BAR_WIDTH = 64;
    private static final int BAR_HEIGHT = 6;
    private static final int LINE_GAP = 3;

    /** Пелена в момент своей отмотки: кадр на миг теряет цвет. */
    private static final int VEIL = 0x6E7370;
    private static final float VEIL_MAX = 0.45f;

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    private TemporalHud() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }

        long now = System.currentTimeMillis();

        // ponytail: пост-шейдер обесцвечивания в 1.21.1 публично не ставится,
        // нужен миксин на GameRenderer. Серая пелена поверх кадра стоит три
        // строки и читается так же.
        float veil = RewindEffects.veil(now);
        if (veil > 0.0f) {
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    argb(VEIL, veil * VEIL_MAX));
        }

        TextRenderer font = client.textRenderer;
        ManualTimeline.render(context, font);

        // пока идёт выбор глубины, карточка нужна целиком: по ней и видно,
        // сколько энергии останется
        float alpha = ClientTemporalState.alpha(now);
        if (ManualTimeline.isOpen()) {
            alpha = 1.0f;
        }
        TemporalStatePayload state = ClientTemporalState.get();
        if (state == null || alpha <= 0.01f) {
            return;
        }

        Text title = Text.translatable("rewind.hud.level", roman(state.level()));
        Text percent = Text.literal(state.energyPercent() + "%");
        Text note = note(state);

        int contentWidth = Math.max(font.getWidth(title), BAR_WIDTH + 4 + font.getWidth(percent));
        if (note != null) {
            contentWidth = Math.max(contentWidth, font.getWidth(note));
        }

        int width = contentWidth + PADDING * 2;
        int height = PADDING * 2 + font.fontHeight + LINE_GAP + BAR_HEIGHT
                + (note == null ? 0 : LINE_GAP + font.fontHeight);

        int x = MARGIN;
        int y = MARGIN;

        // сама страница: рамка, бумага и тень по нижнему краю — лист, а не панель
        Parchment.page(context, x, y, width, height, alpha);

        int textX = x + PADDING;
        int lineY = y + PADDING;
        context.drawText(font, title, textX, lineY, argb(Parchment.INK, alpha), false);

        lineY += font.fontHeight + LINE_GAP;
        drawBar(context, textX, lineY, state, alpha);
        context.drawText(font, percent, textX + BAR_WIDTH + 4,
                lineY + (BAR_HEIGHT - font.fontHeight) / 2 + 1, argb(Parchment.INK, alpha), false);

        if (note != null) {
            lineY += BAR_HEIGHT + LINE_GAP;
            context.drawText(font, note, textX, lineY, argb(Parchment.ACCENT, alpha), false);
        }
    }

    private static void drawBar(DrawContext context, int x, int y,
                                TemporalStatePayload state, float alpha) {
        context.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, argb(Parchment.BAR_EMPTY, alpha));
        int filled = Math.round(BAR_WIDTH * state.energyPercent() / 100.0f);
        if (filled > 0) {
            // пока идёт кулдаун или перелом, энергия есть, но толку от неё нет —
            // и полоса это честно показывает цветом
            boolean ready = state.cooldownTicks() == 0 && state.fractureTicks() == 0;
            context.fill(x, y, x + filled, y + BAR_HEIGHT,
                    argb(ready ? Parchment.BAR_FULL : Parchment.BAR_WAITING, alpha));
        }
    }

    /** Нижняя строка: самое срочное из того, что мешает отмотке. */
    @Nullable
    private static Text note(TemporalStatePayload state) {
        if (state.fractureTicks() > 0) {
            return Text.translatable("rewind.hud.fracture", seconds(state.fractureTicks()));
        }
        if (state.cooldownTicks() > 0) {
            return Text.translatable("rewind.hud.cooldown", seconds(state.cooldownTicks()));
        }
        if (state.debtSeconds() > 0.0f) {
            return Text.translatable("rewind.hud.debt", String.format(Locale.ROOT, "%.1f", state.debtSeconds()));
        }
        return null;
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0f);
    }

    static String roman(int level) {
        return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
    }

    private static int argb(int rgb, float alpha) {
        return Parchment.argb(rgb, alpha);
    }
}
