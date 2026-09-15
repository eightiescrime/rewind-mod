package io.github.eightiescrime.rewind.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Разлом описан цепочкой json-файлов, и рвётся эта цепочка молча: опечатка
 * в ссылке не роняет игру, структура просто никогда не генерируется. Заметить
 * такое в живой игре почти невозможно — разлом и так редкий.
 *
 * <p>Поэтому цепочка проверяется здесь: набор структур → структура → пул →
 * размещённая особенность → настроенная особенность → id, под которым
 * особенность зарегистрирована в коде.
 */
class WorldgenResourcesTest {

    private static final Path DATA = Path.of("src/main/resources/data/rewind");
    private static final String RIFT = "rewind:temporal_rift";

    private static JsonObject read(String relative) throws IOException {
        Path path = DATA.resolve(relative);
        assertTrue(Files.exists(path), "нет файла " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void chainResolves() throws IOException {
        JsonObject set = read("worldgen/structure_set/temporal_rift.json");
        assertEquals(RIFT, set.getAsJsonArray("structures").get(0)
                .getAsJsonObject().get("structure").getAsString());

        JsonObject structure = read("worldgen/structure/temporal_rift.json");
        assertEquals("minecraft:jigsaw", structure.get("type").getAsString());
        assertEquals(RIFT, structure.get("start_pool").getAsString());

        JsonObject pool = read("worldgen/template_pool/temporal_rift.json");
        JsonObject element = pool.getAsJsonArray("elements").get(0)
                .getAsJsonObject().getAsJsonObject("element");
        assertEquals("minecraft:feature_pool_element", element.get("element_type").getAsString());
        assertEquals(RIFT, element.get("feature").getAsString());

        JsonObject placed = read("worldgen/placed_feature/temporal_rift.json");
        assertEquals(RIFT, placed.get("feature").getAsString());

        JsonObject configured = read("worldgen/configured_feature/temporal_rift.json");
        assertEquals(RIFT, configured.get("type").getAsString());
    }

    @Test
    void tagListsTheStructure() throws IOException {
        JsonObject tag = read("tags/worldgen/structure/temporal_rift.json");
        JsonElement first = tag.getAsJsonArray("values").get(0);
        assertEquals(RIFT, first.getAsString(), "компас ищет разлом по этому тегу");
    }
}
