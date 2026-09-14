package io.github.eightiescrime.rewind.persistence;

import io.github.eightiescrime.rewind.RewindMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Привязка временного состояния к игроку через Data Attachment API.
 *
 * <p>Это штатный способ Fabric хранить свои данные на сущности, поэтому
 * миксин на запись/чтение NBT игрока не нужен (RULE 6). Состояние переживает
 * смерть ({@code copyOnDeath}) — обычная гибель не отнимает уровень
 * и мастерство (RULE 9).
 */
public final class TemporalAttachments {

    public static final AttachmentType<TemporalState> STATE = AttachmentRegistry.create(
            RewindMod.id("state"),
            builder -> builder
                    .persistent(TemporalState.CODEC)
                    .copyOnDeath()
                    .initializer(TemporalState::new));

    private TemporalAttachments() {
    }

    public static TemporalState of(ServerPlayerEntity player) {
        return player.getAttachedOrCreate(STATE);
    }

    /**
     * Помечает состояние изменённым.
     *
     * <p>Аттачмент мутабельный, и сам по себе он «грязным» не становится —
     * без этого вызова правки могут не доехать до сохранения.
     */
    public static void markDirty(ServerPlayerEntity player, TemporalState state) {
        player.setAttached(STATE, state);
    }
}
