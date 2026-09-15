package io.github.eightiescrime.rewind.network;

import io.github.eightiescrime.rewind.RewindMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Снимок временного состояния для клиента: ровно то, что рисуется на HUD,
 * и ничего сверх этого.
 *
 * <p>Клиент не получает ни буфера снимков, ни мастерства, ни списка встреч:
 * решения по-прежнему принимает только сервер, клиент лишь показывает итог
 * (ТЗ §30). Поля заранее огрублены — проценты вместо дробной энергии и вместо
 * точного временного долга, — чтобы пакет не менялся каждый тик.
 *
 * @param scarPercent       временной шрам (ТЗ §57): накопленный долг в долях от
 *                          потолка. Клиенту важна не величина долга в секундах,
 *                          а насколько игрок «истёрт», — от этого числа зависит
 *                          и строка на HUD, и порча кадра
 *
 * @param manualMaxSeconds  длина шкалы ручной отмотки; ноль означает, что
 *                          шкале взяться неоткуда — способность ещё не та
 * @param manualReachSeconds докуда по этой шкале хватает энергии сейчас;
 *                          дальше засечки рисуются бледными
 */
public record TemporalStatePayload(boolean unlocked, int level, int energyPercent,
                                   int scarPercent, int cooldownTicks,
                                   int fractureTicks, int manualMaxSeconds,
                                   int manualReachSeconds) implements CustomPayload {

    public static final CustomPayload.Id<TemporalStatePayload> ID =
            new CustomPayload.Id<>(RewindMod.id("temporal_state"));

    public static final PacketCodec<PacketByteBuf, TemporalStatePayload> CODEC =
            CustomPayload.codecOf(TemporalStatePayload::write, TemporalStatePayload::new);

    private TemporalStatePayload(PacketByteBuf buf) {
        this(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt());
    }

    private void write(PacketByteBuf buf) {
        buf.writeBoolean(unlocked);
        buf.writeVarInt(level);
        buf.writeVarInt(energyPercent);
        buf.writeVarInt(scarPercent);
        buf.writeVarInt(cooldownTicks);
        buf.writeVarInt(fractureTicks);
        buf.writeVarInt(manualMaxSeconds);
        buf.writeVarInt(manualReachSeconds);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
