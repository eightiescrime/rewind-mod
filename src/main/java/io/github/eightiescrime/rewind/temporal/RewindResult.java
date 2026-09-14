package io.github.eightiescrime.rewind.temporal;

/** Чем закончилась попытка отмотки. Используется и логикой, и {@code /rewind debug}. */
public enum RewindResult {
    SUCCESS,
    DISABLED,
    DEAD,
    NOT_UNLOCKED,
    WRONG_LEVEL,
    ON_COOLDOWN,
    NO_ENERGY,
    NO_BUFFER,
    NO_SAFE_SNAPSHOT,
    TOO_DEEP;

    public boolean ok() {
        return this == SUCCESS;
    }

    /** Ключ локализации для сообщения игроку. */
    public String translationKey() {
        return "rewind.result." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
