# МОД «ОТМОТКА» (REWIND)
## Техническое задание для Claude Code

---

# 0. РОЛЬ И ЗАДАЧА

Ты реализуешь Minecraft Fabric-мод **«Отмотка» (Rewind)**.

Это не обычная система сохранения/загрузки и не rollback мира.

Основная механика:

> Игрок обладает личным временем. Он способен вернуть собственное состояние в прошлое, не возвращая прошлое миру.

Мод должен ощущаться как полноценная игровая механика с прогрессией, ресурсом, риском и визуально выразительной отмоткой.

## КРИТИЧЕСКОЕ ПРАВИЛО

**Игрок откатывается. Мир — никогда.**

Нельзя реализовывать систему через откат Minecraft world state.

Нельзя восстанавливать:
- блоки;
- контейнеры;
- мобов;
- projectile entities;
- TNT;
- редстоун;
- предметы в мире;
- действия других игроков;
- время мира;
- погоду;
- генерацию мира.

Отматывается только допустимое состояние конкретного игрока.

---

# 1. ПЕРЕД НАЧАЛОМ РАБОТЫ

Сначала проанализируй существующий проект.

Не создавай архитектуру вслепую.

Проверь:

- Minecraft version;
- Fabric Loader;
- Fabric API;
- mappings;
- Java version;
- Gradle;
- существующую структуру source sets;
- client/common разделение;
- mixin configuration;
- entrypoints;
- существующие зависимости.

Используй **актуальные API именно той версии Minecraft/Fabric, которая указана в проекте**.

Не используй устаревшие Fabric API подходы только потому, что они встречаются в старых примерах.

Если API изменился — адаптируй реализацию под текущую версию.

После анализа проекта создай краткий `IMPLEMENTATION_PLAN.md` и только после этого начинай реализацию.

---

# 2. ИГРОВАЯ ФИЛОСОФИЯ

Игрок проходит путь:

```text
Инстинкт
   ↓
Обучение
   ↓
Рефлекс
   ↓
Контроль
   ↓
Осознанное управление временем
```

На первых уровнях игрок не контролирует способность.

Она спасает его автоматически.

На максимальном уровне игрок сам решает:

> «Мне нужно вернуться на 5 секунд назад».

Это должно ощущаться как переход от сверхъестественного рефлекса к освоенной способности.

---

# 3. ПРОГРЕССИЯ

Всего 5 уровней.

## LEVEL 1 — MELEE

Автоматическая отмотка работает против ближнего физического урона.

Примеры:

- Zombie;
- Husk;
- Skeleton melee;
- Spider;
- Piglin;
- другие melee attacks.

Не считать любой урон автоматически melee.

Использовать систему классификации DamageContext.

---

## LEVEL 2 — PROJECTILES

Добавляются:

- arrows;
- tridents;
- fireballs;
- модовые projectile damage sources, если они корректно классифицируются.

---

## LEVEL 3 — EXPLOSIONS

Добавляются:

- Creeper;
- TNT;
- TNT minecart;
- bed explosions;
- respawn anchor explosions;
- другие explosion damage sources.

---

## LEVEL 4 — ENVIRONMENT

Добавляются:

- fire;
- lava;
- fall;
- drowning;
- cactus;
- poison;
- magic;
- wither;
- freezing;
- suffocation;
- sweet berry bush;
- другие environmental damage types.

Однако разные environmental damage types могут иметь разные правила защиты от циклов.

---

## LEVEL 5 — CONTROL

Открывается:

- manual rewind;
- увеличенный temporal buffer;
- более низкий cooldown;
- отсутствие обязательного lethal/danger threshold;
- выбор глубины отмотки;
- полноценное сознательное использование способности.

Это должно быть качественным изменением механики, а не просто «больше чисел».

---

# 4. АВТОМАТИЧЕСКАЯ ОТМОТКА

Automatic Rewind — серверная механика.

При получении подходящего урона:

```text
Damage
 ↓
DamageContext
 ↓
TemporalDamageType
 ↓
Player Temporal Level
 ↓
Danger Threshold
 ↓
Energy Check
 ↓
Cooldown Check
 ↓
Find Snapshot
 ↓
Cancel Damage
 ↓
Restore Player
 ↓
Temporal Protection
```

