package io.github.eightiescrime.rewind.client;

import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import io.github.eightiescrime.rewind.network.JournalPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Открытая тетрадь: с чем игрок уже сталкивался и что из этого его не убило.
 *
 * <p>Цифр здесь нет намеренно. Прогрессия читается по самому списку — он
 * заполняется сам — и по чёрточкам спасений, как в настоящем дневнике.
 * Угрозы, до которых способность пока не дотягивается, написаны бледнее:
 * видно, что впереди ещё есть куда расти.
 */
public class JournalScreen extends Screen {

    private static final int WIDTH = 216;
    private static final int PADDING = 10;
    private static final int LINE = 11;
    /** Больше пяти чёрточек не рисуем: дальше важно только «много». */
    private static final int MAX_TALLIES = 5;

    private final JournalPayload journal;

    public JournalScreen(JournalPayload journal) {
        super(Text.translatable("rewind.journal.title"));
        this.journal = journal;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int rows = Math.max(1, journal.entries().size());
        boolean returns = journal.manualRewinds() > 0;
        int height = PADDING * 2 + textRenderer.fontHeight * 2 + 6 + rows * LINE + 6 + textRenderer.fontHeight
                + (returns ? textRenderer.fontHeight + 1 : 0);
        int x = (width - WIDTH) / 2;
        int y = (this.height - height) / 2;

        Parchment.page(context, x, y, WIDTH, height, 1.0f);

        int textX = x + PADDING;
        int inner = WIDTH - PADDING * 2;
        int lineY = y + PADDING;

        context.drawText(textRenderer, title, textX, lineY, Parchment.argb(Parchment.INK, 1.0f), false);
        lineY += textRenderer.fontHeight + 1;
        context.drawText(textRenderer,
                Text.translatable("rewind.journal.grip", TemporalHud.roman(journal.level())),
                textX, lineY, Parchment.argb(Parchment.INK_FADED, 1.0f), false);

        if (returns) {
            // собственные возвраты стоят отдельной строкой: спасли тебя или ты
            // вернулся сам — в дневнике это разные записи
            lineY += textRenderer.fontHeight + 1;
            context.drawText(textRenderer,
                    Text.translatable("rewind.journal.returns", journal.manualRewinds()),
                    textX, lineY, Parchment.argb(Parchment.INK_FADED, 1.0f), false);
        }

        lineY += textRenderer.fontHeight + 3;
        Parchment.rule(context, textX, lineY, inner, 1.0f);
        lineY += 3;

        if (journal.entries().isEmpty()) {
            context.drawText(textRenderer, Text.translatable("rewind.journal.nothing_yet"),
                    textX, lineY, Parchment.argb(Parchment.INK_FADED, 1.0f), false);
            lineY += LINE;
        } else {
            for (JournalPayload.Entry entry : journal.entries()) {
                drawEntry(context, entry, textX, lineY, inner);
                lineY += LINE;
            }
        }

        lineY += 3;
        Parchment.rule(context, textX, lineY, inner, 1.0f);
        lineY += 3;

        boolean complete = journal.entries().size() >= TemporalDamageType.values().length;
        context.drawText(textRenderer,
                Text.translatable(complete ? "rewind.journal.complete" : "rewind.journal.blank_pages"),
                textX, lineY, Parchment.argb(Parchment.INK_FADED, 1.0f), false);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawEntry(DrawContext context, JournalPayload.Entry entry, int x, int y, int inner) {
        // угроза не по зубам текущему уровню — она в журнале, но бледная
        boolean reachable = entry.type().requiredLevel() <= journal.level();
        int ink = reachable ? Parchment.INK : Parchment.INK_FADED;
        context.drawText(textRenderer, threatName(entry.type()), x, y, Parchment.argb(ink, 1.0f), false);
        tallies(context, entry.rescues(), x + inner, y + 1);
    }

    /** Чёрточки спасений, как в тетради: по одной за каждый раз, но не больше пяти. */
    private static void tallies(DrawContext context, int rescues, int rightEdge, int y) {
        int marks = Math.min(rescues, MAX_TALLIES);
        for (int i = 0; i < marks; i++) {
            int markX = rightEdge - (marks - i) * 3;
            context.fill(markX, y, markX + 1, y + 6, Parchment.argb(Parchment.ACCENT, 1.0f));
        }
        if (rescues > MAX_TALLIES) {
            // перечёркнутая пятёрка: дальше счёт уже не важен
            int from = rightEdge - marks * 3;
            context.fill(from - 1, y + 3, rightEdge - 1, y + 4, Parchment.argb(Parchment.ACCENT, 1.0f));
        }
    }

    private static Text threatName(TemporalDamageType type) {
        return Text.translatable("rewind.threat." + type.name().toLowerCase(Locale.ROOT));
    }
}
