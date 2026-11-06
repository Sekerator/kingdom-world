# KingdomWorld

[Русский](#русский) | [English](#english)

## Русский

KingdomWorld: мод для Minecraft 1.20.1 (Forge) о королевствах, которые развиваются по всему миру. В планах: экономика, налоги и своя валюта, жители с профессиями и производственными цепочками, армия, знать, фракции, дипломатия, войны и интриги. Любой игрок может основать своё королевство. Кроме того: гильдия авантюристов с рангами F-SSS и кристаллами с монстров (энергия королевства), башня магов, карта королевств и границ.

### Состояние

Этап 1: основание королевства, территория по чанкам с защитой, подданство и должности, окно трона и политическая карта королевств. Каркас этапа 0 (хранение в мире с версией формата, сетевой канал с проверкой запросов на сервере, конфиги, тесты, smoke-проверка на настоящем сервере) сохранён.

### Как играть

1. Скрафтите **Трон** и **Корону основателя** (рецепты ниже) и поставьте трон. Пустой трон просто украшение.
2. Возьмите корону в руку и нажмите правой кнопкой по трону. В окне основания введите название и выберите цвет. Сервер проверяет условия; при успехе корона расходуется, трон становится сердцем королевства, а вы королём.
3. Условия основания: вы не состоите ни в каком королевстве; трон не стоит на чужих землях и не ближе `minChunksFromOtherKingdom` чанков к ним; измерение есть в `allowedDimensions`.
4. Королевство сразу владеет квадратом чанков вокруг трона (радиус `initialClaimRadius`, по умолчанию 3x3).
5. Правый клик по трону открывает **окно трона**: обзор, подданные и заявки, права должностей, кнопка «Территория» (карта в режиме присоединения и отказа от чанков). Чужак видит обзор и может подать заявку.
6. **Карта королевств**: клавиша K (меняется в настройках управления) или предмет **Атлас королевств**. Колесо мыши меняет масштаб, перетаскивание сдвигает карту, клик по землям показывает карточку королевства. В режиме территории ЛКМ присоединяет свободный чанк, ПКМ отказывается от своего.
7. Сердце королевства не ломается. Чтобы убрать трон, король распускает королевство (`/kingdom dissolve` и подтверждение или кнопка в окне трона); после этого трон снова обычный.

Рецепты (датапак, их можно заменить своими):

| Предмет | Рецепт |
|---|---|
| Корона основателя | алмазный блок, звезда Незера, алмазный блок над тремя золотыми блоками |
| Трон | золотые слитки, красная шерсть и доски в форме кресла |
| Атлас королевств | книга, компас и пустая карта (без формы) |

### Территория и защита

- Земли королевства лежат в измерении трона, остаются связными (каждый чанк связан с чанком трона через соседей по стороне), чанк трона отдать нельзя.
- Новый чанк должен граничить стороной с землями королевства и держаться от чужих земель на расстоянии `claimBufferChunks`.
- Лимит чанков: `baseMaxChunks` плюс `chunksPerMember` за каждого подданного-игрока (кроме правителя). Расчёт лимита вынесен в `ChunkLimitPolicy`, его можно заменить через `KingdomWorldApi.setChunkLimitPolicy`.
- В землях королевства чужим и членам без нужного права запрещено ломать и ставить блоки, пользоваться вёдрами и предметами на блоках, открывать блоки с инвентарём. С `protectInteractions` также двери, люки, калитки, кнопки, рычаги, повторители, компараторы, рамки и стойки для брони.
- Взрывы не разрушают блоки в землях (`protectFromExplosions`), огонь туда не распространяется и не сжигает блоки (`protectFromFire`).
- Поршни не двигают блоки через границу: поршень снаружи не вталкивает блоки в земли королевства и не вытягивает их оттуда.
- Машины модов, действующие через FakePlayer (например, Create), разрешены настройкой `allowFakePlayers`.
- Операторы обходят защиту командой `/kingdomworld admin bypass` (переключатель, сбрасывается при перезапуске сервера).
- Все проверки идут через один сервис прав (`ProtectionService` и `AccessPolicy` в ядре); будущие механики (война) добавляют исключения через `KingdomWorldApi.accessPolicy().addExemption(...)`.
- При входе в земли королевства и выходе из них над хотбаром появляется сообщение (отключается в клиентском конфиге).

### Подданство и должности

- Вступление: заявка (кнопка в окне трона или `/kingdom join <название>`) и приглашение от королевства (`/kingdom invite <игрок>`, принять `/kingdom accept`). Если встречаются заявка и приглашение, игрок вступает сразу. Заявки и приглашения хранятся, пока на них не ответят, и показываются адресату при входе в игру.
- Должности: король, маршал, казначей, канцлер, глава разведки, придворный маг, управляющий, подданный.
- Права: стройка, контейнеры, взаимодействие, приём людей, территория, назначение должностей (последнее только у короля). Набор прав должностей король меняет во вкладке «Права». По умолчанию: маршал всё кроме назначений, канцлер с приёмом людей, управляющий с территорией, казначей, глава разведки и придворный маг со стройкой, контейнерами и взаимодействием, подданный со стройкой и взаимодействием.
- Изгонять может обладатель права приёма людей; не король изгоняет только простых подданных. Король не может уйти, он отрекается в пользу другого члена или распускает королевство.

### Требования

- Minecraft 1.20.1
- Forge 47.4.10 или новее в ветке 47.x
- Java 17
- Мод ставится и на сервер, и на клиент. Версии должны совпадать, иначе подключение отклоняется.
- Других обязательных модов нет. Совместимость с другими модами (Create и т.д.) включается сама, только если такой мод установлен.

Мод рассчитан на выделенный сервер: вся логика и все изменения состояния выполняются только на сервере, клиент лишь показывает данные и отправляет намерения, которые сервер проверяет (права, расстояние до трона, лимиты размера и частоты запросов).

### Сборка

```
gradlew build
```

Готовый jar: `build/libs/kingdomworld-<версия>.jar`. Нужен JDK 17: если системная `java` другой версии, задайте `JAVA_HOME`, например:

```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot
gradlew build
```

Первая сборка скачивает и подготавливает Minecraft, это занимает несколько минут.

### Тесты

| Что | Команда |
|---|---|
| Юнит-тесты ядра и архитектурные проверки | `gradlew test` (входит в `gradlew build`) |
| Игровые тесты Forge GameTest | `gradlew runGameTestServer` |
| Smoke-тест на чистом выделенном сервере | `powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1` |
| Smoke-тест с модами сборки | `powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1 -Pack` |

Юнит-тесты ядра покрывают присоединение и отказ от чанков (связность, лимиты, расстояние до чужих земель), права должностей, переходы подданства и политику доступа. Архитектурные тесты проверяют, что пакет `core` не зависит от Minecraft, Forge и Mojang, что клиентский код вызывается только через защищённые точки входа и что модули совместимости не используются напрямую.

`runGameTestServer` запускает игровые тесты в отдельном мире (`run-gametest/`) и завершается с ненулевым кодом, если хотя бы один тест упал. Среди них: основание через API, защита (чужак не ломает блоки и не открывает сундук, член с правом может), взрыв в землях королевства, присоединение и отказ от чанков, режим только чтения для данных новой версии и для повреждённой версии формата.

Smoke-скрипт ставит настоящий сервер Forge 1.20.1-47.4.10 в `D:\Projects\kingdom-world-testenv` (вне репозитория), кладёт туда собранный jar, принимает EULA и дважды запускает сервер на свежем мире. В первый раз он основывает королевство `SmokeTest` с троном в 8 70 8 (`kingdomworld admin found`), присоединяет чанк [2, 0] и проверяет, что оторванный чанк не присоединяется. Во второй раз проверяет, что королевство, его id и все 10 чанков сохранились. Скрипт завершается с кодом 1, если сервер не запустился или не остановился, если в логах есть ошибки или исключения, связанные с `kingdomworld`, если не активна защита от огня, если на сервере загрузился клиентский класс мода или если данные не пережили перезапуск. Логи каждого запуска лежат в `smoke-logs` внутри папки сервера. Команды в консоль сервера пишутся в UTF-8 без BOM, поэтому скрипт работает и под кодовой страницей 65001.

Параметры скрипта:

- `-Pack`: сервер в `server-pack` с серверными и общими модами из папки сборки (по умолчанию `D:\Games\Minecraft\Instances\Taghi RPG & Create RU\mods`; папка только читается). Чисто клиентские моды пропускаются.
- `-SkipBuild`: взять уже собранный jar.
- `-WithoutMod`: диагностический запуск без KingdomWorld, чтобы понять, не роняет ли сервер какой-то мод сборки сам по себе.
- `-ExcludeModIds a,b`: дополнительно исключить моды по id.
- `-TestEnvRoot`, `-PackModsDir`, `-JavaHome`, `-Memory`: пути и память сервера.

### Запуск в среде разработки

- `gradlew runClient`: клиент с модом (папка `run/`). `gradlew runClient -PquickPlayWorld=<папка мира>` сразу открывает одиночный мир.
- `gradlew runServer`: выделенный сервер с модом (папка `run/`, EULA нужно принять в `run/eula.txt`).
- Самопроверка клиента: `gradlew runClient -PclientSelfTest=title` доходит до главного меню и выходит; `gradlew runClient -PclientSelfTest=world -PquickPlayWorld=<папка мира>` в одиночном мире основывает рядом с игроком королевство, открывает карту, окно трона (через настоящий пакет сервера) со всеми вкладками, карту территории с присоединением чанка и окно основания, затем распускает королевство и выходит. Шаги пишутся в лог с префиксом `KingdomWorld client self-test`. Без свойства `clientSelfTest` этот код не работает.

### Команды

Для всех игроков (`/kingdom`):

- `info [название]`: сведения о своём королевстве, о королевстве, на землях которого вы стоите, или о названном.
- `list`: список королевств.
- `join <название>`: подать заявку; `accept [название]` и `decline <название>`: ответить на приглашение.
- `leave`: покинуть королевство.
- `invite <игрок>`, `approve <игрок>`, `reject <игрок>`, `kick <игрок>`: для права приёма людей.
- `role <игрок> <должность>`: назначить должность (`marshal`, `treasurer`, `chancellor`, `spymaster`, `court_mage`, `steward`, `subject`), только король.
- `abdicate <игрок>`: передать корону.
- `claim`, `unclaim`: присоединить чанк, в котором вы стоите, или отказаться от него (право территории).
- `dissolve`, затем `dissolve confirm` в течение 30 секунд: распустить королевство (только король).

Операторы (уровень прав 2, `/kingdomworld`):

- `admin found <название> <x y z> [правитель]`: поставить трон и основать королевство (с консоли без правителя).
- `admin delete <название>`: удалить королевство.
- `admin setruler <название> <игрок>`: назначить правителя.
- `admin claim|unclaim <название> <чанкX> <чанкZ>`: присоединить чанк или отказаться от него без проверки прав и лимита (остальные правила действуют).
- `admin owner <чанкX> <чанкZ>`: чей чанк.
- `admin bypass`: переключить обход защиты.
- `admin data status`: состояние данных; `admin data unlock`: выйти из режима только чтения (сначала делается резервная копия, затем остаётся только то, что удалось прочитать).
- `debug create|list|info|remove`: отладочные команды этапа 0.

Названия из нескольких слов в админских командах берите в кавычки: `"Old Valoria"`.

### Конфиги

- `<мир>/serverconfig/kingdomworld-server.toml` (на мир):
  - `npc`, `simulation`, `player`: `maxActiveNpcs`, `simulationIntervalTicks`, `afkTimeoutSeconds`, `combatTagSeconds` (заготовки для следующих этапов);
  - `kingdom`: `allowedDimensions` (`["minecraft:overworld"]`), `minChunksFromOtherKingdom` (4), `initialClaimRadius` (1);
  - `territory`: `baseMaxChunks` (25), `chunksPerMember` (4), `claimBufferChunks` (1, 0 разрешает соприкасаться границам);
  - `protection`: `protectInteractions`, `protectFromExplosions`, `protectFromFire`, `allowFakePlayers` (все `true`).
- `config/kingdomworld-common.toml`: `disabledCompatModules`, список id модов, модули совместимости с которыми не включать.
- `config/kingdomworld-client.toml`: `requestKingdomListOnJoin`, `showBorderMessages` (сообщения при пересечении границ).

### Данные и безопасность данных

Данные королевств хранятся в `<мир>/data/kingdomworld_kingdoms.dat`, формат 2 (королевства с землями, подданными, правами, заявками и приглашениями). Данные формата 1 обновляются автоматически.

Если данные не удаётся прочитать полностью (формат новее, чем понимает эта версия мода, повреждённая версия формата, неудачная миграция, нечитаемая запись или нечитаемый файл), мод не бросает исключений и переходит в режим только чтения: прочитанное доступно и защищает земли, но любые изменения отклоняются с ошибкой в логе (уровень ERROR), а файл не перезаписывается. Выйти из режима можно, исправив файл, или командой `/kingdomworld admin data unlock`.

### Для интеграций с модами карт

Данные устроены так, чтобы их легко было отдать JourneyMap, Xaero или BlueMap:

- сервер: `KingdomWorldApi.claims(server, dimension)` возвращает все чанки измерения с владельцами (ключ чанка в формате `ChunkPos.toLong()`), `KingdomWorldApi.addTerritoryListener(...)` сообщает о каждой смене владельца;
- клиент: `ClientTerritoryCache` хранит владельцев запрошенных чанков и сведения о королевствах (цвет, столица, правитель) и тоже принимает слушателей.

### Структура кода

Пакет `io.github.sekerator.kingdomworld`:

- `core`: чистая Java-логика без Minecraft (королевства, территория и правила присоединения, подданство и права, политика доступа, проверка запросов). Быстро тестируется юнит-тестами.
- `common`: всё, что работает на обеих сторонах: регистрации, хранение (`SavedData` с версией формата, миграциями и режимом только чтения), сеть, команды, конфиг, серверный API, защита, игровые тесты. В `common.mixin` один mixin для огня: у Forge нет события распространения огня.
- `client`: только клиентский код (экраны основания, трона и карты, клавиша, кэш территории). Подключается лишь на клиенте, выделенный сервер эти классы не загружает.
- `compat`: мягкая совместимость с другими модами. Модуль включается только если мод установлен.

Текстуры и модели короны, трона и атласа временные.

### Лицензия

All Rights Reserved.

## English

KingdomWorld is a Minecraft 1.20.1 (Forge) mod about kingdoms that grow across the world. Planned: economy, taxes and a currency, villagers with professions and production chains, armies, nobility, factions, diplomacy, wars and intrigue. Any player can found a kingdom. Also: an adventurers guild with ranks F-SSS and monster crystals (kingdom energy), a mage tower and a map of kingdoms and borders.

### Status

Stage 1: founding a kingdom, chunk-based territory with protection, membership and positions, the throne screen and a political map of kingdoms. The stage 0 skeleton (versioned world storage, a network channel with server-side request validation, configs, tests, a smoke test on a real server) is kept.

### How to play

1. Craft a **Throne** and a **Founder's Crown** (recipes below) and place the throne. An empty throne is decoration.
2. Hold the crown and right-click the throne. Enter a name and pick a color in the founding screen. The server checks the conditions; on success the crown is used up, the throne becomes the heart of the kingdom and you become its king.
3. Founding conditions: you belong to no kingdom; the throne is not on land of another kingdom and not within `minChunksFromOtherKingdom` chunks of it; the dimension is listed in `allowedDimensions`.
4. The kingdom starts with the square of chunks around the throne (radius `initialClaimRadius`, 3x3 by default).
5. Right-clicking the throne opens the **throne screen**: overview, members and applications, rights of positions, and a "Territory" button (the map in claim mode). Outsiders see the overview and can ask to join.
6. **Kingdom map**: key K (rebindable in the controls) or the **Kingdom Atlas** item. The mouse wheel zooms, dragging moves the map, clicking land shows the kingdom's card. In territory mode a left click claims a free chunk, a right click gives up one of yours.
7. The heart of a kingdom cannot be broken. To remove the throne, the king dissolves the kingdom (`/kingdom dissolve` plus confirmation, or the button in the throne screen); the throne then becomes an ordinary throne again.

Recipes (data pack, replaceable):

| Item | Recipe |
|---|---|
| Founder's Crown | diamond block, nether star, diamond block above three gold blocks |
| Throne | gold ingots, red wool and planks shaped like a chair |
| Kingdom Atlas | book, compass and an empty map (shapeless) |

### Territory and protection

- A kingdom's land lies in the throne's dimension, stays connected (every chunk reaches the throne's chunk through side neighbours), and the throne's chunk cannot be given up.
- A new chunk must share a side with the kingdom's land and keep `claimBufferChunks` away from other kingdoms.
- Chunk limit: `baseMaxChunks` plus `chunksPerMember` for every player subject (the ruler excluded). The limit is computed by a `ChunkLimitPolicy`, which `KingdomWorldApi.setChunkLimitPolicy` can replace.
- In kingdom land, outsiders and members without the matching right cannot break or place blocks, use buckets or items on blocks, or open blocks with an inventory. With `protectInteractions`, doors, trapdoors, gates, buttons, levers, repeaters, comparators, item frames and armor stands are protected as well.
- Explosions do not destroy blocks in kingdom land (`protectFromExplosions`); fire neither spreads into it nor burns its blocks (`protectFromFire`).
- Pistons do not move blocks across a border: a piston outside cannot push blocks into a kingdom's land or pull them out of it.
- Machines of other mods acting as fake players (Create and the like) are allowed by `allowFakePlayers`.
- Operators bypass protection with `/kingdomworld admin bypass` (a toggle, reset when the server restarts).
- Every check goes through one service (`ProtectionService` and the core `AccessPolicy`); later mechanics (war) add exemptions with `KingdomWorldApi.accessPolicy().addExemption(...)`.
- Entering and leaving a kingdom's land shows a message above the hotbar (can be turned off in the client config).

### Membership and positions

- Joining: an application (throne screen button or `/kingdom join <name>`) and an invitation from the kingdom (`/kingdom invite <player>`, accepted with `/kingdom accept`). When an application meets an invitation the player joins at once. Applications and invitations are kept until answered and are shown to the player on the next login.
- Positions: king, marshal, treasurer, chancellor, spymaster, court mage, steward, subject.
- Rights: building, containers, interaction, recruiting, territory, appointments (the last one belongs to the king alone). The king edits the rights of positions in the "Rights" tab. Defaults: the marshal has everything but appointments, the chancellor recruits, the steward manages territory, the treasurer, spymaster and court mage build, open containers and interact, a subject builds and interacts.
- Members with the recruiting right expel; anyone but the king can only expel plain subjects. The king cannot leave; they abdicate in favour of another member or dissolve the kingdom.

### Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or newer in the 47.x line
- Java 17
- Install the mod on both the server and the client. Versions must match, otherwise the connection is refused.
- No other mods are required. Integrations with other mods (Create and so on) switch on automatically, only when that mod is installed.

The mod is built for dedicated servers: all logic and every state change run on the server only; the client only displays data and sends intents that the server validates (rights, distance to the throne, request size and rate limits).

### Building

```
gradlew build
```

The jar ends up in `build/libs/kingdomworld-<version>.jar`. JDK 17 is required; if the system `java` is a different version, set `JAVA_HOME` first (see the Russian section for an example). The first build downloads and prepares Minecraft, which takes a few minutes.

### Tests

| What | Command |
|---|---|
| Core unit tests and architecture checks | `gradlew test` (part of `gradlew build`) |
| Forge GameTests | `gradlew runGameTestServer` |
| Smoke test on a clean dedicated server | `powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1` |
| Smoke test with the modpack's mods | `powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1 -Pack` |

Core unit tests cover claiming and giving up chunks (connectivity, limits, distance to other kingdoms), rights of positions, membership transitions and the access policy. The architecture tests check that the `core` package does not depend on Minecraft, Forge or Mojang, that client code is only reached through guarded entry points and that compat modules are not referenced directly.

`runGameTestServer` runs the game tests in a separate world (`run-gametest/`) and exits with a non-zero code if any test fails. They include founding through the API, protection (an outsider cannot break blocks or open a chest, a member with the right can), an explosion in kingdom land, claiming and giving up chunks, and read-only mode for data of a newer version and for a damaged format version.

The smoke script installs a real Forge 1.20.1-47.4.10 server into `D:\Projects\kingdom-world-testenv` (outside the repository), adds the built jar, accepts the EULA and starts the server twice on a fresh world. The first session founds the kingdom `SmokeTest` with a throne at 8 70 8 (`kingdomworld admin found`), claims chunk [2, 0] and checks that a detached chunk is refused. The second session checks that the kingdom, its id and all 10 chunks survived. The script exits with code 1 if the server fails to start or stop, if the logs contain errors or exceptions related to `kingdomworld`, if fire protection is not active, if a client class of the mod is loaded on the server, or if the data does not survive the restart. Logs of each session are kept in `smoke-logs` inside the server folder. Commands are written to the server console as UTF-8 without a BOM, so the script also works under code page 65001.

Script options:

- `-Pack`: a server in `server-pack` with the server-side and common mods of the modpack (default `D:\Games\Minecraft\Instances\Taghi RPG & Create RU\mods`; the folder is only read). Client-only mods are skipped.
- `-SkipBuild`: use the jar that is already built.
- `-WithoutMod`: diagnostic run without KingdomWorld, to see whether a pack mod breaks the server on its own.
- `-ExcludeModIds a,b`: leave out additional mods by id.
- `-TestEnvRoot`, `-PackModsDir`, `-JavaHome`, `-Memory`: paths and server memory.

### Running in development

- `gradlew runClient`: client with the mod (folder `run/`). `gradlew runClient -PquickPlayWorld=<world folder>` opens a singleplayer world right away.
- `gradlew runServer`: dedicated server with the mod (folder `run/`; accept the EULA in `run/eula.txt`).
- Client self-check: `gradlew runClient -PclientSelfTest=title` reaches the title screen and quits; `gradlew runClient -PclientSelfTest=world -PquickPlayWorld=<world folder>` founds a kingdom next to the player in a singleplayer world, opens the map, the throne screen (through the real server packet) with all tabs, the territory editor with a claim and the founding screen, then dissolves the kingdom and quits. Steps are logged with the prefix `KingdomWorld client self-test`. Without the `clientSelfTest` property this code does nothing.

### Commands

For every player (`/kingdom`):

- `info [name]`: your kingdom, the kingdom whose land you stand on, or the named one.
- `list`: all kingdoms.
- `join <name>`: apply; `accept [name]` and `decline <name>`: answer an invitation.
- `leave`: leave your kingdom.
- `invite <player>`, `approve <player>`, `reject <player>`, `kick <player>`: for the recruiting right.
- `role <player> <position>`: appoint (`marshal`, `treasurer`, `chancellor`, `spymaster`, `court_mage`, `steward`, `subject`), king only.
- `abdicate <player>`: hand over the crown.
- `claim`, `unclaim`: claim or give up the chunk you stand in (territory right).
- `dissolve`, then `dissolve confirm` within 30 seconds: dissolve the kingdom (king only).

Operators (permission level 2, `/kingdomworld`):

- `admin found <name> <x y z> [ruler]`: place a throne and found a kingdom (without a ruler from the console).
- `admin delete <name>`: delete a kingdom.
- `admin setruler <name> <player>`: set the ruler.
- `admin claim|unclaim <name> <chunkX> <chunkZ>`: claim or give up a chunk without rights or limit checks (the other rules apply).
- `admin owner <chunkX> <chunkZ>`: who owns a chunk.
- `admin bypass`: toggle protection bypass.
- `admin data status`: state of the data; `admin data unlock`: leave read-only mode (a backup is made first, then only what could be read is kept).
- `debug create|list|info|remove`: the stage 0 debug commands.

Quote kingdom names with spaces in admin commands: `"Old Valoria"`.

### Configuration

- `<world>/serverconfig/kingdomworld-server.toml` (per world):
  - `npc`, `simulation`, `player`: `maxActiveNpcs`, `simulationIntervalTicks`, `afkTimeoutSeconds`, `combatTagSeconds` (placeholders for later stages);
  - `kingdom`: `allowedDimensions` (`["minecraft:overworld"]`), `minChunksFromOtherKingdom` (4), `initialClaimRadius` (1);
  - `territory`: `baseMaxChunks` (25), `chunksPerMember` (4), `claimBufferChunks` (1; 0 lets borders touch);
  - `protection`: `protectInteractions`, `protectFromExplosions`, `protectFromFire`, `allowFakePlayers` (all `true`).
- `config/kingdomworld-common.toml`: `disabledCompatModules`, mod ids whose compat modules stay off.
- `config/kingdomworld-client.toml`: `requestKingdomListOnJoin`, `showBorderMessages` (messages when crossing borders).

### Data and data safety

Kingdom data is stored in `<world>/data/kingdomworld_kingdoms.dat`, format 2 (kingdoms with land, members, rights, applications and invitations). Format 1 data is upgraded automatically.

If the data cannot be fully read (a format newer than this version understands, a damaged format version, a failed migration, an unreadable entry or an unreadable file), loading does not throw: the data becomes read-only. What could be read stays available and keeps protecting land, but every change is refused with an ERROR in the log, and the file is never overwritten. Fix the file or run `/kingdomworld admin data unlock` to leave read-only mode.

### For map mod integrations

The data is laid out so it can easily be handed to JourneyMap, Xaero or BlueMap:

- server: `KingdomWorldApi.claims(server, dimension)` returns every claimed chunk of a dimension with its owner (chunk keys as `ChunkPos.toLong()`), `KingdomWorldApi.addTerritoryListener(...)` reports every change of owner;
- client: `ClientTerritoryCache` holds the owners of the chunks it asked for and facts about kingdoms (color, capital, ruler) and accepts listeners as well.

### Code layout

Package `io.github.sekerator.kingdomworld`:

- `core`: plain Java domain logic without Minecraft (kingdoms, territory and claim rules, membership and rights, the access policy, request validation). Fast to unit test.
- `common`: everything that runs on both sides: registries, persistence (`SavedData` with a format version, migrations and read-only mode), networking, commands, config, the server API, protection and game tests. `common.mixin` holds one mixin for fire, because Forge has no event for fire spreading.
- `client`: client-only code (founding, throne and map screens, the key binding, the territory cache). Wired in on the client only; a dedicated server never loads these classes.
- `compat`: soft integrations with other mods. A module is activated only when its mod is installed.

Textures and models of the crown, the throne and the atlas are placeholders.

### License

All Rights Reserved.
