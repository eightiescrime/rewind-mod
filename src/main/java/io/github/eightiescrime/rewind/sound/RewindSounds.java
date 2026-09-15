package io.github.eightiescrime.rewind.sound;

import io.github.eightiescrime.rewind.RewindMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Свои звуковые события (ТЗ §29).
 *
 * <p>Своих {@code .ogg} в репозитории нет и взять их неоткуда, поэтому события
 * ссылаются на ванильные файлы через {@code assets/rewind/sounds.json}. Это
 * не то же самое, что играть ванильный {@code SoundEvent} напрямую: событие
 * своё, у него свои субтитры и своя громкость, а подменить звук на настоящий
 * потом можно правкой одного json — код трогать не придётся.
 */
public final class RewindSounds {

    /** Срыв назад во времени — играет там, где игрока ударило. */
    public static final SoundEvent REWIND_TRIGGER = register("rewind_trigger");
    /** Прибытие в прошлое — тише и выше, играет в точке возврата. */
    public static final SoundEvent REWIND_COMPLETE = register("rewind_complete");
    /** Время не удержало: отмотка была нужна, но не вышла. */
    public static final SoundEvent TEMPORAL_INSTABILITY = register("temporal_instability");
    /** Едва слышный тик: здоровья мало, но страховка есть. */
    public static final SoundEvent TEMPORAL_TICK = register("temporal_tick");

    private RewindSounds() {
    }

    /** Регистрация происходит при первом обращении к классу; метод её и вызывает. */
    public static void register() {
    }

    private static SoundEvent register(String path) {
        Identifier id = RewindMod.id(path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
