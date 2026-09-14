package io.github.eightiescrime.rewind;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа мода «Отмотка».
 *
 * <p>Вся логика способности — серверная. Клиент ничего не решает и в фазах 1–3
 * вообще не получает своего кода: обратная связь даётся ванильными средствами
 * сервера (action bar, звук, частицы).
 */
public final class RewindMod implements ModInitializer {
    public static final String MOD_ID = "rewind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Rewind: временное ядро инициализировано");
    }
}