---

# 5. DANGER THRESHOLD

На уровнях 1–4 автоматическая способность не должна реагировать на каждый маленький удар.

Базовое правило:

```text
projectedHealth <= 20% maxHealth
```

ИЛИ configurable damage threshold.

Конфигурация должна позволять выбирать:

- процент оставшегося здоровья;
- минимальный процент max HP потерянного за удар;
- lethal-only режим.

На Level 5 threshold отключается для manual rewind.

---

# 6. AUTO REWIND PARAMETERS

Значения должны быть configurable.

Базовые значения:

```text
auto rewind duration: 0.6 sec
auto cooldown: 5 sec
auto energy cost: 20
temporal protection: 0.8 sec
```

Не хардкодить балансные значения в нескольких местах.

Все параметры вынести в Config.

---

# 7. TEMPORAL ENERGY

Игрок имеет ресурс:

```text
Temporal Energy
```

Базово:

```text
max = 100
```

Auto Rewind:

```text
cost = 20
```

Manual Rewind:

```text
base cost = 40
cost per second = 8
```

Формула:

```text
manualCost = baseCost + rewindSeconds * costPerSecond
```

Все значения configurable.

---

# 8. ВОССТАНОВЛЕНИЕ ЭНЕРГИИ

Энергия постепенно восстанавливается.

Однако после manual rewind возникает:

```text
Temporal Debt
```

Temporal Debt временно замедляет regeneration.

Например:

```text
normal regeneration = 1 unit/sec
```

При наличии debt:

```text
regenMultiplier = configurable
```

Не допускать бесконечной цепочки:

```text
rewind → regenerate → rewind → regenerate
```

---

# 9. TEMPORAL DEBT

Manual rewind создаёт:

```text
Temporal Debt += rewindSeconds
```

Debt постепенно уменьшается.

Пока debt > 0:

- regeneration slower;
- при необходимости можно увеличить стоимость следующего manual rewind;
- визуально можно показывать небольшое состояние нестабильности.

Debt не должен превращаться в раздражающий permanent debuff.

Это балансирующий механизм.

---

# 10. TEMPORAL PROTECTION

После rewind игрок получает краткую temporal protection.

Базово:

```text
0.8 sec
```

Она нужна для предотвращения:

```text
lava
→ rewind
→ lava damage
→ rewind
→ lava damage
→ infinite loop
```

Protection должна учитывать источник/тип опасности.

Предпочтительно:

```text
source-aware protection
```

а не просто бессмертие от всего.

Например, если игрок был отмотан от атаки Zombie:

```text
Zombie attack
→ rewind
→ short protection against same attack/source
```

Игрок всё ещё может получить другую угрозу.

---

# 11. SNAPSHOT SYSTEM

Создать серверный кольцевой буфер:

```text
TemporalBuffer
```

Он принадлежит конкретному игроку.

Пример:

```text
10 seconds
20 snapshots/sec
= 200 snapshots
```

Частота и длительность configurable.

---

# 12. TemporalSnapshot

Создать immutable snapshot structure.

Минимально хранить:

```text
tick

position
velocity

yaw
pitch

health

food level
saturation
exhaustion

air

fire ticks

active effects

selected hotbar slot
```

Можно добавлять дополнительные player-only состояния, если они необходимы для корректной работы.

Не сохранять полный Player NBT каждый tick без необходимости.

Не сериализовать весь Player объект.

---

# 13. SNAPSHOT BUFFER

Ring buffer должен:

- иметь фиксированный размер;
- автоматически удалять старые записи;
- быть привязан к серверному player;
- не расти бесконечно;
- очищаться при недопустимых переходах состояния.

Например:

```text
[198]
[199]
[000]
[001]
[002]
...
```

---

# 14. SNAPSHOT VALIDITY

Snapshot должен считаться недействительным после:

- dimension change;
- death;
- respawn;
- invalid teleport;
- server-side forced teleport;
- перехода в spectator, если это необходимо;
- других событий, после которых восстановление позиции опасно.

При смене dimension:

```text
buffer.clear()
```

Нельзя получить:

```text
Nether → rewind → Overworld
```

---

# 15. SNAPSHOT CONSUMPTION

Использованный snapshot нельзя бесконечно использовать повторно.

Каждый rewind должен иметь валидную временную точку.

Не допускать:

```text
rewind to T-5
rewind to T-5
rewind to T-5
rewind to T-5
```

одним и тем же snapshot.

После manual rewind соответствующий участок buffer должен быть корректно обработан.

---

# 16. ПОСЛЕ ОТМОТКИ

Очень важное правило:

### Snapshot не откатывает мир.

Например:

```text
Player
10 diamonds

↓
gets 20 diamonds
↓
drops them
↓
manual rewind
```

Результат не должен возвращать экономическое состояние игрока назад.

---

# 17. INVENTORY

Полный inventory rollback запрещён.

Не восстанавливать:

- потраченные предметы;
- полученные предметы;
- выброшенные предметы;
- durability;
- XP;
- advancements;
- achievements;
- persistent world progression.

То есть:

```text
съел steak
→ rewind
```

Голод может вернуться.

Steak — нет.

---

# 18. XP

XP никогда не откатывается.

Если игрок получил XP после временной точки:

```text
rewind
```

XP остаётся.

Если игрок потратил XP:

```text
rewind
```

XP не возвращается.

---

# 19. INVENTORY LEDGER

Если для реализации manual rewind требуется отслеживать экономические изменения игрока, создать отдельный:

```text
InventoryLedger
```

Но не пытаться восстанавливать inventory через snapshot.

Ledger используется только для предотвращения дюпов/неконсистентности.

Приоритет:

```text
No duplication > perfect temporal consistency
```

---

# 20. DAMAGE CLASSIFICATION

Создать:

```text
TemporalDamageType
```

Минимально:

```text
MELEE
PROJECTILE
EXPLOSION
FIRE
LAVA
FALL
DROWNING
POISON
MAGIC
FREEZING
SUFFOCATION
CACTUS
ENVIRONMENTAL
OTHER
```

Создать:

```text
DamageContext
```

с информацией:

```text
damage source
attacker
direct entity
source entity
damage type
position
amount
tick
```

---

# 21. DAMAGE INTERCEPTION

Перехватить урон до фактического применения.

Не делать:

```text
damage applied
↓
health restored
```

если это можно избежать.

Предпочтительно:

```text
incoming damage
↓
classify
↓
check rewind
↓
cancel original damage
↓
restore snapshot
```

Это предотвращает лишние побочные эффекты смерти/урона.

---

# 22. DEATH HANDLING

Если игрок уже умер:

```text
automatic rewind
```

не должен использоваться как обычный способ resurrection.

Если смертельный урон был перехвачен заранее — игрок остаётся жив.

Если Minecraft уже перевёл игрока в death state — обычный rewind запрещён.

---

# 23. MANUAL REWIND

Доступен только Level 5.

Клиент получает keybind.

Например:

```text
R
```

Но keybind должен быть configurable.

Клиент отправляет сервер:

```text
C2S_REWIND_REQUEST
```

с желаемой глубиной.

Например:

```text
5 seconds
```

---

# 24. НИКОГДА НЕ ДОВЕРЯТЬ КЛИЕНТУ

Клиент может отправить:

```text
rewind = 999999 seconds
```

Сервер обязан самостоятельно проверить:

- level;
- buffer;
- energy;
- cooldown;
- maximum duration;
- player state;
- dimension;
- permissions;
- current temporal protection;
- valid snapshot.

Клиент не определяет результат rewind.

---

# 25. MANUAL REWIND UI

На Level 5 при удержании/активации кнопки показать temporal timeline.

Пример:

```text
NOW
│
├── 1s
├── 2s
├── 3s
├── 4s
├── 5s
├── 6s
└── 7s
```

UI не должен выглядеть футуристично.

Не использовать:

- sci-fi panels;
- holographic interfaces;
- neon;
- computer HUD aesthetics.

Визуальный язык:

**время / старый дневник / рукопись / след / разрыв / циферблат.**

---

# 26. HUD

