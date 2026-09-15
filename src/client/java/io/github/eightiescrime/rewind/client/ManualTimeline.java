package io.github.eightiescrime.rewind.client;

import io.github.eightiescrime.rewind.network.RewindRequestPayload;
import io.github.eightiescrime.rewind.network.TemporalStatePayload;
import io.github.eightiescrime.rewind.sound.RewindSounds;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Ручная отмотка: шкала времени и выбор глубины (ТЗ §23, §25).
 *
 * <p>Держишь клавишу — сбоку разворачивается столбик засечек от «сейчас»
 * и дальше в прошлое. Колёсико двигает метку, отпустил — уходит просьба
 * на сервер. Shift закрывает шкалу, ничего не потратив: случайное нажатие
 * не должно стоить половины энергии и восьми секунд кулдауна.
 *
 * <p>Ни одной проверки баланса здесь нет. Клиент знает только длину шкалы
 * и докуда по ней хватает энергии — оба числа приходят готовыми от сервера,
 * и оба нужны лишь для того, чтобы правильно покрасить засечки.
 */
public final class ManualTimeline {

    /**
     * Клавиша отмотки.
     *
     * <p>Регистрируется при первом обращении к классу, и обратиться надо
     * вовремя: {@code KeyBindingHelper} принимает клавиши только до того, как
     * соберутся {@code GameOptions}. Отсюда пустой {@link #register()} —
     * его зовёт точка входа клиента, чтобы поле инициализировалось там,
     * а не на первом тике, когда уже поздно.
     */
    public static final KeyBinding KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.rewind.manual", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.rewind"));

    private static final int MARGIN = 8;
    private static final int PADDING = 5;
    private static final int ROW = 11;
    private static final int SPINE = 2;
    private static final int TICK = 4;

    /** Тон засечки: чем глубже в прошлое, тем ниже щелчок. */
    private static final float PITCH_TOP = 1.25f;
    private static final float PITCH_STEP = 0.09f;

    private static boolean open;
    private static boolean cancelled;
    private static boolean sneakArmed;
    private static int depth = 1;

    private ManualTimeline() {
    }

    /** Инициализация класса, а с ней и регистрация клавиши. */
    public static void register() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static void reset() {
        open = false;
        cancelled = false;
        sneakArmed = false;
        depth = 1;
    }

    /** Длина шкалы; ноль означает, что ручной отмотки у игрока пока нет. */
    private static int maxSeconds() {
        TemporalStatePayload state = ClientTemporalState.get();
        return state == null ? 0 : state.manualMaxSeconds();
    }

    private static int reachSeconds() {
        TemporalStatePayload state = ClientTemporalState.get();
        return state == null ? 0 : state.manualReachSeconds();
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) {
            // что-то открылось поверх игры: выбор глубины прекращается, но
            // командой это не считается — отмотки не будет
            reset();
            return;
        }
        if (!KEY.isPressed()) {
            // отпустили — это и есть команда: просьба уходит на сервер,
            // а решение принимает он
            if (open) {
                ClientPlayNetworking.send(new RewindRequestPayload(depth));
            }
            reset();
            return;
        }
        if (cancelled) {
            // передумали и всё ещё держим клавишу: шкала не вернётся,
            // пока её не отпустят
            return;
        }
        if (!open) {
            if (maxSeconds() <= 0) {
                return;
            }
            open = true;
            depth = 1;
            // если присядка была зажата ещё до открытия шкалы, она не считается
            // отменой: иначе способность не работала бы у крадущегося вовсе
            sneakArmed = !client.options.sneakKey.isPressed();
            click(client);
        }
        if (!client.options.sneakKey.isPressed()) {
            sneakArmed = true;
        } else if (sneakArmed) {
            open = false;
            cancelled = true;
        }
    }

    /**
     * Прокрутка колёсика, пока шкала открыта.
     *
     * @return {@code true}, если колёсико забрано себе и ванильный слот
     *         менять не надо
     */
    public static boolean scroll(double vertical) {
        if (!open || vertical == 0.0) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        // вниз по столбику — дальше в прошлое: метка идёт туда же, куда и взгляд
        int moved = Math.clamp(depth + (vertical > 0 ? -1 : 1), 1, maxSeconds());
        if (moved != depth) {
            depth = moved;
            click(client);
        }
        return true;
    }

    private static void click(MinecraftClient client) {
        float pitch = Math.max(0.5f, PITCH_TOP - PITCH_STEP * (depth - 1));
        client.getSoundManager().play(
                PositionedSoundInstance.master(RewindSounds.TEMPORAL_TICK, pitch, 0.35f));
    }

    /** Столбик засечек: «сейчас» сверху, глубже — ниже. */
    public static void render(DrawContext context, TextRenderer font) {
        if (!open) {
            return;
        }
        int max = maxSeconds();
        int reach = reachSeconds();
        if (max <= 0) {
            return;
        }

        Text now = Text.translatable("rewind.timeline.now");
        int labelX = PADDING + SPINE + TICK + 3;
        int width = labelX + PADDING;
        for (int second = 1; second <= max; second++) {
            width = Math.max(width, labelX + font.getWidth(mark(second)) + PADDING);
        }
        width = Math.max(width, PADDING * 2 + font.getWidth(now));

        int height = PADDING * 2 + font.fontHeight + 2 + max * ROW;
        int x = MARGIN;
        int y = (context.getScaledWindowHeight() - height) / 2;

        Parchment.page(context, x, y, width, height, 1.0f);

        int headY = y + PADDING;
        context.drawText(font, now, x + PADDING, headY, Parchment.argb(Parchment.INK, 1.0f), false);

        int spineX = x + PADDING + SPINE;
        int firstRowY = headY + font.fontHeight + 2;
        context.fill(spineX, firstRowY, spineX + 1, firstRowY + max * ROW,
                Parchment.argb(Parchment.INK_FADED, 1.0f));

        for (int second = 1; second <= max; second++) {
            int rowY = firstRowY + (second - 1) * ROW;
            int textY = rowY + (ROW - font.fontHeight) / 2;
            boolean chosen = second == depth;
            // засечка, до которой не хватает энергии, написана бледнее:
            // видно, докуда рука дотягивается, и видно, что дальше есть куда
            int ink = chosen ? Parchment.ACCENT : (second <= reach ? Parchment.INK : Parchment.INK_FADED);

            context.fill(spineX, textY + font.fontHeight / 2, spineX + TICK,
                    textY + font.fontHeight / 2 + 1, Parchment.argb(ink, 1.0f));
            if (chosen) {
                // выбранная секунда помечена в самой линии времени, а не только
                // цветом надписи: при беглом взгляде цвет читается хуже формы
                context.fill(spineX - 2, textY + 1, spineX + 2, textY + font.fontHeight,
                        Parchment.argb(Parchment.ACCENT, 1.0f));
            }
            context.drawText(font, mark(second), x + labelX, textY, Parchment.argb(ink, 1.0f), false);
        }
    }

    private static Text mark(int second) {
        return Text.translatable("rewind.timeline.mark", second);
    }
}
