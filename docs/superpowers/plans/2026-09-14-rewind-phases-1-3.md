# Мод «Отмотка» (Rewind) — план реализации, фазы 1–3

> **Исполнение:** последовательно в основной сессии (владелец запретил
> подагентов в этом проекте). Шаги помечены чекбоксами `- [ ]`.

**Цель:** рабочее серверное ядро «Отмотки» для Minecraft 1.21.1 Fabric:
кольцевой буфер снимков игрока, классификация урона, автоматическая
отмотка уровней 1–4, энергия/долг/кулдауны, прогрессия и мастерство,
сохранение состояния между сессиями, dev-команды.

**Архитектура:** вся логика — authoritative server-side. Состояние игрока
живёт в Data Attachment API (персистентное), кольцевой буфер — в памяти
сервера и никогда не сохраняется. Перехват урона — через
`ServerLivingEntityEvents.ALLOW_DAMAGE` (Fabric API, миксины не нужны).
Тик — через `ServerTickEvents.END_SERVER_TICK` с обходом списка игроков.
Никакого клиентского кода в этих фазах: обратная связь даётся
серверными средствами (action bar, звук, частицы).

**Tech Stack:** Minecraft 1.21.1, Yarn `1.21.1+build.3`, Fabric Loader
`0.19.5`, Fabric API `0.116.17+1.21.1`, Fabric Loom `1.17.20`, Gradle 8.14,
Java 21, JUnit 5, Gson (идёт с Minecraft).

**Спека:** `docs/spec/rewind-tz.md` — читать вместе с планом.

## Состояние на 2026-09-14

Задачи 1–12 и 14 выполнены, всё собрано и покрыто 33 юнит-тестами
(`./gradlew build` зелёный, миксинов в моде нет ни одного).

Единственное незакрытое — задача 13: живой прогон чек-листа эксплойтов
и поведения в игре. Список готов и ждёт владельца:
`docs/superpowers/notes/2026-09-14-exploit-checklist.md`.

Расхождения с планом по ходу работы:
- маппинги оставлены Yarn, хотя fabric-example-mod для 1.21.1 перешёл на
  официальные мойанговские;
- плагин Loom — `net.fabricmc.fabric-loom-remap` 1.17-SNAPSHOT, а не старый
  `fabric-loom`; Gradle 9.5.1 вместо 8.14;
- `AttachmentRegistry.builder()` оказался устаревшим, используется
  `AttachmentRegistry.create(id, builder -> ...)`;
- `HungerManager.setExhaustion` существует в 1.21.1, accessor-миксин
  не понадобился;
- `ProgressionManager.rewardRewind` принимает флаг первой встречи от
  перехватчика, потому что встреча засчитывается раньше отмотки.

---

## Глобальные ограничения

Действуют в каждой задаче:

- **RULE 1.** Мир никогда не откатывается: ни блоки, ни контейнеры, ни мобы,
  ни снаряды, ни время, ни погода, ни другие игроки.
- **RULE 2.** Клиенту не доверять. В фазах 1–3 клиентских пакетов нет вообще.
- **RULE 3.** Не сериализовать Player NBT каждый тик. Снимок — record из
  примитивов + список эффектов.
- **RULE 4.** Инвентарь, опыт, прочность, продвижения не откатываются никогда.
- **RULE 6.** Миксин только там, где нет хука Fabric API. В фазах 1–3
  миксины не нужны ни разу; если кажется, что нужен — сначала искать событие.
- **RULE 8.** Балансные числа только в `RewindConfig`, ни одного магического
  числа в логике.
- **RULE 9.** Обычная смерть не отнимает уровень и мастерство.
- Пакет: `io.github.eightiescrime.rewind`. Mod id: `rewind`.
- Тексты для игрока — через `Text.translatable` с ключами в
  `assets/rewind/lang/en_us.json` и `ru_ru.json`.
- Каждая задача заканчивается зелёным `./gradlew build` и коммитом.

---

### Задача 1: Скелет проекта Fabric 1.21.1

**Файлы:**
- Create: `gradle.properties`, `settings.gradle`, `build.gradle`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`
- Create: `src/main/java/io/github/eightiescrime/rewind/RewindMod.java`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/resources/assets/rewind/lang/en_us.json`, `ru_ru.json`
- Create: `.gitignore`

- [ ] **Шаг 1: gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

minecraft_version=1.21.1
yarn_mappings=1.21.1+build.3
loader_version=0.19.5

mod_version=0.1.0
maven_group=io.github.eightiescrime
archives_base_name=rewind