HUD должен показывать:

```text
Temporal Energy
Current Level
Cooldown
```

На Level 5:

```text
available rewind duration
```

HUD должен быть минималистичным.

Не занимать большую часть экрана.

Не использовать стандартный sci-fi progress bar.

Предпочтительно:

- тонкая temporal mark;
- маленький символ;
- круговая/дуговая шкала;
- визуальный след;
- restrained typography.

---

# 27. VISUAL REWIND EFFECT

При rewind обязательно показать событие.

Порядок:

```text
damage / input
↓
short screen desaturation
↓
camera snap backwards
↓
temporal afterimage
↓
particles move backward
↓
player restored
↓
sound
```

Общая длительность:

```text
~250–400 ms
```

Эффект не должен блокировать управление на длительное время.

---

# 28. TEMPORAL AFTERIMAGE

Создать client-side visual afterimage.

На старой позиции игрока на короткое время появляется силуэт.

Afterimage:

- полупрозрачный;
- быстро исчезает;
- может слегка распадаться;
- не является настоящей entity;
- не взаимодействует с миром.

Не создавать server entity только ради визуального эффекта.

---

# 29. SOUND DESIGN

Добавить отдельные sound events:

```text
rewind_trigger
rewind_complete
temporal_instability
```

Основной rewind sound:

- короткий;
- ощущение движения назад;
- не использовать длинную sci-fi заряжающую последовательность.

---

# 30. MULTIPLAYER

Вся логика authoritative server-side.

Каждый игрок имеет собственное:

```text
TemporalManager
TemporalBuffer
TemporalEnergy
Progression
Cooldown
Debt
```

Игрок A может отмотаться.

Игрок B не отматывается.

---

# 31. ДРУГИЕ ИГРОКИ

Если A отматывается:

B не откатывается.

Действия B не изменяются.

B видит визуальное событие:

```text
A
↓
temporal effect
↓
A appears at previous position
```

---

# 32. PROJECTILES

Projectile не откатывается.

Пример:

```text
Skeleton fires arrow
→ arrow hits A
→ A rewinds
```

Arrow не должен возвращаться в состояние до выстрела.

Skeleton также не откатывается.

---

# 33. MOB STATE

Мобы не откатываются.

Если Zombie ударил игрока:

```text
Zombie remains in current state.
```

Это важно для gameplay.

После rewind игрок получает второй шанс, но не отменяет существующую угрозу.

---

# 34. PVP

PvP должно поддерживаться.

Игрок B наносит A damage.

A может rewind.

Но:

- B не откатывается;
- B не возвращает стрелу;
- B не возвращает расходуемый ресурс;
- мир не откатывается.

Rewind — защитная способность A, а не undo для PvP.

---

# 35. PROGRESSION

Создать:

```text
TemporalProgression
```

Хранить:

```text
level
mastery
encounters
unlockedDamageTypes
```

---

# 36. MASTERY

Не использовать простой бесконечный farm.

Mastery даётся за:

- успешные automatic rewinds;
- первые встречи с новыми типами угроз;
- успешные редкие спасения;
- разнообразные ситуации.

Одинаковый источник должен давать diminishing returns.

Пример:

```text
1st = 100%
2nd = 100%
3rd = 75%
4th = 50%
5th = 25%
6th+ = 0%
```

Периодически multiplier восстанавливается.

Все значения configurable.

---

# 37. LEVEL UNLOCKING

Базовый вариант:

```text
Level 1
→ initial ability

Level 2
→ mastery + encounter PROJECTILE

Level 3
→ mastery + encounter EXPLOSION

Level 4
→ mastery + encounter ENVIRONMENT

Level 5
→ mastery + sufficient progression
```

Важно:

игрок должен сначала столкнуться с неизвестной угрозой, прежде чем получить полноценную защиту от неё.

Но система не должна требовать смерти.

Encounter означает:

> игрок столкнулся с типом угрозы.

Не обязательно был убит.

---

# 38. INITIAL ACQUISITION

Способность не должна просто появляться при создании мира.

Создать gameplay event:

```text
Temporal Rift
```

Редкая структура/аномалия.

Игрок находит:

```text
Temporal Relic
```

Взаимодействует с ним.

Происходит первая uncontrolled rewind.

После этого:

```text
abilityUnlocked = true
level = 1
```

---

# 39. ABILITY LOSS

Не делать permanent loss progression.

Игрок не должен терять весь прогресс из-за обычной смерти.

Можно временно вводить:

```text
Temporal Fracture
```

которая:

- обнуляет/сильно уменьшает energy;
- временно блокирует auto rewind;
- вызывает визуальную нестабильность.

Но:

```text
level
mastery
unlocked abilities
```

сохраняются.

---

# 40. PERSISTENT DATA

Состояние игрока должно сохраняться между:

- logout;
- login;
- server restart.

Сохранять:

```text
abilityUnlocked
level
mastery
energy
temporalDebt
cooldowns, если это необходимо
fracture state
```

Не сохранять ring buffer.

После login:

```text
buffer = empty
```

---

# 41. RING BUFFER НЕ СОХРАНЯТЬ

После:

- logout;
- server restart;
- dimension change;
- death;

старые snapshots должны быть недействительны.

Это предотвращает множество exploit-сценариев.

---

# 42. NETWORKING

Создать отдельный network layer.

Минимальные packets:

```text
C2S_REWIND_REQUEST

S2C_TEMPORAL_STATE

S2C_REWIND_EFFECT

S2C_PROGRESS_UPDATE
```

`S2C_REWIND_EFFECT` используется только для визуализации.

Сам rewind выполняется сервером.

---

# 43. CLIENT-SERVER FLOW

### Automatic

```text
SERVER
damage detected
↓
server validates
↓
server restores player
↓
server sends S2C_REWIND_EFFECT
↓
CLIENT
visual rewind
```

### Manual

```text
CLIENT
key pressed
↓
C2S_REWIND_REQUEST
↓
SERVER
validate
↓
find snapshot
↓
energy
↓
restore
↓
send confirmation
↓
CLIENT visual effect
```

---

# 44. TELEPORTATION

После restore сервер должен корректно установить:

- position;
- rotation;
- velocity.

При необходимости использовать корректный server-side teleport API текущей версии.

Не полагаться на client-only position changes.

После teleport:

- синхронизировать entity position;
- предотвратить desync;
- корректно обновить client movement state.

---

# 45. COLLISION SAFETY

Snapshot может содержать позицию, которая теперь занята блоком.

Перед restore проверить:

```text
isValidPlayerPosition()
```

Если позиция стала недоступна:

1. попытаться найти ближайшую безопасную позицию;
2. если не удалось — использовать ближайший валидный snapshot;
3. если безопасного snapshot нет — отменить rewind.

Никогда не телепортировать игрока внутрь блока.

---

# 46. FALL SAFETY

Особенно тщательно обработать:

```text
fall
```

Игрок не должен:

```text
fall
→ rewind
→ same falling trajectory
→ fall
→ rewind
→ infinite loop
```

После fall rewind:

- временно отменить повторный fall-trigger;
- дать temporal protection;
- корректно обработать velocity.

---

# 47. LAVA / FIRE

Для lava/fire:

```text
rewind
↓
restore position
↓
temporal protection
```

Если snapshot всё ещё находится внутри lava/fire:

не выполнять бесконечный автоматический цикл.

Нужно либо:

- выбрать более ранний безопасный snapshot;
- либо применить специальное post-rewind protection;
- либо отменить конкретный повторный trigger.

---

# 48. DEBUG COMMANDS

Добавить dev commands:

```text
/rewind level <player> <level>

/rewind energy <player> <amount>

/rewind mastery <player> <amount>

/rewind force <player> <seconds>

/rewind clear <player>

/rewind debug <player>
```

`force` и debug-команды должны быть restricted.

---

# 49. CONFIG

Создать конфигурацию.

Минимальные параметры:

```text
enabled

maxLevel

bufferSeconds
snapshotRate

maxEnergy
energyRegen

autoEnergyCost
manualBaseCost
manualCostPerSecond

autoRewindSeconds
manualMaxSeconds

autoCooldown
manualCooldown

dangerThreshold

temporalProtectionDuration

masteryValues

diminishingReturns

dimensionChangeClearsBuffer
deathClearsBuffer

hudEnabled
visualEffectsEnabled
soundEnabled
```

