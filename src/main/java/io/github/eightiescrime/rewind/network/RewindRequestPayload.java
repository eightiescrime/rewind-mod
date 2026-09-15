package io.github.eightiescrime.rewind.network;

import io.github.eightiescrime.rewind.RewindMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Просьба отмотать: единственный пакет, который клиент отправляет сам.
 *
 * <p>Глубина здесь — именно просьба, а не приказ. Сервер заново проверяет
 * уровень, кулдаун, энергию и потолок глубины, и урезает число до своего
 * предела (ТЗ §24). Изменённый клиент может прислать сюда что угодно —
 * отмотается он ровно настолько, насколько ему позволено.
 *
 * @param seconds желаемая глубина в целых секундах — мельче шкала всё равно
 *                не показывает
 */
public record RewindRequestPayload(int seconds) implements CustomPayload {

    public static final CustomPayload.Id<RewindRequestPayload> ID =
            new CustomPayload.Id<>(RewindMod.id("rewind_request"));

    public static final PacketCodec<PacketByteBuf, RewindRequestPayload> CODEC =
            CustomPayload.codecOf(RewindRequestPayload::write, RewindRequestPayload::new);

    private RewindRequestPayload(PacketByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(seconds);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