fabric_version=0.116.17+1.21.1
```

- [ ] **Шаг 2: settings.gradle**

```groovy
pluginManagement {
    repositories {
        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = 'rewind'
```

- [ ] **Шаг 3: build.gradle**

```groovy
plugins {
    id 'fabric-loom' version '1.17.20'
}

version = project.mod_version
group = project.maven_group

base { archivesName = project.archives_base_name }

repositories { mavenCentral() }

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") { expand "version": project.version }
}

tasks.withType(JavaCompile).configureEach { it.options.release = 21 }

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

test { useJUnitPlatform() }
```

- [ ] **Шаг 4: fabric.mod.json**

Точка входа только `main` — клиента в этих фазах нет, миксинов тоже.

```json
{
  "schemaVersion": 1,
  "id": "rewind",
  "version": "${version}",
  "name": "Rewind",
  "description": "Личное время игрока: отматывается игрок, мир — никогда.",
  "authors": ["eightiescrime"],
  "contact": { "sources": "https://github.com/eightiescrime/rewind-mod" },
  "license": "MIT",
  "environment": "*",
  "entrypoints": { "main": ["io.github.eightiescrime.rewind.RewindMod"] },
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~1.21.1",
    "java": ">=21",
    "fabric-api": "*"
  }
}
```

- [ ] **Шаг 5: RewindMod.java — пока только логгер и id**

```java
package io.github.eightiescrime.rewind;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
```

- [ ] **Шаг 6: `.gitignore`**

```gitignore
.gradle/
build/
run/
.idea/
*.iml
out/
```

- [ ] **Шаг 7: обёртка Gradle и сборка**

Run: `gradle wrapper --gradle-version 8.14` (если `gradle` нет в PATH —
взять wrapper из fabric example mod).
Затем: `./gradlew build`
Expected: BUILD SUCCESSFUL, jar в `build/libs/rewind-0.1.0.jar`.

Если loom 1.17.20 конфликтует с loader 0.19.5 — понизить loader до
`0.16.14` (он влияет только на dev-запуск, `depends` остаётся `>=0.16.0`).

- [ ] **Шаг 8: коммит**

```bash
git add -A && git commit -m "chore: Fabric 1.21.1 project skeleton"
```

---

### Задача 2: Конфигурация

**Файлы:**
- Create: `src/main/java/io/github/eightiescrime/rewind/config/RewindConfig.java`
- Test: `src/test/java/io/github/eightiescrime/rewind/config/RewindConfigTest.java`

**Интерфейсы:**
- Produces: `RewindConfig.get()`, `RewindConfig.load(Path)`, `RewindConfig.save(Path, RewindConfig)`;
  публичные поля со значениями по умолчанию из ТЗ §6–§9, §49.

- [ ] **Шаг 1: тест на дефолты и round-trip**

```java
@Test
void defaultsMatchSpec() {
    RewindConfig c = new RewindConfig();
    assertEquals(100.0, c.maxEnergy);
    assertEquals(20.0, c.autoEnergyCost);
    assertEquals(40.0, c.manualBaseCost);
    assertEquals(8.0, c.manualCostPerSecond);
    assertEquals(0.6, c.autoRewindSeconds);
    assertEquals(5.0, c.autoCooldownSeconds);
    assertEquals(0.8, c.temporalProtectionSeconds);
    assertEquals(10.0, c.bufferSeconds);
    assertEquals(20, c.snapshotsPerSecond);
}

@Test
void savesAndLoadsRoundTrip(@TempDir Path dir) {
    Path f = dir.resolve("rewind.json");
    RewindConfig original = new RewindConfig();
    original.maxEnergy = 250.0;
    RewindConfig.save(f, original);
    assertEquals(250.0, RewindConfig.load(f).maxEnergy);
}
```

- [ ] **Шаг 2: запустить — падает (класса нет)**

Run: `./gradlew test`

- [ ] **Шаг 3: реализация**

Плоский класс с публичными полями, Gson с `setPrettyPrinting()`.
Загрузка в `onInitialize` из
`FabricLoader.getInstance().getConfigDir().resolve("rewind.json")`;
файла нет — записать дефолт. Поля (все из ТЗ §49):

```java
public boolean enabled = true;
public int maxLevel = 5;

// буфер
public double bufferSeconds = 10.0;
public int snapshotsPerSecond = 20;      // 1..20, делитель тика
public boolean dimensionChangeClearsBuffer = true;
public boolean deathClearsBuffer = true;

// энергия
public double maxEnergy = 100.0;
public double energyRegenPerSecond = 1.0;
public double autoEnergyCost = 20.0;
public double manualBaseCost = 40.0;
public double manualCostPerSecond = 8.0;

// долг
public double debtRegenMultiplier = 0.25;
public double debtDecayPerSecond = 0.5;
public double maxDebtSeconds = 30.0;

// отмотка
public double autoRewindSeconds = 0.6;
public double manualMaxSeconds = 7.0;
public double autoCooldownSeconds = 5.0;
public double manualCooldownSeconds = 8.0;
public double temporalProtectionSeconds = 0.8;

// порог опасности
public String dangerMode = "HEALTH_PERCENT"; // HEALTH_PERCENT | DAMAGE_PERCENT | LETHAL_ONLY
public double dangerHealthPercent = 0.20;
public double dangerDamagePercent = 0.35;

// мастерство и уровни
public double masteryPerRewind = 20.0;
public double masteryFirstEncounterBonus = 50.0;
public double[] diminishing = {1.0, 1.0, 0.75, 0.5, 0.25, 0.0};
public int diminishingResetMinutes = 10;
public double[] masteryPerLevel = {0, 120, 320, 700, 1400};

// перелом
public double fractureSeconds = 20.0;

// прочее
public boolean grantOnFirstJoin = false;  // для тестов, по умолчанию выкл
public boolean serverFeedbackEnabled = true;
public boolean soundEnabled = true;
```

- [ ] **Шаг 4: тесты зелёные**

Run: `./gradlew test`

- [ ] **Шаг 5: коммит**

```bash
git add -A && git commit -m "feat(config): RewindConfig with spec defaults"
```

---

### Задача 3: Снимок и кольцевой буфер

**Файлы:**
- Create: `.../temporal/TemporalSnapshot.java`
- Create: `.../temporal/TemporalBuffer.java`
- Test: `src/test/java/.../temporal/TemporalBufferTest.java`

**Интерфейсы:**
- Produces:
  - `record TemporalSnapshot(long tick, double x, double y, double z, double velX, double velY, double velZ, float yaw, float pitch, float health, float absorption, int foodLevel, float saturation, float exhaustion, int air, int fireTicks, int frozenTicks, float fallDistance, boolean onGround, int selectedSlot, List<StatusEffectInstance> effects)`
  - `TemporalBuffer(int capacity)`, `push(TemporalSnapshot)`,
    `Optional<TemporalSnapshot> findAtOrBefore(long tick)`,
    `void dropFrom(long tick)`, `void clear()`, `int size()`,
    `long oldestTick()`, `long newestTick()`,
    `Optional<TemporalSnapshot> previousOf(TemporalSnapshot)` — для поиска
    безопасного снимка шагом назад.

- [ ] **Шаг 1: тесты буфера (чистая логика, без Minecraft)**

Снимки-заглушки через фабрику `snap(long tick)`: нули и `List.of()`.

```java
@Test
void overwritesOldestWhenFull() {
    TemporalBuffer b = new TemporalBuffer(3);
    for (long t = 1; t <= 5; t++) b.push(snap(t));
    assertEquals(3, b.size());
    assertEquals(3, b.oldestTick());
    assertEquals(5, b.newestTick());
}

