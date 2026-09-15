package io.github.eightiescrime.rewind.item;

import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import io.github.eightiescrime.rewind.network.JournalPayload;
import io.github.eightiescrime.rewind.persistence.TemporalAttachments;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Журнал времени: тетрадь, которая заполняется сама.
 *
 * <p>Идея в том, чтобы прогрессия была видна без цифр. Игрок не читает
 * «мастерство 340» — он видит, с чем уже сталкивался и что из этого его
 * однажды спасло.
 *
 * <p>Содержимое собирается на сервере в момент открытия: в самом предмете
 * ничего не хранится, поэтому журнал нельзя подделать, передать «полным»
 * или прочитать чужой.
 */
public class TemporalJournalItem extends Item {

    public TemporalJournalItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack, world.isClient);
        }

        TemporalState state = TemporalAttachments.of(player);
        if (!state.unlocked) {
            player.sendMessage(Text.translatable("rewind.journal.blank"), true);
            return TypedActionResult.success(stack, false);
        }
        if (!ServerPlayNetworking.canSend(player, JournalPayload.ID)) {
            // без клиентского мода показать журнал негде — но и молчать нельзя
            player.sendMessage(Text.translatable("rewind.journal.needs_client"), false);
            return TypedActionResult.success(stack, false);
        }

        ServerPlayNetworking.send(player, new JournalPayload(state.level, state.manualRewinds, entries(state)));
        return TypedActionResult.success(stack, false);
    }

    private static List<JournalPayload.Entry> entries(TemporalState state) {
        List<JournalPayload.Entry> entries = new ArrayList<>();
        for (TemporalDamageType type : state.journalThreats()) {
            entries.add(new JournalPayload.Entry(type, state.rescues.getOrDefault(type, 0)));
        }
        return entries;
    }
}