Не дублировать числа по коду.

---

# 50. ARCHITECTURE

Предпочтительная структура:

```text
com.<modid>.rewind
│
├── RewindMod
│
├── temporal
│   ├── TemporalManager
│   ├── TemporalBuffer
│   ├── TemporalSnapshot
│   ├── TemporalEnergy
│   ├── TemporalDebt
│   ├── TemporalProtection
│   └── RewindExecutor
│
├── damage
│   ├── TemporalDamageType
│   ├── DamageContext
│   └── DamageClassifier
│
├── progression
│   ├── TemporalProgression
│   ├── MasteryManager
│   └── ProgressionState
│
├── persistence
│   └── TemporalPlayerData
│
├── networking
│   ├── RewindRequestPacket
│   ├── TemporalStatePacket
│   ├── RewindEffectPacket
│   └── ProgressPacket
│
├── client
│   ├── RewindClient
│   ├── TemporalHud
│   ├── RewindEffects
│   ├── AfterimageRenderer
│   └── Keybinds
│
├── world
│   └── TemporalRift
│
└── config
    └── RewindConfig
```

Названия можно адаптировать под idiomatic architecture текущего Fabric API.

Не создавать бессмысленные классы только ради структуры.

---

# 51. MIXINS / HOOKS

Использовать Mixins только там, где нет нормального Fabric API hook.

Перед созданием Mixins:

1. проверить Fabric API;
2. проверить vanilla events;
3. проверить current mappings;
4. использовать минимально необходимый injection point.

Не делать aggressive overwrite.

Предпочитать:

- inject;
- cancellable injection;
- redirect только при необходимости.

Все Mixins документировать.

---

# 52. THREADING

Temporal state должен изменяться на server thread.

Не выполнять rewind state mutations из client thread.

Network packets должны schedule-ить работу на server thread.

---

# 53. PERFORMANCE

Основная цель:

```text
20 TPS
```

без заметного overhead.

Не:

- создавать огромное количество объектов;
- сериализовать NBT каждый tick;
- запускать expensive collision scan каждый tick;
- хранить бесконечный history;
- создавать real entities для afterimage.

Ring buffer должен иметь фиксированный memory footprint.

---

# 54. TESTING

Создать тесты для критических случаев.

Обязательно проверить:

### Basic

```text
damage
→ auto rewind
```

### Lethal

```text
lethal damage
→ player survives
```

### Cooldown

```text
two lethal attacks quickly
→ only first rewind
```

### Energy

```text
energy < cost
→ no rewind
```

### Lava

```text
lava
→ rewind
→ no infinite loop
```

### Fall

```text
fall
→ rewind
→ no infinite loop
```

### Dimension

```text
Nether
→ Overworld
→ old buffer unavailable
```

### Death

```text
actual death
→ no post-death rewind
```

### Multiplayer

```text
A rewinds
→ B remains unchanged
```

### Projectile

```text
arrow hits A
→ A rewinds
→ arrow does not rewind
```

### Inventory

```text
get item
→ rewind
→ no duplication
```

### XP

```text
gain XP
→ rewind
→ XP remains
```

### Logout

```text
logout
→ login
→ old buffer unavailable
```

---

# 55. EXPLOIT TESTING

Отдельно протестировать:

```text
item duplication
XP duplication
enchantment duplication
durability restoration
container rollback
death rollback
dimension rollback
projectile rollback
mob rollback
disconnect rollback
server restart rollback
```

Ни один из них не должен быть возможен.

---

# 56. VISUAL DESIGN REQUIREMENTS

Визуальный стиль:

**dark / mystical / temporal / book-like / physical time.**

Не:

- futuristic;
- cyberpunk;
- holographic;
- sci-fi computer interface;
- neon;
- generic RPG MMO HUD.

Визуальные ассоциации:

- старые часы;
- чернила;
- выцветшая бумага;
- temporal scars;
- ghost images;
- torn frames;
- reversed particles;
- fading silhouettes.