@Test
void findsNearestSnapshotAtOrBeforeTarget() {
    TemporalBuffer b = new TemporalBuffer(10);
    b.push(snap(10)); b.push(snap(20)); b.push(snap(30));
    assertEquals(20, b.findAtOrBefore(25).orElseThrow().tick());
    assertEquals(30, b.findAtOrBefore(30).orElseThrow().tick());
    assertTrue(b.findAtOrBefore(5).isEmpty());
}

@Test
void dropFromRemovesTargetAndEverythingNewer() {
    TemporalBuffer b = new TemporalBuffer(10);
    for (long t = 1; t <= 5; t++) b.push(snap(t));
    b.dropFrom(3);
    assertEquals(2, b.newestTick());
    assertEquals(2, b.size());
    assertEquals(2, b.findAtOrBefore(4).orElseThrow().tick());
}

@Test
void clearEmptiesBuffer() {
    TemporalBuffer b = new TemporalBuffer(4);
    b.push(snap(1));
    b.clear();
    assertEquals(0, b.size());
    assertTrue(b.findAtOrBefore(1).isEmpty());
}
```

- [ ] **Шаг 2: запустить, убедиться что падает**

Run: `./gradlew test --tests '*TemporalBufferTest*'`

- [ ] **Шаг 3: реализация**

Массив фиксированного размера + `head` + `size`. `findAtOrBefore` идёт от
новейшего к старому линейно — в буфере максимум 200 элементов
(`ponytail: линейный поиск по ≤200 снимкам, бинарный если буфер вырастет
на порядок`). `dropFrom` двигает `head`/`size` назад без перевыделения.
Снимок — `record`, список эффектов оборачивать в `List.copyOf`.

**Важно:** `TemporalSnapshot` импортирует `StatusEffectInstance`, поэтому
тесты используют `List.of()` и классы Minecraft не инициализируются.

- [ ] **Шаг 4: тесты зелёные, коммит**

```bash
git add -A && git commit -m "feat(temporal): immutable snapshot + fixed-size ring buffer"
```

---

### Задача 4: Чистые формулы (`TemporalRules`)

**Файлы:**
- Create: `.../temporal/TemporalRules.java`
- Test: `src/test/java/.../temporal/TemporalRulesTest.java`

Вся арифметика живёт здесь, чтобы тестироваться без Minecraft.

**Интерфейсы:**
- Produces (все `static`):
  - `double manualCost(RewindConfig c, double seconds)`
  - `double regenPerTick(RewindConfig c, double debtSeconds)`
  - `boolean isDangerous(RewindConfig c, float health, float absorption, float maxHealth, float incoming)`
  - `double masteryMultiplier(RewindConfig c, int timesSeenRecently)`
  - `int levelFor(RewindConfig c, double mastery, Set<TemporalDamageType> encountered)`
  - `int secondsToTicks(double seconds)`

- [ ] **Шаг 1: тесты**

```java
@Test
void manualCostFollowsSpecFormula() {
    RewindConfig c = new RewindConfig();      // base 40, perSecond 8
    assertEquals(40.0, TemporalRules.manualCost(c, 0));
    assertEquals(80.0, TemporalRules.manualCost(c, 5));
}

@Test
void debtSlowsRegeneration() {
    RewindConfig c = new RewindConfig();      // regen 1/sec, multiplier 0.25
    double free = TemporalRules.regenPerTick(c, 0);
    double indebted = TemporalRules.regenPerTick(c, 4.0);
    assertEquals(1.0 / 20.0, free, 1e-9);
    assertEquals(free * 0.25, indebted, 1e-9);
}

@Test
void dangerTriggersOnlyNearDeath() {
    RewindConfig c = new RewindConfig();      // HEALTH_PERCENT 0.20
    assertFalse(TemporalRules.isDangerous(c, 20f, 0f, 20f, 2f));
    assertTrue(TemporalRules.isDangerous(c, 6f, 0f, 20f, 3f));
    assertTrue(TemporalRules.isDangerous(c, 6f, 0f, 20f, 99f));
}

@Test
void absorptionCountsAsHealth() {
    RewindConfig c = new RewindConfig();
    assertFalse(TemporalRules.isDangerous(c, 6f, 10f, 20f, 5f));
}

@Test
void lethalOnlyModeIgnoresNonLethalHits() {
    RewindConfig c = new RewindConfig();
    c.dangerMode = "LETHAL_ONLY";
    assertFalse(TemporalRules.isDangerous(c, 6f, 0f, 20f, 5f));
    assertTrue(TemporalRules.isDangerous(c, 6f, 0f, 20f, 6f));
}

@Test
void masteryDiminishesOnRepeatedSource() {
    RewindConfig c = new RewindConfig();      // {1, 1, .75, .5, .25, 0}
    assertEquals(1.0,  TemporalRules.masteryMultiplier(c, 0));
    assertEquals(1.0,  TemporalRules.masteryMultiplier(c, 1));
    assertEquals(0.75, TemporalRules.masteryMultiplier(c, 2));
    assertEquals(0.25, TemporalRules.masteryMultiplier(c, 4));
    assertEquals(0.0,  TemporalRules.masteryMultiplier(c, 5));
    assertEquals(0.0,  TemporalRules.masteryMultiplier(c, 99));
}

