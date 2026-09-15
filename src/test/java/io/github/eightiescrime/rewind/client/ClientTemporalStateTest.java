package io.github.eightiescrime.rewind.client;

import io.github.eightiescrime.rewind.network.TemporalStatePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HUD должен появляться по делу и гаснуть сам — это здесь и проверяется. */
class ClientTemporalStateTest {

    private static TemporalStatePayload calm() {
        return new TemporalStatePayload(true, 3, 100, 0, 0, 0, 7, 7);
    }

    private static TemporalStatePayload spent() {
        return new TemporalStatePayload(true, 3, 60, 20, 40, 0, 7, 2);
    }

    @BeforeEach
    void clear() {
        ClientTemporalState.reset();
    }

    @Test
    void молчит_пока_способность_не_открыта() {
        ClientTemporalState.accept(new TemporalStatePayload(false, 1, 100, 0, 0, 0, 0, 0));
        assertEquals(0.0f, ClientTemporalState.alpha(1000L));
    }

    @Test
    void молчит_в_покое() {
        ClientTemporalState.accept(calm());
        assertEquals(0.0f, ClientTemporalState.alpha(1000L));
    }

    @Test
    void проявляется_когда_время_потрачено() {
        ClientTemporalState.accept(spent());
        assertEquals(0.0f, ClientTemporalState.alpha(1000L), 0.001f);
        assertTrue(ClientTemporalState.alpha(1100L) > 0.4f);
        assertEquals(1.0f, ClientTemporalState.alpha(1300L));
    }

    @Test
    void гаснет_после_возвращения_в_норму() {
        ClientTemporalState.accept(spent());
        ClientTemporalState.alpha(1000L);
        ClientTemporalState.alpha(1300L);

        ClientTemporalState.accept(calm());
        assertEquals(1.0f, ClientTemporalState.alpha(2000L), "первые секунды держится");
        assertTrue(ClientTemporalState.alpha(4650L) < 1.0f, "потом начинает гаснуть");
        assertEquals(0.0f, ClientTemporalState.alpha(6000L), "и пропадает совсем");
    }

    @Test
    void не_мигает_если_отмотка_повторилась_на_затухании() {
        ClientTemporalState.accept(spent());
        ClientTemporalState.alpha(1000L);
        ClientTemporalState.alpha(1300L);
        ClientTemporalState.accept(calm());
        float fading = ClientTemporalState.alpha(4500L);
        assertTrue(fading > 0.0f && fading < 1.0f, "должно идти затухание, а не мгновенный ноль");

        ClientTemporalState.accept(spent());
        assertTrue(ClientTemporalState.alpha(4510L) >= fading - 0.05f,
                "проявление продолжается с текущей прозрачности");
    }
}
