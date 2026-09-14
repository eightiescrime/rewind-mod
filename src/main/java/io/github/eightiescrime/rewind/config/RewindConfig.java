package io.github.eightiescrime.rewind.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.eightiescrime.rewind.RewindMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Все балансные числа мода живут здесь и только здесь (RULE 8 из ТЗ).
 *
 * <p>Плоский класс с публичными полями — Gson читает и пишет его без
 * адаптеров, а игрок правит {@code config/rewind.json} руками.
 */
public final class RewindConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RewindConfig instance = new RewindConfig();

    // --- общее ---
    public boolean enabled = true;
    public int maxLevel = 5;

    // --- буфер снимков ---
    public double bufferSeconds = 10.0;
    /** Сколько снимков в секунду. Должно быть делителем 20, иначе округлится вниз. */
    public int snapshotsPerSecond = 20;
    public boolean dimensionChangeClearsBuffer = true;
    public boolean deathClearsBuffer = true;

    // --- энергия ---
    public double maxEnergy = 100.0;
    public double energyRegenPerSecond = 1.0;
    public double autoEnergyCost = 20.0;
    public double manualBaseCost = 40.0;
    public double manualCostPerSecond = 8.0;

    // --- временной долг ---
    public double debtRegenMultiplier = 0.25;
    public double debtDecayPerSecond = 0.5;
    public double maxDebtSeconds = 30.0;

    // --- параметры отмотки ---
    public double autoRewindSeconds = 0.6;
    public double manualMaxSeconds = 7.0;
    public double autoCooldownSeconds = 5.0;
    public double manualCooldownSeconds = 8.0;
    public double temporalProtectionSeconds = 0.8;

    // --- порог опасности ---
    /** HEALTH_PERCENT | DAMAGE_PERCENT | LETHAL_ONLY */
    public String dangerMode = "HEALTH_PERCENT";
    public double dangerHealthPercent = 0.20;
    public double dangerDamagePercent = 0.35;

    // --- мастерство и уровни ---
    public double masteryPerRewind = 20.0;
    public double masteryFirstEncounterBonus = 50.0;
    /** Множитель за 1-й, 2-й, 3-й… повтор одного источника. Хвост берётся последним. */
    public double[] diminishing = {1.0, 1.0, 0.75, 0.5, 0.25, 0.0};
    public int diminishingResetMinutes = 10;
    /** Порог мастерства для уровней 1..5. Индекс — уровень минус один. */
    public double[] masteryPerLevel = {0, 120, 320, 700, 1400};

    // --- временной перелом ---
    public double fractureSeconds = 20.0;

    // --- прочее ---
    /** Выдавать способность при первом входе. Для тестов; по умолчанию выключено. */
    public boolean grantOnFirstJoin = false;
    public boolean serverFeedbackEnabled = true;
    public boolean soundEnabled = true;

    public static RewindConfig get() {
        return instance;
    }

    /** Читает конфиг рядом с миром; если файла нет — создаёт с дефолтами. */
    public static RewindConfig loadOrCreate(Path file) {
        if (Files.exists(file)) {
            try {
                instance = load(file);
            } catch (RuntimeException e) {
                RewindMod.LOGGER.error("Rewind: не читается {}, беру значения по умолчанию", file, e);
                instance = new RewindConfig();
            }
        } else {
            instance = new RewindConfig();
            save(file, instance);
        }
        return instance;
    }

    public static RewindConfig load(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            RewindConfig parsed = GSON.fromJson(reader, RewindConfig.class);
            return parsed == null ? new RewindConfig() : parsed;
        } catch (IOException e) {
            throw new RuntimeException("Не удалось прочитать " + file, e);
        }
    }

    public static void save(Path file, RewindConfig config) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось записать " + file, e);
        }
    }

    /** Размер кольцевого буфера в снимках. */
    public int bufferCapacity() {
        return Math.max(1, (int) Math.round(bufferSeconds * snapshotsPerSecond));
    }

    /** Через сколько тиков делать следующий снимок. */
    public int ticksBetweenSnapshots() {
        return Math.max(1, 20 / Math.max(1, snapshotsPerSecond));
    }
}