@Test
void levelRequiresBothMasteryAndEncounter() {
    RewindConfig c = new RewindConfig();      // thresholds {0,120,320,700,1400}
    assertEquals(1, TemporalRules.levelFor(c, 5000, Set.of()));
    assertEquals(2, TemporalRules.levelFor(c, 5000, Set.of(TemporalDamageType.PROJECTILE)));
    assertEquals(1, TemporalRules.levelFor(c, 10, Set.of(TemporalDamageType.PROJECTILE)));
}
```

- [ ] **Шаг 2: запустить, падает. Шаг 3: реализовать.**

`levelFor`: идти от 2 к `maxLevel`, для каждого уровня требовать
`mastery >= masteryPerLevel[level-1]` И встречу с типом, который этот
уровень открывает (`TemporalDamageType.unlockedAtLevel(level)`: 2 —
PROJECTILE, 3 — EXPLOSION, 4 — любой из environmental, 5 — встреч не
требует, только мастерство). Останавливаться на первом непройденном.

`isDangerous` при `DAMAGE_PERCENT`: `incoming >= maxHealth * dangerDamagePercent`.
Летальность (`health + absorption - incoming <= 0`) считается опасной во
всех режимах.

- [ ] **Шаг 4: тесты зелёные, коммит**

```bash
git add -A && git commit -m "feat(temporal): pure rules for energy, danger, mastery, levels"
```

---

### Задача 5: Классификация урона

**Файлы:**
- Create: `.../damage/TemporalDamageType.java`
- Create: `.../damage/DamageContext.java`
- Create: `.../damage/DamageClassifier.java`
- Test: `src/test/java/.../damage/TemporalDamageTypeTest.java`

**Интерфейсы:**
- Produces:
  - `enum TemporalDamageType { MELEE, PROJECTILE, EXPLOSION, FIRE, LAVA, FALL, DROWNING, POISON, MAGIC, WITHER, FREEZING, SUFFOCATION, CACTUS, ENVIRONMENTAL, OTHER }`
    с `int requiredLevel()`, `boolean isEnvironmental()` и
    `static Set<TemporalDamageType> unlockedAtLevel(int level)`.
  - `record DamageContext(TemporalDamageType type, DamageSource source, Entity attacker, Entity direct, Vec3d position, float amount, long tick)`
  - `DamageClassifier.classify(DamageSource, LivingEntity victim)`
  - `DamageClassifier.context(ServerPlayerEntity, DamageSource, float amount)`
  - `DamageClassifier.sourceKey(DamageContext)` → `String`: uuid атакующего,
    если он есть, иначе имя типа урона. Ключ нужен и для diminishing
    returns, и для source-aware защиты.

- [ ] **Шаг 1: тест уровней (без Minecraft-классов)**

```java
@Test
void requiredLevelsMatchProgression() {
    assertEquals(1, TemporalDamageType.MELEE.requiredLevel());
    assertEquals(2, TemporalDamageType.PROJECTILE.requiredLevel());
    assertEquals(3, TemporalDamageType.EXPLOSION.requiredLevel());
    assertEquals(4, TemporalDamageType.LAVA.requiredLevel());
    assertEquals(4, TemporalDamageType.FALL.requiredLevel());
    assertEquals(5, TemporalDamageType.OTHER.requiredLevel());
}

@Test
void environmentalTypesAreGrouped() {
    assertTrue(TemporalDamageType.LAVA.isEnvironmental());
    assertFalse(TemporalDamageType.MELEE.isEnvironmental());
}
```

- [ ] **Шаг 2: запустить, падает. Шаг 3: реализовать enum и `DamageContext`.**

- [ ] **Шаг 4: `DamageClassifier` — порядок проверок важен**

Использовать теги 1.21.1, а не строковые имена:

```java
public static TemporalDamageType classify(DamageSource s, LivingEntity victim) {
    if (s.isIn(DamageTypeTags.IS_PROJECTILE)) return TemporalDamageType.PROJECTILE;
    if (s.isIn(DamageTypeTags.IS_EXPLOSION))  return TemporalDamageType.EXPLOSION;
    if (s.isOf(DamageTypes.LAVA))             return TemporalDamageType.LAVA;
    if (s.isIn(DamageTypeTags.IS_FIRE))       return TemporalDamageType.FIRE;
    if (s.isIn(DamageTypeTags.IS_FALL))       return TemporalDamageType.FALL;
    if (s.isIn(DamageTypeTags.IS_DROWNING))   return TemporalDamageType.DROWNING;
    if (s.isIn(DamageTypeTags.IS_FREEZING))   return TemporalDamageType.FREEZING;
    if (s.isOf(DamageTypes.IN_WALL))          return TemporalDamageType.SUFFOCATION;
    if (s.isOf(DamageTypes.CACTUS) || s.isOf(DamageTypes.SWEET_BERRY_BUSH))
        return TemporalDamageType.CACTUS;
    if (s.isOf(DamageTypes.WITHER) || s.isOf(DamageTypes.WITHER_SKULL))
        return TemporalDamageType.WITHER;
    if (s.isOf(DamageTypes.MAGIC) || s.isOf(DamageTypes.INDIRECT_MAGIC)) {
        // ванильный яд бьёт источником MAGIC — отличаем по активному эффекту
        return victim.hasStatusEffect(StatusEffects.POISON)
            ? TemporalDamageType.POISON : TemporalDamageType.MAGIC;
    }
    if (s.getAttacker() != null && s.getSource() == s.getAttacker())
        return TemporalDamageType.MELEE;
    if (s.isOf(DamageTypes.STARVE) || s.isOf(DamageTypes.HOT_FLOOR)
        || s.isOf(DamageTypes.FALLING_BLOCK) || s.isOf(DamageTypes.FLY_INTO_WALL))
        return TemporalDamageType.ENVIRONMENTAL;
    return TemporalDamageType.OTHER;
}
```

Сверить в IDE, что `DamageTypeTags.IS_FALL` и `IS_FREEZING` существуют
в yarn 1.21.1; если имя другое — взять фактическое. `getSource()` — это
непосредственная сущность, `getAttacker()` — инициатор; не перепутать.

- [ ] **Шаг 5: тесты зелёные, коммит**

```bash
git add -A && git commit -m "feat(damage): temporal damage types and tag-based classifier"
```

---

### Задача 6: Состояние игрока и персистентность

**Файлы:**
- Create: `.../persistence/TemporalState.java`
- Create: `.../persistence/TemporalAttachments.java`
- Test: `src/test/java/.../persistence/TemporalStateTest.java`

**Интерфейсы:**
- Produces:
  - `final class TemporalState` — мутабельный:
    `boolean unlocked; int level; double mastery; double energy; double debtSeconds;`
    `int autoCooldown; int manualCooldown; int fractureTicks;`
    `Map<TemporalDamageType,Integer> encounters;`
    `Map<String,Integer> recentSources;` `int recentResetTicks;`
    `transient TemporalProtection protection;`
  - `Codec<TemporalState> TemporalState.CODEC`
  - `TemporalAttachments.STATE` — `AttachmentType<TemporalState>`
  - `TemporalAttachments.of(ServerPlayerEntity)` — get-or-create
  - `TemporalAttachments.markDirty(ServerPlayerEntity)` — обязательный
    `setAttached`, иначе изменения не сохранятся

- [ ] **Шаг 1: тест кодека round-trip через `JsonOps`**

```java
@Test
void codecRoundTripsAllFields() {
    TemporalState s = new TemporalState();
    s.unlocked = true; s.level = 3; s.mastery = 412.5; s.energy = 77.0;
    s.debtSeconds = 2.5; s.encounters.put(TemporalDamageType.EXPLOSION, 4);
    JsonElement json = TemporalState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
    TemporalState back = TemporalState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    assertTrue(back.unlocked);
    assertEquals(3, back.level);
    assertEquals(412.5, back.mastery);
    assertEquals(4, back.encounters.get(TemporalDamageType.EXPLOSION));
}
```

DFU (`com.mojang.serialization`) приходит с зависимостью Minecraft — тест
не требует инициализации игры.

- [ ] **Шаг 2: запустить, падает. Шаг 3: реализовать.**

`TemporalDamageType` кодировать через
`Codec.STRING.xmap(TemporalDamageType::valueOf, Enum::name)`, карты —
`Codec.unboundedMap(...)`. Кулдауны и `recentResetTicks` включить в кодек
(ТЗ §40 «cooldowns, если это необходимо»), чтобы перезаход не сбрасывал
кулдаун. `protection` в кодек **не** включать — она живёт только в памяти.

Регистрация:

```java
public static final AttachmentType<TemporalState> STATE =
    AttachmentRegistry.<TemporalState>builder()
        .persistent(TemporalState.CODEC)
        .copyOnDeath()
        .initializer(TemporalState::new)
        .buildAndRegister(RewindMod.id("state"));
