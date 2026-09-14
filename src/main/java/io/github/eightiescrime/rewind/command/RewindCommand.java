package io.github.eightiescrime.rewind.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.persistence.TemporalAttachments;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import io.github.eightiescrime.rewind.progression.ProgressionManager;
import io.github.eightiescrime.rewind.temporal.RewindExecutor;
import io.github.eightiescrime.rewind.temporal.RewindResult;
import io.github.eightiescrime.rewind.temporal.TemporalBuffer;
import io.github.eightiescrime.rewind.temporal.TemporalManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Отладочные команды (ТЗ §48).
 *
 * <p>Всё поддерево требует прав оператора, а {@code force} и {@code debug} —
 * третьего уровня: первая умеет двигать игрока во времени в обход уровня,
 * вторая показывает внутренности.
 */
public final class RewindCommand {

    private RewindCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("rewind")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("level")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("value", IntegerArgumentType.integer(0, 5))
                                        .executes(RewindCommand::setLevel))))
                .then(CommandManager.literal("energy")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0))
                                        .executes(RewindCommand::setEnergy))))
                .then(CommandManager.literal("mastery")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0))
                                        .executes(RewindCommand::setMastery))))
                .then(CommandManager.literal("unlock")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(RewindCommand::unlock)))
                .then(CommandManager.literal("clear")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(RewindCommand::clear)))
                .then(CommandManager.literal("force")
                        .requires(source -> source.hasPermissionLevel(3))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(0.05, 60.0))
                                        .executes(RewindCommand::force))))
                .then(CommandManager.literal("debug")
                        .requires(source -> source.hasPermissionLevel(3))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(RewindCommand::debug))));
    }

    private static int setLevel(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        TemporalState state = TemporalAttachments.of(player);
        state.level = IntegerArgumentType.getInteger(ctx, "value");
        if (state.level > 0) {
            state.unlocked = true;
        }
        TemporalAttachments.markDirty(player, state);
        return reply(ctx, "уровень " + player.getGameProfile().getName() + " = " + state.level);
    }

    private static int setEnergy(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        TemporalState state = TemporalAttachments.of(player);
        state.energy = Math.min(RewindConfig.get().maxEnergy, DoubleArgumentType.getDouble(ctx, "value"));
        TemporalAttachments.markDirty(player, state);
        return reply(ctx, "энергия " + player.getGameProfile().getName() + " = " + state.energy);
    }

    private static int setMastery(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        TemporalState state = TemporalAttachments.of(player);
        state.mastery = DoubleArgumentType.getDouble(ctx, "value");
        ProgressionManager.tryLevelUp(player, state);
        TemporalAttachments.markDirty(player, state);
        return reply(ctx, "мастерство " + player.getGameProfile().getName() + " = " + state.mastery);
    }

    private static int unlock(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        TemporalState state = TemporalAttachments.of(player);
        boolean changed = ProgressionManager.unlock(player, state);
        return reply(ctx, changed ? "способность выдана" : "способность уже была");
    }

    private static int clear(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        TemporalManager.INSTANCE.clearBuffer(player);
        TemporalState state = TemporalAttachments.of(player);
        state.protection.clear();
        state.autoCooldown = 0;
        state.manualCooldown = 0;
        TemporalAttachments.markDirty(player, state);
        return reply(ctx, "буфер и защиты очищены");
    }

    private static int force(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        double seconds = DoubleArgumentType.getDouble(ctx, "seconds");
        // проверки безопасности позиции остаются на месте: команда не должна
        // засунуть игрока в блок
        RewindResult result = RewindExecutor.rewind(player, seconds, null, true);
        return reply(ctx, "отмотка на " + seconds + " с: " + result.name());
    }

    private static int debug(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        TemporalState state = TemporalAttachments.of(player);
        TemporalBuffer buffer = TemporalManager.INSTANCE.buffer(player);
        RewindConfig config = RewindConfig.get();

        StringBuilder sb = new StringBuilder();
        sb.append(player.getGameProfile().getName()).append('\n');
        sb.append("способность: ").append(state.unlocked ? "есть" : "нет")
                .append(", уровень ").append(state.level).append('\n');
        sb.append("мастерство: ").append(String.format("%.1f", state.mastery)).append('\n');
        sb.append("энергия: ").append(String.format("%.1f/%.1f", state.energy, config.maxEnergy)).append('\n');
        sb.append("долг: ").append(String.format("%.2f", state.debtSeconds)).append(" с\n");
        sb.append("кулдауны: авто ").append(state.autoCooldown)
                .append(", ручной ").append(state.manualCooldown)
                .append(", перелом ").append(state.fractureTicks).append('\n');
        sb.append("буфер: ").append(buffer.size()).append('/').append(buffer.capacity());
        if (!buffer.isEmpty()) {
            sb.append(" (тики ").append(buffer.oldestTick()).append("..").append(buffer.newestTick()).append(')');
        }
        sb.append('\n');
        sb.append("защиты: ").append(state.protection.describe()).append('\n');
        sb.append("встречи: ").append(state.encounters);

        String text = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
        return 1;
    }

    private static int reply(CommandContext<ServerCommandSource> ctx, String message) {
        ctx.getSource().sendFeedback(() -> Text.literal("[Отмотка] " + message), true);
        return 1;
    }
}
