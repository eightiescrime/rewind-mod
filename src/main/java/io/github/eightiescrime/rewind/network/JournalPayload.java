package io.github.eightiescrime.rewind.network;

import io.github.eightiescrime.rewind.RewindMod;
import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Содержимое журнала времени — то, что игрок видит, открыв тетрадь.
 *
 * <p>Уходит по запросу, а не каждый тик: журнал смотрят редко, а данных в нём
 * заметно больше, чем в строке HUD.
 *
 * @param entries только те угрозы, с которыми игрок уже встречался, — журнал
 *                заполняется сам и не рассказывает наперёд, что бывает дальше
 * @param manualRewinds сколько раз игрок вернулся сам: спасения и собственные
 *                      возвраты — разные строки, и путать их нельзя
 */
public record JournalPayload(int level, int manualRewinds,
                             List<Entry> entries) implements CustomPayload {

    /** Одна строка журнала: встреченная угроза и сколько раз она не убила. */
    public record Entry(TemporalDamageType type, int rescues) {
    }

    public static final CustomPayload.Id<JournalPayload> ID =
            new CustomPayload.Id<>(RewindMod.id("journal"));

    public static final PacketCodec<PacketByteBuf, JournalPayload> CODEC =
            CustomPayload.codecOf(JournalPayload::write, JournalPayload::new);

    private JournalPayload(PacketByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), readEntries(buf));
    }

    private static List<Entry> readEntries(PacketByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        TemporalDamageType[] types = TemporalDamageType.values();
        for (int i = 0; i < size; i++) {
            int ordinal = buf.readVarInt();
            int saved = buf.readVarInt();
            // пакет пришёл с сервера, но верить ему на слово всё равно нельзя:
            // неизвестный порядковый номер — это чужая версия мода, а не угроза
            if (ordinal >= 0 && ordinal < types.length) {
                entries.add(new Entry(types[ordinal], saved));
            }
        }
        return List.copyOf(entries);
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(level);
        buf.writeVarInt(manualRewinds);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeVarInt(entry.type().ordinal());
            buf.writeVarInt(entry.rescues());
        }
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