```

Сверить имена builder-методов в fabric-api 0.116.17
(`net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry`) и поправить,
если отличаются.

- [ ] **Шаг 4: тесты зелёные, коммит**

```bash
git add -A && git commit -m "feat(persistence): persistent temporal state via attachment api"
```

---

### Задача 7: Менеджер — тик, захват снимков, регенерация

**Файлы:**
- Create: `.../temporal/TemporalManager.java`
- Modify: `.../RewindMod.java` (регистрация событий)

**Интерфейсы:**
- Consumes: `TemporalBuffer`, `TemporalSnapshot`, `TemporalState`, `TemporalRules`, `RewindConfig`
- Produces: `TemporalManager.INSTANCE`, `TemporalBuffer buffer(ServerPlayerEntity)`,
  `void clearBuffer(ServerPlayerEntity)`, `void tick(MinecraftServer)`,
  `TemporalSnapshot capture(ServerPlayerEntity)`

- [ ] **Шаг 1: реализация менеджера**

Буферы — `Map<UUID, TemporalBuffer>`, обычный `HashMap`: доступ только
с серверного треда (`ponytail: single-threaded access, ConcurrentHashMap
если появятся асинхронные потребители`).

В `tick(server)` по каждому игроку из `server.getPlayerManager().getPlayerList()`:

1. `!config.enabled`, игрок мёртв или в spectator — пропустить, снимки не копить;
2. каждые `20 / snapshotsPerSecond` тиков — `buffer.push(capture(player))`;
3. регенерация: `energy = min(maxEnergy, energy + regenPerTick(config, debt))`,
   но при `fractureTicks > 0` регенерации нет;
4. `debtSeconds = max(0, debtSeconds - debtDecayPerSecond / 20)`;
5. уменьшить `autoCooldown`, `manualCooldown`, `fractureTicks`,
   вызвать `state.protection.tick()`;
6. `recentResetTicks--`; при нуле — очистить `recentSources` и выставить
   `diminishingResetMinutes * 60 * 20`;
7. в конце итерации один раз `TemporalAttachments.markDirty(player)`.

Захват:

```java
public TemporalSnapshot capture(ServerPlayerEntity p) {
    return new TemporalSnapshot(
        p.getServerWorld().getTime(),
        p.getX(), p.getY(), p.getZ(),
        p.getVelocity().x, p.getVelocity().y, p.getVelocity().z,
        p.getYaw(), p.getPitch(),
        p.getHealth(), p.getAbsorptionAmount(),
        p.getHungerManager().getFoodLevel(),
        p.getHungerManager().getSaturationLevel(),
        p.getHungerManager().getExhaustion(),
        p.getAir(), p.getFireTicks(), p.getFrozenTicks(),
        p.fallDistance, p.isOnGround(),
        p.getInventory().selectedSlot,
        p.getStatusEffects().stream().map(StatusEffectInstance::new).toList()
    );
}
```

Имена `getExhaustion`, `selectedSlot`, `getFrozenTicks` сверить с yarn
1.21.1. Если геттера exhaustion нет — добавить accessor-миксин **только
на него**, задокументировать (допустимый случай по RULE 6).

- [ ] **Шаг 2: регистрация событий в `RewindMod.onInitialize`**

```java
ServerTickEvents.END_SERVER_TICK.register(TemporalManager.INSTANCE::tick);

ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
    TemporalManager.INSTANCE.clearBuffer(handler.player));

ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, dest) -> {
    if (RewindConfig.get().dimensionChangeClearsBuffer)
        TemporalManager.INSTANCE.clearBuffer(player);
});

ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
    TemporalManager.INSTANCE.clearBuffer(newPlayer));

ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
    if (entity instanceof ServerPlayerEntity p && RewindConfig.get().deathClearsBuffer)
        TemporalManager.INSTANCE.clearBuffer(p);
});
```

- [ ] **Шаг 3: проверка вживую**

Run: `./gradlew runServer` (принять EULA в `run/eula.txt`), зайти,
временным `LOGGER.debug` убедиться, что буфер наполняется до 200 и не
растёт дальше. Debug-лог после проверки убрать.

- [ ] **Шаг 4: коммит**

```bash
git add -A && git commit -m "feat(temporal): per-tick snapshot capture, regen, buffer lifecycle"
```

---

### Задача 8: Исполнитель отмотки

**Файлы:**
- Create: `.../temporal/RewindExecutor.java`
- Create: `.../temporal/TemporalProtection.java`
- Test: `src/test/java/.../temporal/TemporalProtectionTest.java`

**Интерфейсы:**
- Produces:
  - `enum RewindResult { SUCCESS, NO_BUFFER, NO_SAFE_SNAPSHOT, NO_ENERGY, ON_COOLDOWN, NOT_UNLOCKED, WRONG_LEVEL, DEAD, DISABLED }`
  - `RewindResult RewindExecutor.rewind(ServerPlayerEntity p, double seconds, DamageContext cause, boolean manual)`
  - `final class TemporalProtection`: `void grant(String sourceKey, TemporalDamageType type, int ticks)`,
    `boolean covers(String sourceKey, TemporalDamageType type)`, `void tick()`

- [ ] **Шаг 1: тест защиты (чистая логика)**

```java
@Test
void protectionCoversSameSourceAndTypeOnly() {
    TemporalProtection p = new TemporalProtection();
    p.grant("zombie-uuid", TemporalDamageType.MELEE, 16);
    assertTrue(p.covers("zombie-uuid", TemporalDamageType.MELEE));
    assertFalse(p.covers("skeleton-uuid", TemporalDamageType.MELEE));
    assertFalse(p.covers("zombie-uuid", TemporalDamageType.PROJECTILE));
}

