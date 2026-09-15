# Передача: фаза 6 (клиент и вид)

Заметка для следующей сессии. Фазы 1–3 закончены и лежат в `origin/main`,
сборка зелёная, 33 юнит-теста проходят. Фаза 6 не начата — есть только
разведка API, она ниже.

## Что решил владелец

Следующая работа — **фаза 6**: HUD, афтеримидж, рывок камеры, обесцвечивание
кадра, свои звуки. Плюс он утвердил **все четыре** идеи сверх ТЗ:

1. **Эхо отмотки для соседей.** Сейчас чужая отмотка выглядит как
   телепорт-чит. Соседний игрок должен видеть силуэт, уходящий назад
   по траектории, — это честно читается в PvP.
2. **Журнал времени вместо простого HUD.** Предмет-тетрадь: список
   встреченных угроз и спасений, заполняется сам. Прогрессия становится
   видимой без цифр.
3. **Звук близкой отмотки.** Когда энергии хватает, а здоровья мало —
   едва слышный тик. Игрок чувствует страховку до удара, а не узнаёт
   о ней постфактум.
4. **След на месте смерти.** Если отмотка не сработала из-за кулдауна или
   энергии — сказать об этом в сообщении о смерти, иначе игрок решит, что
   мод сломался.

Отдельное требование владельца: **всё публиковать на GitHub**, не только
коммитить локально. Работа считается сделанной, когда она в `origin/main`.

И ещё одно, важное: **подагентов в этом проекте не использовать** — прямой
запрет, «они создают много ошибок».

## Разведка API: всё сверено по настоящим джарникам

Проверялось через `javap` по ремапнутому джарнику
`/d/dev-cache/gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2/…jar`
и по джарникам Fabric API в `/d/dev-cache/gradle/caches/modules-2/`.
Это сильно дешевле, чем ловить ошибки компиляции.

### Есть и работает

- `HudRenderCallback.EVENT` → `onHudRender(DrawContext, RenderTickCounter)`
  (`fabric-rendering-v1` 5.2.1).
- `WorldRenderEvents.AFTER_ENTITIES`, `.LAST`, `.END`;
  `WorldRenderContext` даёт `matrixStack()`, `consumers()`, `camera()`,
  `world()`, `tickCounter()`.
- `EntityRenderDispatcher.render(E entity, double x, double y, double z,
  float yaw, float tickDelta, MatrixStack, VertexConsumerProvider, int light)`
  и `getLight(E, float)` — этим и рисуется афтеримидж, без создания сущности.
- `VertexConsumer` в 1.21.1: `vertex(float,float,float)`,
  `color(int,int,int,int)`, `texture`, `overlay`, `light`, `normal`.
  Полупрозрачность силуэта делается обёрткой `VertexConsumerProvider`,
  которая перехватывает `color()` и домножает альфу.
- `PayloadTypeRegistry.playS2C()` / `playC2S()`;
  `CustomPayload.codecOf(ValueFirstEncoder, PacketDecoder)` — самый удобный
  способ, у `PacketCodec.tuple` не хватит арности на пакет эффекта.
  `PacketCodecs` имеет `BOOL`, `VAR_INT`, `FLOAT`, `DOUBLE`.
- `PlayerLookup.tracking(Entity)` — готовый список тех, кому слать эхо
  отмотки (`fabric-networking-api-v1`).
- `DrawContext.fill(int,int,int,int,int)` и `fillGradient` — на них рисуется
  дуговая шкала из мелких прямоугольников.

### Ловушки, на которые я уже наступил

- **Пост-шейдер обесцвечивания не выйдет просто так.** У `GameRenderer`
  в 1.21.1 публично есть только `disablePostProcessor()`,
  `getPostProcessor()`, `togglePostProcessorEnabled()` — публичного
  `loadPostProcessor(Identifier)` нет. Либо миксин, либо, что дешевле,
  серая полупрозрачная заливка поверх кадра через `HudRenderCallback`
  с затуханием. Второе — 10 строк вместо шейдерного конвейера.
- **Рывок камеры требует миксина.** `Camera.moveBy(float,float,float)`
  и `setPos` — `protected`. Нужен `@Mixin(Camera.class)` с `@Shadow`
  на `moveBy` и `@Inject(method = "update", at = @At("TAIL"))`.
  Это законный случай по RULE 6: события Fabric такого не дают.
  Не забыть завести `rewind.client.mixins.json` и прописать его
  в `fabric.mod.json` с `"environment": "client"`.
- **Со своими звуками осторожно.** ТЗ §29 просит отдельные события
  `rewind_trigger`, `rewind_complete`, `temporal_instability`, но своих
  `.ogg` в репозитории нет и взять их неоткуда. Вариант — свои события
  в `sounds.json`, ссылающиеся на ванильные файлы
  (`{"name": "minecraft:block/beacon/deactivate"}`), но это надо проверить
  живым клиентом: если ссылка не разрешится, получится тишина, а тишина
  выглядит как рабочий код. Пока в `ServerFeedback` стоят прямые ванильные
  `SoundEvents` с пометкой `ponytail:`.
- **Предмету «Журнал времени» не нужна своя текстура на первом заходе.**
  Модель может ссылаться на ванильную: `"layer0": "minecraft:item/writable_book"`.
  Экран журнала можно рисовать на ванильном фоне книги
  (`textures/gui/book.png`) — эстетика дневника получается даром, бинарных
  ассетов в репозитории не заводится.

## Что делать в начале следующей сессии

1. `git pull`, прочитать `IMPLEMENTATION_PLAN.md` и `ARCHITECTURE.md`.
2. Включить клиентский source set — этот шаг я начал и откатил, чтобы не
   коммитить сломанную сборку. В `build.gradle` внутрь блока `loom`:

   ```groovy
   loom {
       splitEnvironmentSourceSets()
       mods {
           "rewind" {
               sourceSet sourceSets.main
               sourceSet sourceSets.client
           }
       }
   }
   ```

   В `fabric.mod.json` добавить entrypoint
   `"client": ["io.github.eightiescrime.rewind.client.RewindClient"]`
   и блок `mixins` с `rewind.client.mixins.json`. **Важно:** после этой
   правки сборка ломается, пока не появятся сам класс `RewindClient`
   и файл конфигурации миксинов, — поэтому делать её надо вместе
   с созданием этих двух файлов, одним шагом.
3. Дальше по этапам, коммит и `git push` после каждого:
   - **A.** Сетевой слой (`TemporalStatePayload`, `RewindEffectPayload`),
     синхронизация состояния, HUD в эстетике дневника.
   - **B.** Пакет эффекта → афтеримидж, частицы назад по траектории, эхо
     для соседей, серая заливка кадра, рывок камеры.
   - **C.** Звуки, включая тик близкой отмотки.
   - **D.** Журнал времени (предмет + экран) и подсказка в сообщении
     о смерти.

## Состояние репозитория

Всё в `origin/main`, рабочая копия чистая, `./gradlew build` зелёный.
Живой чек-лист эксплойтов и поведения (`2026-09-14-exploit-checklist.md`)
владельцем ещё не прогонялся.