---

# 57. TEMPORAL SCAR

После значительных rewinds можно добавлять временный:

```text
Temporal Scar
```

Это не обычный debuff.

Он используется преимущественно как feedback/balance.

При высоком количестве:

- визуальная нестабильность;
- ghost images;
- лёгкое искажение звука;
- более медленная energy regeneration.

Scar должен постепенно исчезать.

Не превращать механику в постоянное наказание.

---

# 58. GAMEPLAY QUALITY

Нельзя допускать ощущения:

> «Я получил бесплатный save state».

Правильное ощущение:

> «Я избежал смерти, но проблема всё ещё существует».

Пример:

```text
Zombie hits player
↓
REWIND
↓
player returns 0.6 sec
↓
Zombie still exists
↓
player must dodge/fight/run
```

Это фундаментальный принцип.

---

# 59. ACCEPTANCE CRITERIA

Работа считается выполненной, когда:

1. Игрок может получить способность.
2. Level 1 automatic rewind работает.
3. Progression корректно разблокирует следующие уровни.
4. Damage types корректно классифицируются.
5. Energy работает.
6. Cooldowns работают.
7. Ring buffer работает.
8. Manual rewind работает на Level 5.
9. Inventory/XP duplication невозможны.
10. World state никогда не откатывается.
11. Multiplayer не ломается.
12. Client не может самостоятельно инициировать недопустимый rewind.
13. Dimension/death/logout корректно очищают buffer.
14. HUD отображает состояние.
15. Rewind имеет выраженный visual/audio feedback.
16. Конфигурация работает.
17. Dev commands работают.
18. Критические edge cases протестированы.

---

# 60. ПОРЯДОК РЕАЛИЗАЦИИ

Не пытайся сделать всё одновременно.

Реализуй по фазам.

## PHASE 1 — CORE

## PHASE 2 — DAMAGE


## PHASE 3 — PROGRESSION


## PHASE 4 — MANUAL REWIND


## PHASE 5 — MULTIPLAYER


## PHASE 6 — CLIENT

- HUD
- timeline
- afterimage
- camera effect
- desaturation
- sounds

## PHASE 7 — WORLD

- Temporal Rift
- Temporal Relic
- acquisition event

## PHASE 8 — POLISH

- config
- debug tools
- tests
- exploit testing
- performance profiling
- documentation

---

# 61. IMPORTANT IMPLEMENTATION RULES

### RULE 1

Не откатывать мир.

### RULE 2

Не доверять клиенту.

### RULE 3

Не использовать Player NBT snapshot каждый tick без необходимости.

### RULE 4

Не откатывать inventory, XP или durability.

### RULE 5

Не создавать real entities для визуального afterimage.

### RULE 6

Не делать Mixins там, где есть нормальный Fabric API hook.

### RULE 7

Не использовать устаревшие API.

### RULE 8

Не хардкодить балансные значения.

### RULE 9

Не делать permanent death-loss progression.

### RULE 10

Не превращать HUD в futuristic UI.

---

# 62. ДОКУМЕНТАЦИЯ

После реализации создать:

```text
README.md
IMPLEMENTATION_PLAN.md
ARCHITECTURE.md
CONFIG.md
```

README должен объяснять:

- концепцию;
- получение способности;
- уровни;
- automatic rewind;
- manual rewind;
- energy;
- temporal debt;
- multiplayer;
- ограничения.

ARCHITECTURE должен объяснять:

- snapshot system;
- buffer;
- damage interception;
- networking;
- persistence;
- client/server responsibilities.

---

# 63. FINAL PRINCIPLE

Главное ощущение мода:

> **Ты не отменяешь произошедшее. Ты переживаешь его ещё раз — и получаешь шанс поступить иначе.**

Отмотка не должна уничтожать последствия мира.

Она должна возвращать **тебя**.

Мир продолжает существовать.

Мобы продолжают двигаться.

Другие игроки продолжают жить.

Выброшенный предмет остаётся выброшенным.

Потраченный ресурс остаётся потраченным.

Но ты снова оказываешься там, где был несколько секунд назад.

И теперь знаешь, что произойдёт.