@Test
void environmentalProtectionCoversTypeRegardlessOfSource() {
    TemporalProtection p = new TemporalProtection();
    p.grant("minecraft:lava", TemporalDamageType.LAVA, 16);
    assertTrue(p.covers("anything", TemporalDamageType.LAVA));
}

@Test
void protectionExpires() {
    TemporalProtection p = new TemporalProtection();
    p.grant("zombie-uuid", TemporalDamageType.MELEE, 2);
    p.tick(); p.tick();
    assertFalse(p.covers("zombie-uuid", TemporalDamageType.MELEE));
}
```

- [ ] **Шаг 2: запустить, падает. Шаг 3: реализовать защиту.**

Для `isEnvironmental()`-типов ключ источника игнорируется (у лавы нет
атакующего, а повтор придёт следующим тиком) — это и есть «source-aware,
а не бессмертие от всего» из ТЗ §10.

- [ ] **Шаг 4: реализовать `RewindExecutor.rewind`**

Порядок строго такой:

1. `config.enabled`, иначе `DISABLED`;
2. игрок жив и не в death state, иначе `DEAD` (ТЗ §22);
3. `state.unlocked`, иначе `NOT_UNLOCKED`;
4. для manual — `state.level >= 5`, иначе `WRONG_LEVEL`;
5. кулдаун (`autoCooldown` / `manualCooldown` > 0) → `ON_COOLDOWN`;
6. стоимость: авто — `autoEnergyCost`, ручная — `TemporalRules.manualCost`;
   `state.energy < cost` → `NO_ENERGY`;
7. целевой тик = `world.getTime() - secondsToTicks(seconds)`;
   `buffer.findAtOrBefore(target)` пусто → `NO_BUFFER`;
8. **безопасность позиции**: от найденного снимка шагать назад по буферу,
   пока не найдётся проходящий `isSafe(world, player, snapshot)`;
   не больше 40 шагов; не нашлось → `NO_SAFE_SNAPSHOT` (урон проходит);
9. восстановление (ниже);
10. `buffer.dropFrom(chosen.tick())` — снимок потрачен (ТЗ §15);
11. списать энергию, выставить кулдаун; для ручной —
    `debtSeconds = min(maxDebtSeconds, debtSeconds + seconds)`;
12. выдать защиту: по `cause`, если он есть, иначе по `OTHER`;
13. `SUCCESS`.

`isSafe`:

```java
Box box = player.getDimensions(EntityPose.STANDING)
        .getBoxAt(snap.x(), snap.y(), snap.z());
if (!world.isSpaceEmpty(player, box)) return false;
// не возвращать в лаву — иначе цикл (ТЗ §47)
if (world.getBlockState(BlockPos.ofFloored(snap.x(), snap.y(), snap.z()))
        .getFluidState().isIn(FluidTags.LAVA)) return false;
