package io.github.eightiescrime.rewind.network;

import io.github.eightiescrime.rewind.RewindMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Случившаяся отмотка — то, что нужно нарисовать.
 *
 * <p>Уходит и самому игроку, и всем, кто его видит: без этого чужая отмотка
 * выглядит как телепорт читера. Соседи получают ровно тот же пакет и рисуют
 * силуэт, уходящий назад по траектории.
 *
 * @param entityId кого отмотало — по нему клиент берёт модель для силуэта
 *                 и отличает свою отмотку от чужой
 */
public record RewindEffectPayload(int entityId,
                                  double fromX, double fromY, double fromZ,
                                  double toX, double toY, double toZ) implements CustomPayload {

    public static final CustomPayload.Id<RewindEffectPayload> ID =
            new CustomPayload.Id<>(RewindMod.id("rewind_effect"));

    public static final PacketCodec<PacketByteBuf, RewindEffectPayload> CODEC =
            CustomPayload.codecOf(RewindEffectPayload::write, RewindEffectPayload::new);

    private RewindEffectPayload(PacketByteBuf buf) {
        this(buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeDouble(fromX);
        buf.writeDouble(fromY);
        buf.writeDouble(fromZ);
        buf.writeDouble(toX);
        buf.writeDouble(toY);
        buf.writeDouble(toZ);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
