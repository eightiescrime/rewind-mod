package io.github.eightiescrime.rewind.persistence;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalStateTest {

    @Test
    void codecRoundTripsAllFields() {
        TemporalState s = new TemporalState();
        s.unlocked = true;
        s.level = 3;
        s.mastery = 412.5;
        s.energy = 77.0;
        s.debtSeconds = 2.5;
        s.autoCooldown = 14;
        s.encounters.put(TemporalDamageType.EXPLOSION, 4);
        s.recentSources.put("creeper-uuid", 2);

        JsonElement json = TemporalState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
        TemporalState back = TemporalState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertTrue(back.unlocked);
        assertEquals(3, back.level);
        assertEquals(412.5, back.mastery);
        assertEquals(77.0, back.energy);
        assertEquals(2.5, back.debtSeconds);
        assertEquals(14, back.autoCooldown);
        assertEquals(4, back.encounters.get(TemporalDamageType.EXPLOSION));
        assertEquals(2, back.recentSources.get("creeper-uuid"));
    }

    @Test
    void oldSavesWithoutFieldsStillLoad() {
        JsonElement json = TemporalState.CODEC
                .encodeStart(JsonOps.INSTANCE, new TemporalState()).getOrThrow();
        json.getAsJsonObject().remove("recentSources");
        json.getAsJsonObject().remove("fractureTicks");

        TemporalState back = TemporalState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1, back.level);
        assertTrue(back.recentSources.isEmpty());
        assertEquals(0, back.fractureTicks);
    }

    @Test
    void журнал_показывает_только_встреченное_и_по_нарастанию() {
        TemporalState state = new TemporalState();
        state.encounters.put(TemporalDamageType.EXPLOSION, 1);
        state.encounters.put(TemporalDamageType.MELEE, 4);

        assertEquals(List.of(TemporalDamageType.MELEE, TemporalDamageType.EXPLOSION),
                state.journalThreats(),
                "порядок объявления типов — это и есть порядок нарастания угрозы");
        assertTrue(new TemporalState().journalThreats().isEmpty(),
                "пустой журнал не рассказывает наперёд, что бывает дальше");
    }
}