return true;
```

Восстановление (ТЗ §44; инвентарь и опыт не трогаем — §17, §18):

```java
p.networkHandler.requestTeleport(snap.x(), snap.y(), snap.z(), snap.yaw(), snap.pitch());
p.setVelocity(snap.velX(), snap.velY(), snap.velZ());
p.velocityModified = true;
p.setHealth(snap.health());
p.setAbsorptionAmount(snap.absorption());
p.getHungerManager().setFoodLevel(snap.foodLevel());
p.getHungerManager().setSaturationLevel(snap.saturation());
p.setAir(snap.air());
p.setFireTicks(snap.fireTicks());
p.setFrozenTicks(snap.frozenTicks());
p.fallDistance = 0.0f;                  // ТЗ §46: не воспроизводить падение
p.clearStatusEffects();
for (StatusEffectInstance e : snap.effects()) p.addStatusEffect(new StatusEffectInstance(e));
p.getInventory().selectedSlot = snap.selectedSlot();
```

`fallDistance = 0` — сознательное расхождение со снимком: вернуть
накопленное падение значит немедленно повторить тот же урон. Пометить
комментарием со ссылкой на ТЗ §46.

Для `FALL` дополнительно обнулять вертикальную скорость
(`setVelocity(vx, 0, vz)`), иначе игрок падает по той же траектории.

- [ ] **Шаг 5: тесты зелёные, сборка, коммит**

```bash
git add -A && git commit -m "feat(temporal): rewind executor with collision, lava and fall safety"
```

---

### Задача 9: Автоматическая отмотка на перехвате урона

**Файлы:**
- Create: `.../damage/DamageInterceptor.java`
- Modify: `.../RewindMod.java`

**Интерфейсы:**
- Consumes: `DamageClassifier`, `RewindExecutor`, `TemporalRules`, `TemporalProtection`, `ProgressionManager`, `ServerFeedback`
- Produces: `DamageInterceptor.register()`

- [ ] **Шаг 1: реализация через `ServerLivingEntityEvents.ALLOW_DAMAGE`**

Штатный хук Fabric API, отменяющий урон **до** применения (ТЗ §21).
Миксин не нужен.

```java
ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
    if (!(entity instanceof ServerPlayerEntity player)) return true;
    RewindConfig cfg = RewindConfig.get();
    if (!cfg.enabled) return true;

    TemporalState state = TemporalAttachments.of(player);
    DamageContext ctx = DamageClassifier.context(player, source, amount);

    // встреча засчитывается всегда, даже если отмотки не будет (ТЗ §37)
    ProgressionManager.recordEncounter(player, state, ctx);

    if (!state.unlocked) return true;

    // активная защита от того же источника — урон гасим, отмотку не тратим
    if (state.protection.covers(DamageClassifier.sourceKey(ctx), ctx.type())) return false;

    if (ctx.type().requiredLevel() > state.level) return true;
    if (!TemporalRules.isDangerous(cfg, player.getHealth(),
            player.getAbsorptionAmount(), player.getMaxHealth(), amount)) return true;

    if (RewindExecutor.rewind(player, cfg.autoRewindSeconds, ctx, false) != RewindResult.SUCCESS)
        return true;   // отмотка не вышла — урон проходит как обычно

    ProgressionManager.rewardRewind(player, state, ctx);
    ServerFeedback.rewind(player, ctx);
    return false;      // урон отменён
});
```

`ponytail: порог опасности считается по сырому урону до брони — игрок
иногда отматывается чуть раньше, чем нужно. Точный расчёт — через
DamageUtil.getDamageLeft, если понадобится тонкий баланс.`

- [ ] **Шаг 2: живая проверка на сервере**

- зомби бьёт игрока с 3 хп → отмотка, зомби остаётся на месте;
- два смертельных удара подряд → второй проходит (кулдаун);
- энергия < 20 → отмотки нет;
- падение с высоты на уровне 4 → одна отмотка, без цикла;
- лава на уровне 4 → одна отмотка, без цикла.

- [ ] **Шаг 3: коммит**

```bash
git add -A && git commit -m "feat(damage): automatic rewind on incoming lethal damage"
```

---

### Задача 10: Прогрессия и мастерство

**Файлы:**
- Create: `.../progression/ProgressionManager.java`
- Test: `src/test/java/.../progression/ProgressionManagerTest.java`

**Интерфейсы:**
- Produces:
  - `static double masteryGain(RewindConfig, TemporalState, TemporalDamageType, String key, boolean firstEncounter)` — чистая часть, тестируемая
  - `void recordEncounter(ServerPlayerEntity, TemporalState, DamageContext)`
  - `void rewardRewind(ServerPlayerEntity, TemporalState, DamageContext)`
  - `boolean tryLevelUp(ServerPlayerEntity, TemporalState)`
  - `void unlock(ServerPlayerEntity, TemporalState)` — первое получение
    способности: `unlocked = true`, уровень 1, полная энергия

- [ ] **Шаг 1: тест начисления с убыванием**

```java
@Test
void repeatedSourceGivesDiminishingMastery() {
    RewindConfig c = new RewindConfig();   // perRewind 20
    TemporalState s = new TemporalState();
    double[] got = new double[6];
    for (int i = 0; i < 6; i++)
        got[i] = ProgressionManager.masteryGain(c, s, TemporalDamageType.MELEE, "zombie", false);
    assertEquals(20.0, got[0]);
    assertEquals(20.0, got[1]);
    assertEquals(15.0, got[2]);
    assertEquals(10.0, got[3]);
    assertEquals(5.0,  got[4]);
    assertEquals(0.0,  got[5]);
}

@Test
void firstEncounterAddsBonus() {
    RewindConfig c = new RewindConfig();
    TemporalState s = new TemporalState();
    assertEquals(70.0,
        ProgressionManager.masteryGain(c, s, TemporalDamageType.EXPLOSION, "creeper", true));
}
```

- [ ] **Шаг 2: запустить, падает. Шаг 3: реализовать.**

`masteryGain` увеличивает счётчик в `state.recentSources` как побочный
эффект — это делает её нечистой, но позволяет тестировать без игры;
отметить в javadoc.

`tryLevelUp` вызывать после каждого начисления: считать
`TemporalRules.levelFor`; если больше текущего — поднять уровень,
сообщить через `ServerFeedback.levelUp` и полностью восстановить энергию.

- [ ] **Шаг 4: тесты зелёные, коммит**

```bash
git add -A && git commit -m "feat(progression): mastery with diminishing returns and level unlocking"
```

---

### Задача 11: Серверная обратная связь (без клиентского мода)

**Файлы:**
- Create: `.../feedback/ServerFeedback.java`
- Modify: `assets/rewind/lang/en_us.json`, `ru_ru.json`

Клиентской части в фазах 1–3 нет, но отмотку должно быть видно и слышно,
иначе её нельзя проверить в игре. Всё делается ванильными средствами
сервера и не требует мода на клиенте.

**Интерфейсы:**
- Produces: `rewind(ServerPlayerEntity, DamageContext)`,
  `levelUp(ServerPlayerEntity, int level)`, `unlocked(ServerPlayerEntity)`,
  `denied(ServerPlayerEntity, RewindResult)`

- [ ] **Шаг 1: реализация**

```java
public static void rewind(ServerPlayerEntity p, DamageContext ctx) {
    RewindConfig cfg = RewindConfig.get();
    if (!cfg.serverFeedbackEnabled) return;
    ServerWorld w = p.getServerWorld();
    if (cfg.soundEnabled)
        w.playSound(null, p.getX(), p.getY(), p.getZ(),
            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.6f);
    // «след» на старой позиции — частицы, а не сущность (RULE 5)
    w.spawnParticles(ParticleTypes.SOUL, ctx.position().x, ctx.position().y + 1.0,
        ctx.position().z, 12, 0.25, 0.5, 0.25, 0.01);
    p.sendMessage(Text.translatable("rewind.feedback.rewound"), true);
}
```

Звук — временная затычка из ванили; свои `rewind_trigger` /
`rewind_complete` придут в фазе 6. Пометить `ponytail:`-комментарием.

Ключи локализации: `rewind.feedback.rewound`, `rewind.feedback.level_up`,
`rewind.feedback.unlocked`, `rewind.feedback.no_energy`,
`rewind.feedback.cooldown`, `rewind.feedback.no_snapshot`.

- [ ] **Шаг 2: коммит**

```bash
git add -A && git commit -m "feat(feedback): server-side sound, particles and action bar"
```

---

### Задача 12: Dev-команды

**Файлы:**
- Create: `.../command/RewindCommand.java`
- Modify: `.../RewindMod.java`

- [ ] **Шаг 1: реализация**

`CommandRegistrationCallback.EVENT`, корень `/rewind`. Права: всё
поддерево — `requires(src -> src.hasPermissionLevel(2))`, ветки `force`
и `debug` — уровень 3 (ТЗ §48).

```text
/rewind level <player> <0..5>
/rewind energy <player> <amount>
/rewind mastery <player> <amount>
/rewind unlock <player>
/rewind force <player> <seconds>
/rewind clear <player>
/rewind debug <player>
```

`debug` печатает: `unlocked`, `level`, `mastery`, `energy/max`, `debt`,
кулдауны, размер буфера и диапазон его тиков, активные защиты, список
встреченных типов.

`force` вызывает `RewindExecutor.rewind(player, seconds, null, true)`
в обход проверки уровня, но **не** в обход проверок безопасности позиции —
чтобы команда не телепортировала в блок.

- [ ] **Шаг 2: живая проверка**

`/rewind unlock @s`, `/rewind level @s 4`, `/rewind debug @s`, дать зомби
себя ударить → отмотка. `/rewind clear @s` → буфер пуст, отмотки нет.

- [ ] **Шаг 3: коммит**

```bash
git add -A && git commit -m "feat(command): /rewind dev commands"
```

---

### Задача 13: Защита от эксплойтов и проверочный прогон

**Файлы:**
- Create: `docs/superpowers/notes/2026-09-14-exploit-checklist.md`

Инвентарь и опыт в снимке не хранятся вообще — дюп конструктивно
невозможен, отдельный `InventoryLedger` (ТЗ §19) не нужен, пока снимок
не трогает предметы. Зафиксировать это как сознательное решение.

- [ ] **Шаг 1: прогнать чек-лист вживую и записать результат**

```text
[ ] предмет получен → отмотка → дюпа нет
[ ] предмет потрачен → отмотка → не вернулся
[ ] опыт получен → отмотка → остался
[ ] прочность потрачена → отмотка → не восстановилась
[ ] сундук опустошён → отмотка → сундук остался пустым
[ ] смерть → отмотки после смерти нет
[ ] Нижний мир → Верхний мир → старый буфер недоступен
[ ] стрела попала → игрок отмотался → стрела не вернулась
[ ] зомби ударил → игрок отмотался → зомби на месте и в том же состоянии
[ ] выход/вход → буфер пуст
[ ] перезапуск сервера → уровень и мастерство на месте, буфер пуст
[ ] второй игрок рядом не откатывается ничем
```

- [ ] **Шаг 2: коммит результатов**

```bash
git add -A && git commit -m "docs: exploit checklist results for phases 1-3"
```

---

### Задача 14: Документация

**Файлы:**
- Create: `README.md`, `ARCHITECTURE.md`, `CONFIG.md`
- Modify: `IMPLEMENTATION_PLAN.md`

- [ ] **Шаг 1: README** — концепция, как получить способность (пока
  `/rewind unlock`, Temporal Rift в фазе 7), уровни, авто-отмотка, энергия,
  долг, мультиплеер, ограничения, явный список того, что **не** откатывается.

- [ ] **Шаг 2: ARCHITECTURE** — снимок, буфер, перехват урона,
  персистентность, разделение сервер/клиент, почему миксинов нет.

- [ ] **Шаг 3: CONFIG** — таблица всех полей `rewind.json`: имя, дефолт,
  что делает, на что влияет.

- [ ] **Шаг 4: IMPLEMENTATION_PLAN.md** — отметить фазы 1–3 сделанными,
  перечислить оставшееся на фазы 4–8.

- [ ] **Шаг 5: коммит и пуш**

```bash
git add -A && git commit -m "docs: README, ARCHITECTURE, CONFIG"
git push -u origin main
```

---

## Самопроверка плана против ТЗ

| Раздел ТЗ | Где закрыт |
|---|---|
| §3 прогрессия 1–5 | Задачи 4, 5, 10 |
| §4 поток авто-отмотки | Задача 9 |
| §5 порог опасности | Задача 4 (`isDangerous`, три режима) |
| §6–§9 энергия, долг | Задачи 2, 4, 7 |
| §10 защита | Задача 8 (`TemporalProtection`) |
| §11–§15 буфер и снимки | Задачи 3, 7, 8 (`dropFrom`) |
| §16–§19 инвентарь, опыт | Задача 13 (конструктивно: их нет в снимке) |
| §20–§21 классификация и перехват | Задачи 5, 9 |
| §22 смерть | Задача 8, проверка `DEAD` |
| §30–§34 мультиплеер | Конструктивно: состояние по игроку, мир не трогается; проверка в задаче 13 |
| §35–§37 мастерство и уровни | Задачи 4, 10 |
| §40–§41 персистентность | Задача 6 (состояние сохраняется, буфер — нет) |
| §44–§47 телепорт, коллизии, падение, лава | Задача 8 |
| §48 команды | Задача 12 |
| §49 конфиг | Задача 2 |
| §54–§55 тесты и эксплойты | Задачи 3, 4, 5, 6, 8, 10, 13 |
| §62 документация | Задача 14 |

**Отложено до фаз 4–8 (сознательно, вне объёма этой итерации):**
ручная отмотка по клавише и её UI (§23–§25), HUD (§26), визуальный эффект
и афтеримидж (§27–§28), свои звуки (§29), сетевой слой (§42–§43),
Temporal Rift и Relic (§38), Temporal Scar (§57).
`RewindExecutor` уже принимает `manual = true`, поэтому фаза 4 добавляет
только пакет и обработку клавиши.
